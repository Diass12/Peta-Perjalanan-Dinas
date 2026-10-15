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
    }

    // ---------- dinas ----------
    fun observeDinas(): Flow<List<DinasModel>> = dao.observeDinas().map { l -> l.map { it.toModel() } }
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
        val paths = dao.fotoList(id).map { it.path } + dao.lampiranList(id).map { it.path }
        dao.deleteDinas(id)
        paths.forEach { vault.delete(it) }
    }

    fun observeSnapshot(id: String): Flow<DinasSnapshot?> {
        val a = combine(dao.observeTitik(id), dao.observeTemuan(id), dao.observeFoto(id)) { t, m, f -> Triple(t, m, f) }
        val b = combine(dao.observeLampiran(id), dao.observePoin(id)) { l, p -> l to p }
        return combine(a, b) { (t, m, f), (l, p) ->
            DinasSnapshotParts(t, m, f, l, p)
        }.map { parts ->
            val d = dao.dinasById(id) ?: return@map null
            DinasSnapshot(d.toModel(), parts.t.map { it.toModel() }, parts.m.map { it.toModel() }, parts.f.map { it.toModel() }, parts.l.map { it.toModel() }, parts.p.map { it.toModel() })
        }
    }

    private class DinasSnapshotParts(val t: List<TitikEntity>, val m: List<TemuanEntity>, val f: List<FotoEntity>, val l: List<LampiranEntity>, val p: List<PoinEntity>)

    suspend fun snapshot(id: String): DinasSnapshot? = withContext(Dispatchers.IO) {
        val d = dao.dinasById(id) ?: return@withContext null
        DinasSnapshot(d.toModel(), dao.titikList(id).map { it.toModel() }, dao.temuanList(id).map { it.toModel() },
            dao.fotoList(id).map { it.toModel() }, dao.lampiranList(id).map { it.toModel() }, dao.poinList(id).map { it.toModel() })
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
        val paths = dao.fotoOfOwner(id).map { it.path }
        db.withTransaction { dao.deleteFotoOfOwner(id); dao.deleteTitik(id) }
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
        val paths = dao.fotoOfOwner(id).map { it.path } + dao.lampiranOfTemuan(id).map { it.path }
        db.withTransaction {
            dao.deleteFotoOfOwner(id)
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
        BackupCodec.write(snaps, vault, out, passphrase)
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
            }
        }
        r.snapshots.size
    }

    /** Menghapus berkas terenkripsi yang tidak dirujuk database (sisa proses yang terhenti). */
    suspend fun cleanOrphans(): Int = withContext(Dispatchers.IO) {
        val used = (dao.allFotoPaths() + dao.allLampiranPaths()).toSet()
        vault.deleteOrphans(used)
    }
}
