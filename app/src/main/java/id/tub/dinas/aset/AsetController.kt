package id.tub.dinas.aset

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import id.tub.dinas.MainViewModel
import id.tub.dinas.domain.*
import id.tub.dinas.export.MemoFat
import id.tub.dinas.media.PhotoPipeline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class ScanLine(val kode: String, val asetId: String?, val nama: String, val fuzzy: Boolean, val baru: Boolean, val catatan: String)
class ScanReport(val lines: List<ScanLine>, val diabaikan: Int, val jpeg: ByteArray?)

class ImportState(val kind: String, val file: File, val report: ImportReport, val plan: AsetImport.XlsxPlan?)

/** Status & operasi layar Aset: filter, scan, import, export. Dipegang MainViewModel supaya bertahan saat layar diputar. */
class AsetController(private val vm: MainViewModel) {
    private val repo = vm.repo
    private val app get() = vm.getApplication<Application>()

    val register: StateFlow<List<AsetModel>> = repo.observeAset().stateIn(vm.viewModelScope, SharingStarted.Eagerly, emptyList())
    val fotos: StateFlow<Map<String, List<AsetFotoModel>>> =
        repo.observeAsetFoto().map { l -> l.groupBy { it.asetId } }.stateIn(vm.viewModelScope, SharingStarted.Eagerly, emptyMap())

    var tab by mutableStateOf(0)                       // 0 daftar, 1 audit
    var query by mutableStateOf("")
    var kategori by mutableStateOf<String?>(null)
    var barcode by mutableStateOf<StatusBarcode?>(null)
    var auditFilter by mutableStateOf<AuditState?>(null)
    var peringatan by mutableStateOf<String?>(null)    // nofoto | nokode | dup
    var auditMode by mutableStateOf(false)
    var report by mutableStateOf<ScanReport?>(null)
    var editing by mutableStateOf<String?>(null)
    var importState by mutableStateOf<ImportState?>(null)

    fun duplikat(list: List<AsetModel>): Set<String> =
        list.map { KodeAset.norm(it.code) }.filter { it.isNotEmpty() }.groupingBy { it }.eachCount().filter { it.value > 1 }.keys

    fun filtered(list: List<AsetModel>): List<AsetModel> {
        val q = query.trim().lowercase()
        val dup = if (peringatan == "dup") duplikat(list) else emptySet()
        val f = fotos.value
        return list.filter { a ->
            (q.isEmpty() || listOf(a.code, a.name, a.location, a.dept, a.owner, a.user).any { it.lowercase().contains(q) }) &&
                (kategori == null || a.category == kategori) &&
                (barcode == null || a.barcode == barcode) &&
                (auditFilter == null || a.state == auditFilter) &&
                when (peringatan) {
                    "nofoto" -> f[a.id].isNullOrEmpty()
                    "nokode" -> a.code.isEmpty()
                    "dup" -> KodeAset.norm(a.code) in dup
                    else -> true
                }
        }
    }

    // ---------------- scan ----------------

