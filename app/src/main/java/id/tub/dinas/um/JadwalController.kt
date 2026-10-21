package id.tub.dinas.um

import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import id.tub.dinas.MainViewModel
import id.tub.dinas.domain.*
import id.tub.dinas.export.XStyle
import id.tub.dinas.export.XlsxWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

fun jamOk(s: String) = s.isEmpty() || Regex("^([01]?\\d|2[0-3]):[0-5]\\d$").matches(s.trim())
fun normJam(s: String): String = s.trim().let { if (it.isEmpty()) "" else it.split(":").let { p -> "%02d:%s".format(p[0].toInt(), p[1]) } }

/** Status & operasi layar Jadwal (audit, meeting, dll). */
class JadwalController(private val vm: MainViewModel) {
    private val app get() = vm.getApplication<android.app.Application>()
    val daftar: StateFlow<List<JadwalModel>> = vm.repo.observeJadwal().stateIn(vm.viewModelScope, SharingStarted.Eagerly, emptyList())

    var edit by mutableStateOf<JadwalModel?>(null)
    var filterJenis by mutableStateOf<JadwalJenis?>(null)
    /** 0 = akan datang, 1 = selesai, 2 = semua */
    var filterStatus by mutableStateOf(0)

    fun baru(jenis: JadwalJenis = JadwalJenis.MEETING) { edit = JadwalModel("", jenis = jenis, tanggal = todayIso()) }

    fun simpan(m: JadwalModel) = vm.work { vm.repo.saveJadwal(m); edit = null; vm.toast("Jadwal tersimpan") }
    fun hapus(id: String) = vm.work { edit = null; vm.repo.deleteJadwal(id); vm.toast("Jadwal dihapus") }
    fun tandai(m: JadwalModel, selesai: Boolean) = vm.work { vm.repo.saveJadwal(m.copy(selesai = selesai)) }

    /** Membuka aplikasi kalender dengan acara terisi (tanpa izin kalender). */
    fun keKalender(m: JadwalModel) {
        val f = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        val jam = m.jamMulai.ifEmpty { null }
        val mulai = runCatching { f.parse(m.tanggal + " " + (jam ?: "00:00"))!!.time }.getOrNull() ?: return vm.toast("Tanggal belum valid")
        val akhir = if (m.jamSelesai.isNotEmpty()) runCatching { f.parse(m.tanggal + " " + m.jamSelesai)!!.time }.getOrNull() ?: (mulai + 3600_000) else mulai + 3600_000
        val i = Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, "[${m.jenis.label}] ${m.judul}")
            .putExtra(CalendarContract.Events.EVENT_LOCATION, m.lokasi)
            .putExtra(CalendarContract.Events.DESCRIPTION, listOf(m.peserta.takeIf { it.isNotBlank() }?.let { "Peserta: $it" }, m.catatan.takeIf { it.isNotBlank() }).filterNotNull().joinToString("\n"))
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, mulai).putExtra(CalendarContract.EXTRA_EVENT_END_TIME, akhir)
            .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, jam == null)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try { app.startActivity(i) } catch (_: Exception) { vm.toast("Tidak ada aplikasi kalender di perangkat ini") }
    }

    fun ekspor(uri: Uri, daftar: List<JadwalModel>) = vm.work {
        withContext(Dispatchers.IO) {
            val os = app.contentResolver.openOutputStream(uri, "w") ?: throw IllegalArgumentException("Tidak dapat menulis berkas")
            os.use { tulisExcel(daftar, it) }
        }
        vm.hasil("Berkas berhasil diekspor", "Rekap jadwal: ${daftar.size} agenda.", uri)
    }

    private val HEAD = XStyle(bold = true, color = "FFFFFF", fill = "1F6FE0", h = "center", v = "center", border = true)
    private val CELL = XStyle(border = true, v = "center")

    private fun tulisExcel(daftar: List<JadwalModel>, out: java.io.OutputStream) {
        val wb = XlsxWriter()
        val sh = wb.sheet("Jadwal")
        sh.colWidths(listOf(5.0, 13.0, 11.0, 14.0, 12.0, 38.0, 24.0, 26.0, 12.0, 34.0))
        sh.text(0, 0, "Rekap Jadwal", XStyle(bold = true, size = 14.0))
        listOf("No.", "Tanggal", "Hari", "Jam", "Jenis", "Agenda", "Lokasi", "Peserta", "Status", "Catatan").forEachIndexed { c, t -> sh.text(2, c, t, HEAD) }
        sh.rowHeight(2, 28.0)
        val hari = SimpleDateFormat("EEEE", Locale("id", "ID")); val iso = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        daftar.forEachIndexed { i, m ->
            val r = 3 + i
            sh.number(r, 0, (i + 1).toDouble(), CELL); sh.text(r, 1, fmtTgl(m.tanggal), CELL)
            sh.text(r, 2, runCatching { hari.format(iso.parse(m.tanggal)!!) }.getOrDefault(""), CELL)
            sh.text(r, 3, listOf(m.jamMulai, m.jamSelesai).filter { it.isNotEmpty() }.joinToString(" – "), CELL)
            sh.text(r, 4, m.jenis.label, CELL); sh.text(r, 5, m.judul, CELL); sh.text(r, 6, m.lokasi, CELL); sh.text(r, 7, m.peserta, CELL)
            sh.text(r, 8, if (m.selesai) "Selesai" else "Belum", CELL); sh.text(r, 9, m.catatan, CELL)
        }
        wb.write(out)
    }
}
