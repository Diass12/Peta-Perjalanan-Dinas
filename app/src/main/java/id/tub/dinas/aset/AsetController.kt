package id.tub.dinas.aset

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import id.tub.dinas.MainViewModel
import id.tub.dinas.domain.*
import id.tub.dinas.export.AsetReports
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

class GaleriHasil(val lines: List<ScanLine>, val tanpaKode: Int, val dibuat: Int, val gagal: Int, val jpegTanpaKode: List<ByteArray>)

class ImportState(val kind: String, val file: File, val report: ImportReport, val plan: AsetImport.XlsxPlan?, val timpa: Boolean = true)

/** Status & operasi layar Aset: filter, scan, import, export. Dipegang MainViewModel supaya bertahan saat layar diputar. */
class AsetController(private val vm: MainViewModel) {
    private val repo = vm.repo
    private val app get() = vm.getApplication<Application>()

    val register: StateFlow<List<AsetModel>> = repo.observeAset().stateIn(vm.viewModelScope, SharingStarted.Eagerly, emptyList())
    val fotos: StateFlow<Map<String, List<AsetFotoModel>>> =
        repo.observeAsetFoto().map { l -> l.groupBy { it.asetId } }.stateIn(vm.viewModelScope, SharingStarted.Eagerly, emptyMap())

    var scannerBuka by mutableStateOf(false)
    var fotoTanpaKode by mutableStateOf<ByteArray?>(null)   // foto terakhir di scanner yang kodenya tak terbaca
    val media: StateFlow<Map<String, List<AsetMediaModel>>> =
        repo.observeAsetMedia().map { l -> l.groupBy { it.asetId } }.stateIn(vm.viewModelScope, SharingStarted.Eagerly, emptyMap())

    var tab by mutableStateOf(0)                       // halaman: 0 Home, 1 Aset, 2 Audit, 3 Menu
    var tampilScan by mutableStateOf(false)            // dialog pilih kamera/galeri
    var query by mutableStateOf("")
    var kategori by mutableStateOf<String?>(null)
    var barcode by mutableStateOf<StatusBarcode?>(null)
    var auditFilter by mutableStateOf<AuditState?>(null)
    var peringatan by mutableStateOf<String?>(null)    // nofoto | nokode | dup | noname | nodept | noloc | badcode | noowner
    var deptFilter by mutableStateOf<String?>(null)
    var ownerFilter by mutableStateOf<String?>(null)
    var auditMode by mutableStateOf(false)
    var report by mutableStateOf<ScanReport?>(null)
    var editing by mutableStateOf<String?>(null)
    var importState by mutableStateOf<ImportState?>(null)

