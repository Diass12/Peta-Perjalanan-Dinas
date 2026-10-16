package id.tub.dinas.data

import android.content.Context
import androidx.room.withTransaction
import id.tub.dinas.data.backup.BackupCodec
import id.tub.dinas.data.local.*
import id.tub.dinas.domain.*
import id.tub.dinas.export.ExcelExporter
import id.tub.dinas.security.MediaVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/** Satu-satunya pintu ke data. Semua operasi tulis berjalan di Dispatchers.IO. */
class Repository(
    private val ctx: Context,
    private val db: AppDatabase,
    val vault: MediaVault,
) {
    private val dao = db.dao()
    private fun uid() = UUID.randomUUID().toString()

    companion object {
        const val WARN_PDF = 12L * 1024 * 1024
        const val MAX_PDF = 50L * 1024 * 1024
        const val MAX_VIDEO = 500L * 1024 * 1024
        const val MAX_AUDIO = 100L * 1024 * 1024
        const val MAX_DOC = 50L * 1024 * 1024
    }

    // ---------- dinas ----------
    fun observeDinas(): Flow<List<DinasModel>> = dao.observeDinas().map { l -> l.map { it.toModel() } }
    /** Titik, temuan, temuan selesai: bahan hitung XP. */
    fun observeXp(): Flow<Triple<Int, Int, Int>> = combine(dao.countAllTitik(), dao.countAllTemuan(), dao.countAllSelesai()) { a, b, c -> Triple(a, b, c) }
    fun observeCounts(id: String): Flow<Pair<Int, Int>> = combine(dao.countTitik(id), dao.countTemuan(id)) { a, b -> a to b }

    suspend fun createDinas(name: String, startMs: Long?, endMs: Long?, note: String): String = withContext(Dispatchers.IO) {
        val id = uid()
        dao.upsertDinas(DinasEntity(id, name.trim().ifEmpty { "Dinas baru" }, startMs, endMs, note.trim(), System.currentTimeMillis()))
        id
    }

    suspend fun updateDinas(m: DinasModel) = withContext(Dispatchers.IO) {
        val old = dao.dinasById(m.id) ?: return@withContext
        dao.upsertDinas(old.copy(name = m.name.trim().ifEmpty { old.name }, startMs = m.startMs, endMs = m.endMs, note = m.note.trim()))
    }

    suspend fun deleteDinas(id: String) = withContext(Dispatchers.IO) {
        val paths = dao.fotoList(id).map { it.path } + dao.lampiranList(id).map { it.path } + dao.mediaList(id).map { it.path }
        dao.deleteDinas(id)
        paths.forEach { vault.delete(it) }
    }

    fun observeSnapshot(id: String): Flow<DinasSnapshot?> {
        val a = combine(dao.observeTitik(id), dao.observeTemuan(id), dao.observeFoto(id)) { t, m, f -> Triple(t, m, f) }
        val b = combine(dao.observeLampiran(id), dao.observePoin(id), dao.observeMedia(id)) { l, p, md -> Triple(l, p, md) }
        return combine(a, b) { (t, m, f), (l, p, md) ->
            DinasSnapshotParts(t, m, f, l, p, md)
        }.map { parts ->
            val d = dao.dinasById(id) ?: return@map null
            DinasSnapshot(d.toModel(), parts.t.map { it.toModel() }, parts.m.map { it.toModel() }, parts.f.map { it.toModel() }, parts.l.map { it.toModel() }, parts.p.map { it.toModel() }, parts.md.map { it.toModel() })
        }
    }

    private class DinasSnapshotParts(val t: List<TitikEntity>, val m: List<TemuanEntity>, val f: List<FotoEntity>, val l: List<LampiranEntity>, val p: List<PoinEntity>, val md: List<MediaEntity>)

    suspend fun snapshot(id: String): DinasSnapshot? = withContext(Dispatchers.IO) {
        val d = dao.dinasById(id) ?: return@withContext null
        DinasSnapshot(d.toModel(), dao.titikList(id).map { it.toModel() }, dao.temuanList(id).map { it.toModel() },
            dao.fotoList(id).map { it.toModel() }, dao.lampiranList(id).map { it.toModel() }, dao.poinList(id).map { it.toModel() }, dao.mediaList(id).map { it.toModel() })
    }

    // ---------- titik ----------
    suspend fun saveTitik(dinasId: String, id: String?, title: String, note: String, geo: GeoPoint?, recordedAt: Long? = null): String = withContext(Dispatchers.IO) {
        val old = id?.let { dao.titikById(it) }
        val tid = old?.id ?: uid()
        val order = old?.urutan ?: ((dao.titikList(dinasId).maxOfOrNull { it.urutan } ?: -1) + 1)
        dao.upsertTitik(TitikModel(tid, title.trim().ifEmpty { "Titik ${order + 1}" }, note.trim(), geo, old?.recordedAt ?: recordedAt ?: System.currentTimeMillis(), order).toEntity(dinasId))
        tid
    }

    suspend fun deleteTitik(id: String) = withContext(Dispatchers.IO) {
        val paths = dao.fotoOfOwner(id).map { it.path } + dao.mediaOfOwner(id).map { it.path }
        db.withTransaction { dao.deleteFotoOfOwner(id); dao.deleteMediaOfOwner(id); dao.deleteTitik(id) }
        paths.forEach { vault.delete(it) }
    }

    /** Memindahkan titik dokumentasi menjadi temuan laporan; foto ikut pindah. Atomik. */
    suspend fun titikToTemuan(titikId: String): String? = withContext(Dispatchers.IO) {
        val t = dao.titikById(titikId) ?: return@withContext null
        val newId = uid()
        db.withTransaction {
            val order = (dao.temuanList(t.dinasId).maxOfOrNull { it.urutan } ?: -1) + 1
            dao.upsertTemuan(TemuanEntity(newId, t.dinasId, t.title, "", t.note, null, null, null, "", "", "", Status.BELUM.name, t.lat, t.lng, t.acc, t.src, order))
            dao.reassignFoto(titikId, newId, OwnerType.TEMUAN.name)
            dao.reassignMedia(titikId, newId, OwnerType.TEMUAN.name)
            dao.deleteTitik(titikId)
        }
        newId
    }

    suspend fun reorderTitik(dinasId: String, ids: List<String>) = withContext(Dispatchers.IO) {
        val map = dao.titikList(dinasId).associateBy { it.id }
        dao.upsertTitikAll(ids.mapIndexedNotNull { i, id -> map[id]?.copy(urutan = i) })
    }

    // ---------- temuan ----------
    suspend fun saveTemuan(dinasId: String, m: TemuanModel): String = withContext(Dispatchers.IO) {
        val existing = dao.temuanById(m.id)
        val order = existing?.urutan ?: ((dao.temuanList(dinasId).maxOfOrNull { it.urutan } ?: -1) + 1)
        dao.upsertTemuan(m.copy(title = m.title.trim().ifEmpty { "Temuan ${order + 1}" }, order = order).toEntity(dinasId))
        m.id
    }

    suspend fun newTemuan(dinasId: String, geo: GeoPoint?): String = withContext(Dispatchers.IO) {
        val id = uid()
        val order = (dao.temuanList(dinasId).maxOfOrNull { it.urutan } ?: -1) + 1
        dao.upsertTemuan(TemuanEntity(id, dinasId, "Temuan ${order + 1}", "", "", null, null, null, "", "", "", Status.BELUM.name, geo?.lat, geo?.lng, geo?.accuracyM, geo?.source ?: "", order))
        id
    }

    suspend fun deleteTemuan(id: String) = withContext(Dispatchers.IO) {
        val paths = dao.fotoOfOwner(id).map { it.path } + dao.lampiranOfTemuan(id).map { it.path } + dao.mediaOfOwner(id).map { it.path }
        db.withTransaction {
            dao.deleteFotoOfOwner(id)
            dao.deleteMediaOfOwner(id)
            dao.lampiranOfTemuan(id).forEach { dao.deleteLampiran(it.id) }
            dao.deleteTemuan(id)
        }
        paths.forEach { vault.delete(it) }
    }

    suspend fun reorderTemuan(dinasId: String, ids: List<String>) = withContext(Dispatchers.IO) {
        val map = dao.temuanList(dinasId).associateBy { it.id }
        dao.upsertTemuanAll(ids.mapIndexedNotNull { i, id -> map[id]?.copy(urutan = i) })
    }

    // ---------- foto ----------
    suspend fun addFoto(dinasId: String, type: OwnerType, ownerId: String, jpeg: ByteArray, w: Int, h: Int, takenAt: Long?): String = withContext(Dispatchers.IO) {
        val path = vault.save(jpeg, "foto", "jpg")
        try {
            val id = uid()
            val order = (dao.fotoOfOwner(ownerId).maxOfOrNull { it.urutan } ?: -1) + 1
            dao.upsertFoto(FotoEntity(id, dinasId, type.name, ownerId, path, "", order, w, h, takenAt))
            id
        } catch (e: Exception) { vault.delete(path); throw e }
    }

    suspend fun setCaption(fotoId: String, caption: String) = withContext(Dispatchers.IO) {
        dao.fotoById(fotoId)?.let { dao.upsertFoto(it.copy(caption = caption.trim())) }
    }

    suspend fun deleteFoto(id: String) = withContext(Dispatchers.IO) {
        val f = dao.fotoById(id) ?: return@withContext
        dao.deleteFoto(id); vault.delete(f.path)
    }

    suspend fun reorderFoto(ownerId: String, ids: List<String>) = withContext(Dispatchers.IO) {
        val map = dao.fotoOfOwner(ownerId).associateBy { it.id }
        dao.upsertFotoAll(ids.mapIndexedNotNull { i, id -> map[id]?.copy(urutan = i) })
    }

    // ---------- video & rekaman suara ----------
    suspend fun addMedia(dinasId: String, type: OwnerType, ownerId: String, kind: MediaKind, name: String, ext: String, input: InputStream): String = withContext(Dispatchers.IO) {
        val max = if (kind == MediaKind.VIDEO) MAX_VIDEO else MAX_AUDIO
        val (path, size) = try { vault.saveStream(input, "media", ext, max) } catch (e: java.io.IOException) {
            throw IllegalArgumentException("${kind.label} lebih dari ${max / 1024 / 1024} MB tidak diterima")
        }
        try {
            val id = uid()
            val order = (dao.mediaOfOwner(ownerId).maxOfOrNull { it.urutan } ?: -1) + 1
            dao.upsertMedia(MediaEntity(id, dinasId, type.name, ownerId, kind.name, path, sanitizeName(name), size, System.currentTimeMillis(), order))
            id
        } catch (e: Exception) { vault.delete(path); throw e }
    }

    suspend fun deleteMedia(id: String) = withContext(Dispatchers.IO) {
        val m = dao.mediaById(id) ?: return@withContext
        dao.deleteMedia(id); vault.delete(m.path)
    }

    fun mediaTempFile(path: String, suffix: String): File = vault.decryptToTemp(path, File(ctx.cacheDir, "media"), suffix)

    // ---------- lampiran PDF ----------
    /** Melempar IllegalArgumentException bila bukan PDF / terlalu besar. */
    suspend fun addLampiran(dinasId: String, temuanId: String, name: String, input: InputStream): String = withContext(Dispatchers.IO) {
        val buffered = java.io.BufferedInputStream(input)
        buffered.mark(8)
        val head = ByteArray(5); val n = buffered.read(head); buffered.reset()
        require(n == 5 && String(head, Charsets.US_ASCII) == "%PDF-") { "Berkas bukan PDF yang valid" }
        val (path, size) = try { vault.saveStream(buffered, "lampiran", "pdf", MAX_PDF) } catch (e: java.io.IOException) { throw IllegalArgumentException("PDF lebih dari 50 MB tidak diterima") }
        val id = uid()
        val order = (dao.lampiranOfTemuan(temuanId).maxOfOrNull { it.urutan } ?: -1) + 1
        dao.upsertLampiran(LampiranEntity(id, dinasId, temuanId, sanitizeName(name), path, size, order))
        id
    }

    suspend fun deleteLampiran(id: String) = withContext(Dispatchers.IO) {
        val l = dao.lampiranById(id) ?: return@withContext
        dao.deleteLampiran(id); vault.delete(l.path)
    }

    fun pdfTempFile(path: String): File = vault.decryptToTemp(path, File(ctx.cacheDir, "pdf"), ".pdf")

    private fun sanitizeName(n: String): String =
        n.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim().take(80).ifEmpty { "lampiran.pdf" }

    // ---------- poin sistem ----------
    suspend fun savePoin(dinasId: String, id: String?, text: String) = withContext(Dispatchers.IO) {
        val old = id?.let { dao.poinList(dinasId).firstOrNull { p -> p.id == it } }
        val order = old?.urutan ?: ((dao.poinList(dinasId).maxOfOrNull { it.urutan } ?: -1) + 1)
        dao.upsertPoin(PoinEntity(old?.id ?: uid(), dinasId, text.trim(), order))
    }
    suspend fun deletePoin(id: String) = withContext(Dispatchers.IO) { dao.deletePoin(id) }

    // ---------- export / backup ----------
    suspend fun exportExcel(dinasId: String, out: OutputStream) = withContext(Dispatchers.IO) {
        val s = snapshot(dinasId) ?: throw IllegalStateException("Dinas tidak ditemukan")
        ExcelExporter.export(s, { p -> runCatching { vault.read(p) }.getOrNull() }, out)
    }

    suspend fun exportBackup(out: OutputStream, passphrase: CharArray?) = withContext(Dispatchers.IO) {
        val ids = dao.observeDinasOnce()
        val snaps = ids.mapNotNull { snapshot(it.id) }
        BackupCodec.write(snaps, vault, out, passphrase, asetBundles(), umBundles(), jadwalList())
    }

    /** Mengembalikan jumlah dinas yang dipulihkan. Dinas ber-ID sama akan ditimpa. */
    suspend fun restoreBackup(input: InputStream, passphrase: CharArray?): Int = withContext(Dispatchers.IO) {
        val r = BackupCodec.read(input, vault, passphrase)
        db.withTransaction {
            for (s in r.snapshots) {
                val id = s.dinas.id
                val old = dao.dinasById(id)
                dao.upsertDinas(s.dinas.toEntity(old?.createdAt ?: System.currentTimeMillis()))
                dao.upsertTitikAll(s.titik.map { it.toEntity(id) })
                dao.upsertTemuanAll(s.temuan.map { it.toEntity(id) })
                dao.upsertFotoAll(s.foto.map { it.toEntity(id) })
                dao.upsertLampiranAll(s.lampiran.map { it.toEntity(id) })
                dao.upsertPoinAll(s.poin.map { it.toEntity(id) })
                dao.upsertMediaAll(s.media.map { it.toEntity(id) })
            }
            dao.upsertAsetAll(r.assets.map { it.aset.toEntity() })
            dao.upsertAsetFotoAll(r.assets.flatMap { b -> b.fotos.map { it.toEntity() } })
            dao.upsertAsetMediaAll(r.assets.flatMap { b -> b.media.map { it.toEntity() } })
            dao.upsertJadwalAll(r.jadwal.map { it.toEntity() })
            dao.upsertUmAll(r.um.map { it.um.toEntity() })
            dao.upsertBonAll(r.um.flatMap { u -> u.bons.map { it.bon.toEntity() } })
            dao.upsertBonFotoAll(r.um.flatMap { u -> u.bons.flatMap { b -> b.fotos.map { it.toEntity() } } })
        }
        r.snapshots.size
    }

    /** Menghapus berkas terenkripsi yang tidak dirujuk database (sisa proses yang terhenti). */
    suspend fun cleanOrphans(): Int = withContext(Dispatchers.IO) {
        val used = (dao.allFotoPaths() + dao.allLampiranPaths() + dao.allMediaPaths() + dao.allAsetFotoPaths() + dao.allAsetMediaPaths() + dao.allUmFotoPaths()).toSet()
        vault.deleteOrphans(used)
    }

    // ---------- register aset ----------
    fun observeAset(): Flow<List<AsetModel>> = dao.observeAset().map { l -> l.map { it.toModel() } }
    fun observeAsetFoto(): Flow<List<AsetFotoModel>> = dao.observeAsetFoto().map { l -> l.map { it.toModel() } }
    suspend fun asetList(): List<AsetModel> = withContext(Dispatchers.IO) { dao.asetList().map { it.toModel() } }
    suspend fun asetById(id: String): AsetModel? = withContext(Dispatchers.IO) { dao.asetById(id)?.toModel() }
    suspend fun asetFotoOf(asetId: String): List<AsetFotoModel> = withContext(Dispatchers.IO) { dao.asetFotoOf(asetId).map { it.toModel() } }

    fun observeAsetMedia(): Flow<List<AsetMediaModel>> = dao.observeAsetMedia().map { l -> l.map { it.toModel() } }

    suspend fun asetBundles(): List<AsetBundle> = withContext(Dispatchers.IO) {
        val fotos = dao.asetFotoAll().map { it.toModel() }.groupBy { it.asetId }
        val media = dao.asetMediaAll().map { it.toModel() }.groupBy { it.asetId }
        dao.asetList().map { AsetBundle(it.toModel(), fotos[it.id].orEmpty(), media[it.id].orEmpty()) }
    }

    /** Video atau dokumen untuk aset. Disimpan terenkripsi; batas ukuran per jenis. */
    suspend fun addAsetMedia(asetId: String, kind: String, name: String, ext: String, input: InputStream): String = withContext(Dispatchers.IO) {
        val max = if (kind == "VIDEO") MAX_VIDEO else MAX_DOC
        val (path, size) = try { vault.saveStream(input, "media", ext, max) } catch (e: java.io.IOException) {
            throw IllegalArgumentException("Berkas lebih dari ${max / 1024 / 1024} MB tidak diterima")
        }
        try {
            val id = uid()
            val order = (dao.asetMediaOf(asetId).maxOfOrNull { it.urutan } ?: -1) + 1
            dao.upsertAsetMedia(AsetMediaEntity(id, asetId, kind, path, sanitizeName(name), size, System.currentTimeMillis(), order))
            id
        } catch (e: Exception) { vault.delete(path); throw e }
    }

    suspend fun deleteAsetMedia(id: String) = withContext(Dispatchers.IO) {
        val m = dao.asetMediaById(id) ?: return@withContext
        dao.deleteAsetMedia(id); vault.delete(m.path)
    }

    /** Aset baru bila id belum ada; pada aset baru `order` diisi urutan berikutnya. */
    suspend fun saveAset(m: AsetModel): String = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val old = dao.asetById(m.id)
        val order = old?.ord ?: if (m.order > 0) m.order else dao.maxAsetOrd() + 1
        val clean = m.copy(code = KodeAset.norm(m.code).ifEmpty { "" }, order = order, createdAt = old?.createdAt ?: m.createdAt.takeIf { it > 0 } ?: now, updatedAt = now)
        dao.upsertAset(clean.toEntity())
        clean.id
    }

    suspend fun deleteAset(id: String) = withContext(Dispatchers.IO) {
        val paths = dao.asetFotoOf(id).map { it.path } + dao.asetMediaOf(id).map { it.path }
        dao.deleteAset(id)
        paths.forEach { vault.delete(it) }
    }

    suspend fun addAsetFoto(asetId: String, jpeg: ByteArray, takenAt: Long?): String = withContext(Dispatchers.IO) {
        val path = vault.save(jpeg, "aset", "jpg")
        try {
            val id = uid()
            val order = (dao.asetFotoOf(asetId).maxOfOrNull { it.urutan } ?: -1) + 1
            dao.upsertAsetFoto(AsetFotoEntity(id, asetId, path, order, takenAt))
            id
        } catch (e: Exception) { vault.delete(path); throw e }
    }

    suspend fun deleteAsetFoto(id: String) = withContext(Dispatchers.IO) {
        val f = dao.asetFotoById(id) ?: return@withContext
        dao.deleteAsetFoto(id); vault.delete(f.path)
    }

    /** Menjadikan foto sebagai foto utama (urutan pertama). */
    suspend fun setAsetCover(asetId: String, fotoId: String) = withContext(Dispatchers.IO) {
        val l = dao.asetFotoOf(asetId)
        val ordered = listOfNotNull(l.firstOrNull { it.id == fotoId }) + l.filter { it.id != fotoId }
        dao.upsertAsetFotoAll(ordered.mapIndexed { i, f -> f.copy(urutan = i) })
    }

    suspend fun resetAudit() = withContext(Dispatchers.IO) { dao.resetAudit() }

    suspend fun asetFotoBytes(path: String): ByteArray? = withContext(Dispatchers.IO) { runCatching { vault.read(path) }.getOrNull() }

    /** Membuat temuan lensa Aset di dinas tertentu dari sebuah aset; foto utama disalin. Mengembalikan id temuan. */
    suspend fun asetToTemuan(asetId: String, dinasId: String, geo: GeoPoint?): String? = withContext(Dispatchers.IO) {
        val a = dao.asetById(asetId)?.toModel() ?: return@withContext null
        val id = uid()
        val order = (dao.temuanList(dinasId).maxOfOrNull { it.urutan } ?: -1) + 1
        val ket = buildString {
            append("Aset ${a.code.ifEmpty { "(tanpa kode)" }} — ${a.name}")
            if (a.cond.isNotEmpty()) append("\nKondisi: ${a.cond}")
            if (a.location.isNotEmpty() || a.dept.isNotEmpty()) append("\nLokasi/Dept: ${listOf(a.location, a.dept).filter { it.isNotEmpty() }.joinToString(" / ")}")
            append("\nStatus audit: ${a.state.label}")
        }
        db.withTransaction {
            dao.upsertTemuan(TemuanEntity(id, dinasId, a.name.ifEmpty { a.code }.take(80), a.code, "", ket, null, null, "", "", "", Status.BELUM.name, geo?.lat, geo?.lng, geo?.accuracyM, geo?.source ?: "", order))
            dao.upsertAset(a.copy(temuanId = id).addLog("temuan dibuat").toEntity())
        }
        dao.asetFotoOf(asetId).firstOrNull()?.let { f ->
            runCatching {
                val bytes = vault.read(f.path)
                val bo = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bo)
                addFoto(dinasId, OwnerType.TEMUAN, id, bytes, bo.outWidth, bo.outHeight, f.takenAt)
            }
        }
        id
    }

    // ---------- uang muka & bon ----------
    private fun bundleUm(um: List<UangMukaEntity>, bon: List<UmBonEntity>, foto: List<UmFotoEntity>): List<UmBundle> {
        val fotoBy = foto.groupBy { it.bonId }
        val bonBy = bon.groupBy { it.umId }
        return um.map { u -> UmBundle(u.toModel(), bonBy[u.id].orEmpty().map { b -> BonBundle(b.toModel(), fotoBy[b.id].orEmpty().map { it.toModel() }) }) }
    }

    fun observeUmBundles(): Flow<List<UmBundle>> = combine(dao.observeUm(), dao.observeBon(), dao.observeBonFoto()) { u, b, f -> bundleUm(u, b, f) }

    suspend fun umBundles(): List<UmBundle> = withContext(Dispatchers.IO) { bundleUm(dao.umAll(), dao.bonAll(), dao.bonFotoAll()) }

    /** Id kosong = pengajuan baru. */
    suspend fun saveUm(m: UangMukaModel): String = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val id = m.id.ifEmpty { uid() }
        val old = dao.umById(id)
        dao.upsertUm(m.copy(id = id, createdAt = old?.createdAt ?: now, updatedAt = now).toEntity())
        id
    }

    suspend fun deleteUm(id: String) = withContext(Dispatchers.IO) {
        val paths = dao.bonOfUm(id).flatMap { b -> dao.bonFotoOf(b.id).map { it.path } }
        dao.deleteUm(id)
        paths.forEach { vault.delete(it) }
    }

    suspend fun saveBon(m: BonModel): String = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val id = m.id.ifEmpty { uid() }
        val old = dao.bonById(id)
        val order = old?.urutan ?: (dao.maxBonOrd(m.umId) + 1)
        dao.upsertBon(m.copy(id = id, order = order, createdAt = old?.createdAt ?: now).toEntity())
        id
    }

    suspend fun deleteBon(id: String) = withContext(Dispatchers.IO) {
        val paths = dao.bonFotoOf(id).map { it.path }
        dao.deleteBon(id)
        paths.forEach { vault.delete(it) }
    }

    suspend fun addBonFoto(bonId: String, jpeg: ByteArray): String = withContext(Dispatchers.IO) {
        val path = vault.save(jpeg, "bon", "jpg")
        try {
            val id = uid()
            val order = (dao.bonFotoOf(bonId).maxOfOrNull { it.urutan } ?: -1) + 1
            dao.upsertBonFoto(UmFotoEntity(id, bonId, path, order))
            id
        } catch (e: Exception) { vault.delete(path); throw e }
    }

    suspend fun deleteBonFoto(id: String) = withContext(Dispatchers.IO) {
        val f = dao.bonFotoById(id) ?: return@withContext
        dao.deleteBonFoto(id); vault.delete(f.path)
    }

    // ---------- jadwal ----------
    fun observeJadwal(): Flow<List<JadwalModel>> = dao.observeJadwal().map { l -> l.map { it.toModel() } }
    suspend fun jadwalList(): List<JadwalModel> = withContext(Dispatchers.IO) { dao.jadwalAll().map { it.toModel() } }

    /** Id kosong = jadwal baru. */
    suspend fun saveJadwal(m: JadwalModel): String = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val id = m.id.ifEmpty { uid() }
        val old = dao.jadwalById(id)
        dao.upsertJadwal(m.copy(id = id, createdAt = old?.createdAt ?: now, updatedAt = now).toEntity())
        id
    }

    suspend fun deleteJadwal(id: String) = withContext(Dispatchers.IO) { dao.deleteJadwal(id) }
}
