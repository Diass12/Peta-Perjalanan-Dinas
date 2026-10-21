package id.tub.dinas.um

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import id.tub.dinas.MainViewModel
import id.tub.dinas.domain.*
import id.tub.dinas.media.PhotoPipeline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import java.io.File
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun rupiah(d: Double): String {
    val nf = NumberFormat.getNumberInstance(Locale("id", "ID")).apply { maximumFractionDigits = 0 }
    return (if (d < 0) "-Rp " else "Rp ") + nf.format(Math.abs(d))
}
fun todayIso(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
/** yyyy-MM-dd -> dd/MM/yyyy untuk kolom isian. */
fun isoToInput(s: String): String = Regex("^(\\d{4})-(\\d{2})-(\\d{2})$").find(s)?.let { "${it.groupValues[3]}/${it.groupValues[2]}/${it.groupValues[1]}" } ?: s
/** dd/MM/yyyy -> yyyy-MM-dd; null bila tidak valid. Kosong dianggap valid (""). */
fun inputToIso(s: String): String? {
    val t = s.trim(); if (t.isEmpty()) return ""
    val m = Regex("^(\\d{1,2})[/.-](\\d{1,2})[/.-](\\d{4})$").find(t) ?: return null
    val d = m.groupValues[1].toInt(); val mo = m.groupValues[2].toInt()
    if (d !in 1..31 || mo !in 1..12) return null
    return "%s-%02d-%02d".format(m.groupValues[3], mo, d)
}
fun fmtTgl(iso: String): String = if (iso.isEmpty()) "" else isoToInput(iso)

/** Status & operasi layar Uang Muka. Dipegang MainViewModel supaya bertahan saat layar diputar. */
class UmController(private val vm: MainViewModel) {
    private val app get() = vm.getApplication<android.app.Application>()
    private val repo get() = vm.repo

    val bundles: StateFlow<List<UmBundle>> = repo.observeUmBundles().stateIn(vm.viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Pengajuan yang sedang dibuka; null = daftar. */
    var detailId by mutableStateOf<String?>(null)
    var editUm by mutableStateOf<UangMukaModel?>(null)
    var editBonId by mutableStateOf<String?>(null)
    var viewer by mutableStateOf<Pair<String, Int>?>(null)   // bonId, indeks foto
    var exportAsk by mutableStateOf(false)

    fun pengajuanBaru() {
        val n = bundles.value.size + 1
        editUm = UangMukaModel(id = "", nomor = "UM-" + SimpleDateFormat("yyMMdd", Locale.US).format(Date()) + "-" + n, tglPengajuan = todayIso(), pemohon = vm.settings.playerName)
    }

    fun simpanUm(m: UangMukaModel) = vm.work {
        val id = repo.saveUm(m)
        editUm = null
        if (m.id.isEmpty()) detailId = id
        vm.toast("Tersimpan")
    }

    fun ubahStatus(u: UangMukaModel, s: UmStatus) = vm.work {
        var n = u.copy(status = s)
        if (s != UmStatus.DIAJUKAN && n.jumlahCair == 0.0) n = n.copy(jumlahCair = n.jumlahDiajukan, tglCair = n.tglCair.ifEmpty { todayIso() })
        repo.saveUm(n)
    }

    fun hapusUm(id: String) = vm.work { detailId = null; repo.deleteUm(id); vm.toast("Pengajuan dihapus") }

    /** Membuat bon kosong lalu membukanya; bon kosong yang ditinggalkan dibuang oleh [tutupBon]. */
    fun bonBaru(umId: String) = vm.work {
        val id = repo.saveBon(BonModel("", umId, tanggal = todayIso(), kategori = BonKategori.LAINNYA))
        editBonId = id
    }

    fun simpanBon(m: BonModel) = vm.work { repo.saveBon(m); editBonId = null; vm.toast("Bon tersimpan") }

    fun hapusBon(id: String) = vm.work { editBonId = null; repo.deleteBon(id); vm.toast("Bon dihapus") }

    /** Menutup editor tanpa menyimpan; bon yang masih kosong (baru dibuat) dibuang. */
    fun tutupBon(bonId: String) = vm.work {
        editBonId = null
        val b = bundles.value.flatMap { it.bons }.firstOrNull { it.bon.id == bonId } ?: return@work
        if (b.bon.jumlah == 0.0 && b.bon.uraian.isBlank() && b.fotos.isEmpty()) repo.deleteBon(bonId)
    }

    fun tambahFoto(bonId: String, uris: List<Uri>, temp: File? = null) = vm.work {
        try {
            var ok = 0
            for (u in uris) {
                try {
                    val p = withContext(Dispatchers.IO) { PhotoPipeline.process(app, u, vm.settings.photoPreset, null) }
                    repo.addBonFoto(bonId, p.jpeg); ok++
                } catch (e: Exception) { vm.toast("Foto dilewati: ${e.message ?: "tidak dapat dibaca"}") }
            }
            if (ok > 0) vm.toast("$ok foto bon disimpan terenkripsi")
        } finally { temp?.delete() }
    }

    fun hapusFoto(id: String) = vm.work { repo.deleteBonFoto(id) }

    // ---------------- ekspor / impor ----------------

    fun ekspor(uri: Uri, format: String, daftar: List<UmBundle>) = vm.work {
        withContext(Dispatchers.IO) {
            val os = app.contentResolver.openOutputStream(uri, "w") ?: throw IllegalArgumentException("Tidak dapat menulis berkas")
            val baca: (String) -> ByteArray? = { p -> runCatching { repo.vault.read(p) }.getOrNull() }
            os.use { if (format == "json") UmTransfer.tulisJson(daftar, baca, it) else UmTransfer.tulisExcel(daftar, baca, it) }
        }
        vm.hasil("Berkas berhasil diekspor", "${if (format == "json") "JSON" else "Excel"}: ${daftar.size} pengajuan.", uri)
    }

    fun impor(uri: Uri) = vm.work {
        val file = File(app.cacheDir, "impor").apply { mkdirs() }.let { File(it, "um_${System.nanoTime()}.dat") }
        try {
            val r = withContext(Dispatchers.IO) {
                (app.contentResolver.openInputStream(uri) ?: throw IllegalArgumentException("Berkas tidak dapat dibuka")).use { i -> file.outputStream().use { o -> i.copyTo(o) } }
                UmTransfer.impor(file, repo)
            }
            vm.toast("Impor: ${r.um} pengajuan, ${r.bon} bon baru, ${r.foto} foto" + if (r.lewat > 0) " (${r.lewat} sudah ada dilewati)" else "")
        } finally { file.delete() }
    }
}