    fun scan(uri: Uri, tempFile: File? = null) = vm.work {
        try {
            val read = withContext(Dispatchers.IO) { CodeScanner.baca(app, uri) }
            val photo = withContext(Dispatchers.IO) { PhotoPipeline.process(app, uri, vm.settings.photoPreset, null) }
            var list = repo.asetList()
            val audit = auditMode
            val now = System.currentTimeMillis()
            val extra = read.mentah.filter { r -> val n = KodeAset.norm(r); n.isNotEmpty() && list.any { it.code.isNotEmpty() && KodeAset.norm(it.code) == n } }
            val codes = (read.kode + extra.map { KodeAset.norm(it) }).distinct()
            val lines = ArrayList<ScanLine>()
            for (kode in codes) {
                val m = KodeAset.cocokkan(kode, list) { it.code }
                val target = m.aset
                if (target != null) {
                    var a = target.copy(pcodes = (target.pcodes + KodeAset.norm(target.code)).distinct())
                    if (audit) a = a.copy(state = AuditState.FOUND, auditedAt = now)
                    val (st, src) = KodeAset.hitungStatusBarcode(a.code, a.pcodes, true, a.barcode, a.barcodeSrc)
                    a = a.copy(barcode = st, barcodeSrc = src).addLog(if (audit) "scan audit" else "scan")
                    repo.saveAset(a)
                    repo.addAsetFoto(a.id, photo.jpeg, photo.takenAt ?: now)
                    list = list.map { if (it.id == a.id) a else it }
                    lines.add(ScanLine(a.code, a.id, a.name, m.fuzzy, false, if (audit) "Ditemukan" else "Cocok"))
                } else {
                    val k = KodeAset.norm(kode)
                    val kelas = KodeAset.kelasDariKode(k)
                    val a = AsetModel(
                        id = UUID.randomUUID().toString(), code = k, name = "", category = kelas?.kategori ?: KodeAset.tebakKategori(k),
                        classif = kelas?.nama.orEmpty(), pcodes = listOf(k), barcode = StatusBarcode.TAGGED, barcodeSrc = SumberBarcode.OCR,
                        state = if (audit) AuditState.FOUND else AuditState.PENDING, auditedAt = if (audit) now else null,
                        sumber = "scan", createdAt = now,
                    ).addLog(if (audit) "dibuat dari scan audit" else "dibuat dari scan")
                    repo.saveAset(a)
                    repo.addAsetFoto(a.id, photo.jpeg, photo.takenAt ?: now)
                    list = list + a
                    lines.add(ScanLine(k, a.id, "", false, true, "Aset baru — lengkapi datanya"))
                }
            }
            report = ScanReport(lines, read.mentah.size - extra.size, if (lines.isEmpty()) photo.jpeg else null)
        } finally { tempFile?.delete() }
    }

    /** Aset baru tanpa kode dari foto yang kodenya tidak terbaca; langsung membuka formulirnya. */
    fun buatDariFoto(jpeg: ByteArray) = vm.work {
        val a = AsetModel(id = UUID.randomUUID().toString(), sumber = "scan", barcode = StatusBarcode.NONASSET, barcodeSrc = SumberBarcode.AUTO, createdAt = System.currentTimeMillis()).addLog("dibuat dari foto")
        repo.saveAset(a)
        repo.addAsetFoto(a.id, jpeg, System.currentTimeMillis())
        report = null
        editing = a.id
    }

    fun baru() {
        val a = AsetModel(id = UUID.randomUUID().toString(), barcode = StatusBarcode.NONASSET, barcodeSrc = SumberBarcode.AUTO, createdAt = System.currentTimeMillis())
        pendingNew = a
        editing = a.id
    }

    /** Aset baru yang belum disimpan (dibuat saat tombol + ditekan). */
    var pendingNew by mutableStateOf<AsetModel?>(null)

    fun simpan(m: AsetModel, onDone: () -> Unit = {}) = vm.work(onDone = onDone) {
        var a = m
        val (st, src) = KodeAset.hitungStatusBarcode(a.code, a.pcodes, a.pcodes.isNotEmpty(), a.barcode, a.barcodeSrc)
        a = a.copy(barcode = st, barcodeSrc = src)
        if (a.classif.isBlank()) KodeAset.kelasDariKode(a.code)?.let { a = a.copy(classif = it.nama, category = it.kategori) }
        repo.saveAset(a.addLog("diubah"))
        pendingNew = null
        vm.toast("Tersimpan")
    }

    fun hapus(id: String) = vm.work { repo.deleteAset(id); vm.toast("Aset dihapus") }

    fun tambahFoto(asetId: String, uris: List<Uri>, temp: File? = null) = vm.work {
        try {
            var ok = 0
            for (u in uris) {
                try {
                    val p = withContext(Dispatchers.IO) { PhotoPipeline.process(app, u, vm.settings.photoPreset, null) }
                    repo.addAsetFoto(asetId, p.jpeg, p.takenAt ?: System.currentTimeMillis())
                    ok++
                } catch (e: Exception) { vm.toast("Foto dilewati: ${e.message ?: "tidak dapat dibaca"}") }
            }
            if (ok > 0) vm.toast("$ok foto disimpan terenkripsi")
        } finally { temp?.delete() }
    }