    fun resetFilter() { query = ""; kategori = null; barcode = null; auditFilter = null; peringatan = null; deptFilter = null; ownerFilter = null }

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
                (deptFilter == null || a.dept.equals(deptFilter, true)) &&
                (ownerFilter == null || a.owner.equals(ownerFilter, true)) &&
                when (peringatan) {
                    "nofoto" -> f[a.id].isNullOrEmpty()
                    "nokode" -> a.code.isEmpty()
                    "dup" -> KodeAset.norm(a.code) in dup
                    "noowner" -> a.owner.isBlank()
                    "noname" -> a.name.isBlank()
                    "nodept" -> a.dept.isBlank()
                    "noloc" -> a.location.isBlank()
                    "badcode" -> a.code.isNotEmpty() && KodeAset.validasi(a.code) == null
                    else -> true
                }
        }
    }

    // ---------------- scan ----------------

    /** Satu foto: baca kode (barcode + OCR dalam), cocokkan/buat aset, simpan foto. Mengembalikan laporan tanpa menampilkannya. */
    suspend fun scanFoto(uri: Uri): ScanReport {
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
        return ScanReport(lines, read.mentah.size - extra.size, if (lines.isEmpty()) photo.jpeg else null)
    }

    fun scan(uri: Uri, tempFile: File? = null) = vm.work {
        try { report = scanFoto(uri) } finally { tempFile?.delete() }
    }

    // ---------------- scan live ----------------

    private val liveLast = HashMap<String, Long>()

    /** Satu kode dari mode live. Null = kode yang sama baru saja diproses (anti-ulang 6 detik). Pair(teks, berhasilCocok). */
    suspend fun prosesLive(kode: String): Pair<String, Boolean>? {
        val k = KodeAset.norm(kode)
        val now = System.currentTimeMillis()
        if (now - (liveLast[k] ?: 0L) < 6000L) return null
        liveLast[k] = now
        val list = repo.asetList()
        val t = KodeAset.cocokkan(k, list) { it.code }.aset ?: return ("? $k — tidak terdaftar (ambil foto untuk membuat aset)") to false
        var a = t.copy(pcodes = (t.pcodes + KodeAset.norm(t.code)).distinct())
        if (auditMode) a = a.copy(state = AuditState.FOUND, auditedAt = now)
        val (st, src) = KodeAset.hitungStatusBarcode(a.code, a.pcodes, true, a.barcode, a.barcodeSrc)
        repo.saveAset(a.copy(barcode = st, barcodeSrc = src).addLog(if (auditMode) "scan live audit" else "scan live"))
        return "✔ ${a.code} — ${a.name.ifEmpty { "(tanpa nama)" }}" + (if (auditMode) " · Ditemukan" else "") to true
    }

    // ---------------- tambah dari galeri (4 cara) ----------------

    var galeriUris by mutableStateOf<List<Uri>?>(null)          // foto terpilih, menunggu pilihan cara
    var galeriHasil by mutableStateOf<GaleriHasil?>(null)
    var pilihAsetUntuk by mutableStateOf<List<Uri>?>(null)      // dialog pilih aset tujuan

    /** mode: auto | each | new | existing (target wajib). */
    fun galeri(mode: String, uris: List<Uri>, target: String? = null) = vm.work {
        galeriUris = null; pilihAsetUntuk = null
        try {
            when (mode) {
                "existing" -> {
                    val id = target ?: return@work
                    var ok = 0
                    for ((i, u) in uris.withIndex()) {
                        sibuk = "Menyimpan foto ${i + 1}/${uris.size}…"
                        runCatching { val p = withContext(Dispatchers.IO) { PhotoPipeline.process(app, u, vm.settings.photoPreset, null) }; repo.addAsetFoto(id, p.jpeg, p.takenAt ?: System.currentTimeMillis()); ok++ }
                    }
                    vm.toast("$ok foto ditambahkan")
                }
                "new" -> {
                    val a = AsetModel(id = UUID.randomUUID().toString(), sumber = "galeri", barcode = StatusBarcode.NONASSET, barcodeSrc = SumberBarcode.AUTO, createdAt = System.currentTimeMillis()).addLog("dibuat dari galeri")
                    repo.saveAset(a)
                    var ok = 0
                    for ((i, u) in uris.withIndex()) {
                        sibuk = "Menyimpan foto ${i + 1}/${uris.size}…"
                        runCatching { val p = withContext(Dispatchers.IO) { PhotoPipeline.process(app, u, vm.settings.photoPreset, null) }; repo.addAsetFoto(a.id, p.jpeg, p.takenAt ?: System.currentTimeMillis()); ok++ }
                    }
                    editing = a.id
                    vm.toast("Aset baru dengan $ok foto")
                }
                else -> { // auto / each
                    berhenti = false
                    val lines = ArrayList<ScanLine>(); val tanpaKode = ArrayList<ByteArray>()
                    var gagal = 0
                    for ((i, u) in uris.withIndex()) {
                        if (berhenti) break
                        sibuk = "Membaca foto ${i + 1}/${uris.size}… (ketuk Berhenti untuk membatalkan)"
                        val rep = try { scanFoto(u) } catch (e: Exception) { gagal++; continue }
                        lines.addAll(rep.lines)
                        if (rep.lines.isEmpty() && rep.jpeg != null) tanpaKode.add(rep.jpeg)
                    }
                    if (mode == "each" && tanpaKode.isNotEmpty()) {
                        for (j in tanpaKode) {
                            val a = AsetModel(id = UUID.randomUUID().toString(), sumber = "galeri", barcode = StatusBarcode.NONASSET, barcodeSrc = SumberBarcode.AUTO, createdAt = System.currentTimeMillis()).addLog("dibuat dari galeri")
                            repo.saveAset(a); repo.addAsetFoto(a.id, j, System.currentTimeMillis())
                        }
                        galeriHasil = GaleriHasil(lines, 0, tanpaKode.size, gagal, emptyList())
                    } else galeriHasil = GaleriHasil(lines, tanpaKode.size, 0, gagal, tanpaKode)
                }
            }
        } finally { sibuk = null }
    }

    /** Buat aset tanpa kode (satu per foto) dari foto yang kodenya tidak terbaca. */
    fun buatTanpaKode(jpegs: List<ByteArray>) = vm.work {
        for (j in jpegs) {
            val a = AsetModel(id = UUID.randomUUID().toString(), sumber = "galeri", barcode = StatusBarcode.NONASSET, barcodeSrc = SumberBarcode.AUTO, createdAt = System.currentTimeMillis()).addLog("dibuat dari foto tanpa kode")
            repo.saveAset(a); repo.addAsetFoto(a.id, j, System.currentTimeMillis())
        }
        galeriHasil = null
        vm.toast("${jpegs.size} aset tanpa kode dibuat")
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

    /** kind: VIDEO | DOC. Nama dan ekstensi diambil dari berkas yang dipilih. */
    fun tambahMedia(asetId: String, kind: String, uris: List<Uri>) = vm.work {
        val cr = app.contentResolver
        var ok = 0
        for (u in uris) {
            try {
                val name = cr.query(u, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null }
                    ?: if (kind == "VIDEO") "video.mp4" else "dokumen"
                val ext = name.substringAfterLast('.', "").lowercase().filter { it.isLetterOrDigit() }.take(5).ifEmpty { if (kind == "VIDEO") "mp4" else "bin" }
                val ins = cr.openInputStream(u) ?: throw IllegalArgumentException("Berkas tidak dapat dibuka")
                ins.use { repo.addAsetMedia(asetId, kind, name, ext, it) }
                ok++
            } catch (e: Exception) { vm.toast("Dilewati: ${e.message ?: "tidak dapat dibaca"}") }
        }
        if (ok > 0) vm.toast("$ok berkas disimpan terenkripsi")
    }

    fun tambahMediaFile(asetId: String, kind: String, file: File, name: String, ext: String) = vm.work {
        try { file.inputStream().use { repo.addAsetMedia(asetId, kind, name, ext, it) }; vm.toast("Video disimpan terenkripsi") } finally { file.delete() }
    }

    fun hapusMedia(id: String) = vm.work { repo.deleteAsetMedia(id) }

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


    // ---------------- master, pilih banyak, operasi massal ----------------

    val master by lazy { MasterStore(app) }
    var masterVer by mutableStateOf(0)
    var selMode by mutableStateOf(false)
    var selected by mutableStateOf<Set<String>>(emptySet())
    var sibuk by mutableStateOf<String?>(null)          // pesan progres operasi panjang
    @Volatile var berhenti = false

    fun toggleSel(id: String) { selected = if (id in selected) selected - id else selected + id; if (selected.isEmpty()) selMode = false }
    fun mulaiPilih(id: String) { selMode = true; selected = setOf(id) }
    fun selesaiPilih() { selMode = false; selected = emptySet() }

    fun opsi(f: MasterField, list: List<AsetModel>): List<String> { masterVer; return master.options(f, list) }

    /** Ganti nilai master di semua aset yang memakainya. */
    fun renameMaster(f: MasterField, lama: String, baru: String) = vm.work {
        val t = baru.trim(); if (t.isEmpty()) return@work
        master.rename(f, lama, t)
        var n = 0
        for (a in repo.asetList()) if (f.get(a).equals(lama, true)) { repo.saveAset(f.set(a, t).addLog("master ${f.label}: $lama → $t")); n++ }
        masterVer++
        vm.toast("$n aset diperbarui")
    }

    fun bulkEdit(ids: Set<String>, ket: String, ubah: (AsetModel) -> AsetModel) = vm.work {
        var n = 0
        for (a in repo.asetList()) if (a.id in ids) { repo.saveAset(ubah(a).addLog(ket)); n++ }
        vm.toast("$n aset diubah")
    }

    fun bulkSimpan(daftar: List<AsetModel>) = vm.work {
        for (a in daftar) {
            val (st, src) = KodeAset.hitungStatusBarcode(a.code, a.pcodes, a.pcodes.isNotEmpty(), a.barcode, a.barcodeSrc)
            repo.saveAset(a.copy(barcode = st, barcodeSrc = src).addLog("edit tabel"))
        }
        vm.toast("${daftar.size} aset disimpan")
    }

    fun bulkHapus(ids: Set<String>) = vm.work { ids.forEach { repo.deleteAset(it) }; vm.toast("${ids.size} aset dihapus"); selesaiPilih() }

    /** mode "urut": foto ke-i untuk aset ke-i; "sama": semua foto ke semua aset terpilih. */
    fun bulkFoto(ids: List<String>, uris: List<Uri>, mode: String) = vm.work {
        try {
            var ok = 0
            val pairs = if (mode == "urut") ids.zip(uris) else ids.flatMap { id -> uris.map { id to it } }
            for ((i, pr) in pairs.withIndex()) {
                sibuk = "Menyimpan foto ${i + 1}/${pairs.size}…"
                try {
                    val p = withContext(Dispatchers.IO) { PhotoPipeline.process(app, pr.second, vm.settings.photoPreset, null) }
                    repo.addAsetFoto(pr.first, p.jpeg, p.takenAt ?: System.currentTimeMillis()); ok++
                } catch (e: Exception) { vm.toast("Foto dilewati: ${e.message ?: "tidak terbaca"}") }
            }
            vm.toast("$ok foto ditambahkan")
        } finally { sibuk = null }
    }

    /** Hitung ulang status barcode semua aset yang tidak diatur manual. */
    fun hitungUlang() = vm.work {
        var n = 0
        for (a in repo.asetList()) {
            if (a.barcodeSrc == SumberBarcode.MANUAL) continue
            val (st, src) = KodeAset.hitungStatusBarcode(a.code, a.pcodes, a.pcodes.isNotEmpty(), a.barcode, a.barcodeSrc)
            if (st != a.barcode || src != a.barcodeSrc) { repo.saveAset(a.copy(barcode = st, barcodeSrc = src).addLog("hitung ulang barcode")); n++ }
        }
        vm.toast("$n aset diperbarui statusnya")
    }

    /** OCR ulang (mode dalam) pada maksimum 3 foto tiap aset berfoto. Bisa dihentikan lewat `berhenti`. */
    fun ocrSemua() = vm.work {
        berhenti = false
        try {
            val daftar = repo.asetBundles().filter { it.fotos.isNotEmpty() }
            var ubah = 0
            for ((i, b) in daftar.withIndex()) {
                if (berhenti) break
                sibuk = "OCR ${i + 1}/${daftar.size} — ketuk Berhenti untuk membatalkan"
                val kode = LinkedHashSet<String>()
                for (f in b.fotos.take(3)) {
                    val bytes = repo.asetFotoBytes(f.path) ?: continue
                    kode.addAll(runCatching { CodeScanner.bacaJpeg(bytes).kode }.getOrDefault(emptyList()))
                    if (kode.isNotEmpty()) break
                }
                if (kode.isEmpty()) continue
                var a = repo.asetById(b.aset.id) ?: continue
                val pc = (a.pcodes + kode).distinct()
                if (pc != a.pcodes) {
                    a = a.copy(pcodes = pc)
                    if (a.code.isBlank() && kode.size == 1) a = a.copy(code = kode.first())
                    val (st, src) = KodeAset.hitungStatusBarcode(a.code, a.pcodes, true, a.barcode, a.barcodeSrc)
                    repo.saveAset(a.copy(barcode = st, barcodeSrc = src).addLog("OCR ulang")); ubah++
                }
            }
            vm.toast(if (berhenti) "Dihentikan. $ubah aset diperbarui" else "Selesai. $ubah aset diperbarui")
        } finally { sibuk = null }
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
            val imp = AsetImporter(repo, existing, counts, dryRun = true, timpa = true)
            if (head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte()) {
                val plan = withContext(Dispatchers.IO) { AsetImport.planXlsx(file) }
                for ((m, c, _) in plan.rows) imp.add(m, c) { emptyList() }
                importState?.file?.delete()
                importState = ImportState("xlsx", file, imp.report("Sheet \"${plan.sheet}\", judul di baris ${plan.headerRow}. Kolom dikenali: ${plan.kolom.joinToString(", ")}"), plan, true)
            } else {
                withContext(Dispatchers.IO) { file.inputStream().use { s -> AsetImport.streamJson(s, false) { m, c, p -> imp.add(m, c, p) } } }
                importState?.file?.delete()
                importState = ImportState("json", file, imp.report("Cadangan AsetScan (JSON)"), null, true)
            }
        } catch (e: Exception) { file.delete(); throw e }
    }

    /** Ganti mode import (lengkapi / perbarui) lalu hitung ulang pratinjau dari berkas yang sama. */
    fun ubahMode(timpa: Boolean) {
        val st = importState ?: return
        vm.work {
            val bundles = repo.asetBundles()
            val imp = AsetImporter(repo, bundles.map { it.aset }, bundles.associate { it.aset.id to it.fotos.size }, dryRun = true, timpa = timpa)
            if (st.kind == "xlsx") { for ((m, c, _) in st.plan!!.rows) imp.add(m, c) { emptyList() } }
            else withContext(Dispatchers.IO) { st.file.inputStream().use { s -> AsetImport.streamJson(s, false) { m, c, p -> imp.add(m, c, p) } } }
            importState = ImportState(st.kind, st.file, imp.report(st.report.info), st.plan, timpa)
        }
    }

    fun batalImport() { importState?.file?.delete(); importState = null }

    fun terapkanImport() {
        val st = importState ?: return
        vm.work {
            val bundles = repo.asetBundles()
            val imp = AsetImporter(repo, bundles.map { it.aset }, bundles.associate { it.aset.id to it.fotos.size }, dryRun = false, timpa = st.timpa)
            withContext(Dispatchers.IO) {
                if (st.kind == "xlsx") {
                    for ((m, c, entries) in st.plan!!.rows) imp.add(m, c) { entries.mapNotNull { e -> XlsxReader.readImage(st.file, e)?.let { b -> AsetImport.toJpeg(b) } } }
                } else {
                    st.file.inputStream().use { s -> AsetImport.streamJson(s, true) { m, c, p -> imp.add(m, c, p) } }
                }
            }
            st.file.delete(); importState = null
            vm.toast("Import selesai: ${imp.baru} baru, ${imp.update} ${if (st.timpa) "diperbarui" else "dilengkapi"}, ${imp.sama} sama, ${imp.ganda} ganda di file")
        }
    }

    // ---------------- export ----------------

    private fun company(daftar: List<AsetModel>) = daftar.map { it.owner }.filter { it.isNotBlank() }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: "PT TRI USAHA BARU"

    /** format: pdf | html | label. */
    fun exportLain(uri: Uri, format: String, maxFoto: Int, daftar: List<AsetModel>) = vm.work {
        val fm = fotos.value
        val co = company(daftar)
        withContext(Dispatchers.IO) {
            val os = app.contentResolver.openOutputStream(uri, "w") ?: throw IllegalArgumentException("Tidak dapat menulis berkas")
            val baca: (String) -> ByteArray? = { p -> runCatching { repo.vault.read(p) }.getOrNull() }
            os.use {
                when (format) {
                    "pdf" -> AsetReports.laporanPdf(daftar, fm, co, maxFoto, baca, it)
                    "html" -> AsetReports.laporanHtml(daftar, fm, co, maxFoto, baca, it)
                    "json" -> AsetJson.tulis(daftar, fm.mapValues { e -> e.value.let { l -> if (maxFoto == 0) emptyList() else l } }, master, repo.asetList(), baca, it)
                    else -> AsetReports.labelPdf(daftar, co, it)
                }
            }
        }
        vm.toast(when (format) { "label" -> "Label QR tersimpan (${daftar.count { it.code.isNotBlank() }} label)"; "pdf" -> "PDF tersimpan (${daftar.size} aset)"; "json" -> "Cadangan JSON AsetScan tersimpan (${daftar.size} aset)"; else -> "HTML tersimpan (${daftar.size} aset)" })
    }

    fun exportKartu(uri: Uri, a: AsetModel) = vm.work {
        val fm = fotos.value[a.id].orEmpty()
        withContext(Dispatchers.IO) {
            val os = app.contentResolver.openOutputStream(uri, "w") ?: throw IllegalArgumentException("Tidak dapat menulis berkas")
            os.use { AsetReports.kartuPdf(a, fm, { p -> runCatching { repo.vault.read(p) }.getOrNull() }, it) }
        }
        vm.toast("Kartu aset tersimpan")
    }


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