    fun hapusFoto(id: String) = vm.work { repo.deleteAsetFoto(id) }
    fun jadikanUtama(asetId: String, fotoId: String) = vm.work { repo.setAsetCover(asetId, fotoId) }

    fun setState(a: AsetModel, s: AuditState) = vm.work {
        repo.saveAset(a.copy(state = s, auditedAt = if (s == AuditState.PENDING) null else System.currentTimeMillis()).addLog("audit: ${s.label}"))
    }

    fun resetAudit() = vm.work { repo.resetAudit(); vm.toast("Sesi audit baru dimulai") }

    fun buatTemuan(asetId: String, dinasId: String) = vm.work {
        repo.asetToTemuan(asetId, dinasId, null) ?: throw IllegalArgumentException("Aset tidak ditemukan")
        vm.toast("Temuan dibuat di dinas terpilih")
    }

    // ---------------- import ----------------

    fun siapkanImport(uri: Uri) = vm.work {
        val file = File(app.cacheDir, "impor").apply { mkdirs() }.let { File(it, "impor_${System.nanoTime()}.dat") }
        withContext(Dispatchers.IO) {
            (app.contentResolver.openInputStream(uri) ?: throw IllegalArgumentException("Berkas tidak dapat dibuka")).use { i ->
                file.outputStream().use { o -> i.copyTo(o) }
            }
        }
        try {
            val head = withContext(Dispatchers.IO) { file.inputStream().use { s -> ByteArray(4).also { s.read(it) } } }
            val bundles = repo.asetBundles()
            val existing = bundles.map { it.aset }
            val counts = bundles.associate { it.aset.id to it.fotos.size }
            val imp = AsetImporter(repo, existing, counts, dryRun = true)
            if (head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte()) {
                val plan = withContext(Dispatchers.IO) { AsetImport.planXlsx(file) }
                for ((m, c, _) in plan.rows) imp.add(m, c) { emptyList() }
                importState?.file?.delete()
                importState = ImportState("xlsx", file, imp.report("Sheet \"${plan.sheet}\", judul di baris ${plan.headerRow}. Kolom dikenali: ${plan.kolom.joinToString(", ")}"), plan)
            } else {
                withContext(Dispatchers.IO) { file.inputStream().use { s -> AsetImport.streamJson(s, false) { m, c, p -> imp.add(m, c, p) } } }
                importState?.file?.delete()
                importState = ImportState("json", file, imp.report("Cadangan AsetScan (JSON)"), null)
            }
        } catch (e: Exception) { file.delete(); throw e }
    }

    fun batalImport() { importState?.file?.delete(); importState = null }

    fun terapkanImport() {
        val st = importState ?: return
        vm.work {
            val bundles = repo.asetBundles()
            val imp = AsetImporter(repo, bundles.map { it.aset }, bundles.associate { it.aset.id to it.fotos.size }, dryRun = false)
            withContext(Dispatchers.IO) {
                if (st.kind == "xlsx") {
                    for ((m, c, entries) in st.plan!!.rows) imp.add(m, c) { entries.mapNotNull { e -> XlsxReader.readImage(st.file, e)?.let { b -> AsetImport.toJpeg(b) } } }
                } else {
                    st.file.inputStream().use { s -> AsetImport.streamJson(s, true) { m, c, p -> imp.add(m, c, p) } }
                }
            }
            st.file.delete(); importState = null
            vm.toast("Import selesai: ${imp.baru} baru, ${imp.update} dilengkapi, ${imp.sama} sama, ${imp.ganda} ganda di file")
        }
    }

    // ---------------- export ----------------

    fun exportMemo(uri: Uri, maxFoto: Int, daftar: List<AsetModel>) = vm.work {
        val bundles = fotos.value
        val company = daftar.map { it.owner }.filter { it.isNotBlank() }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: "PT TRI USAHA BARU"
        withContext(Dispatchers.IO) {
            val os = app.contentResolver.openOutputStream(uri, "w") ?: throw IllegalArgumentException("Tidak dapat menulis berkas")
            os.use { MemoFat.export(daftar, bundles, company, maxFoto, { p -> runCatching { repo.vault.read(p) }.getOrNull() }, it) }
        }
        vm.toast("Excel Memo FAT tersimpan (${daftar.size} aset)")
    }
}
