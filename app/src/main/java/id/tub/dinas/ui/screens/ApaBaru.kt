package id.tub.dinas.ui.screens

import id.tub.dinas.ui.components.Text
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.tub.dinas.MainViewModel
import id.tub.dinas.ui.Screen
import id.tub.dinas.ui.theme.C

/** Satu fitur yang berubah/baru, lengkap dengan langkah singkat memakainya dan layar tujuan "Coba sekarang". */
class FiturBaru(val ikon: String, val judul: String, val isi: String, val langkah: List<String>, val tujuan: Screen? = null)
class RilisInfo(val kode: Int, val nama: String, val fitur: List<FiturBaru>)

/**
 * Catatan rilis + panduan singkat. SETIAP rilis baru: tambahkan RilisInfo di paling atas [daftar]
 * (kode = versionCode). Dialog "Yang baru" muncul sekali setelah pembaruan, panduan bisa dibuka lagi dari Pengaturan.
 */
object ApaBaru {
    val daftar = listOf(
        RilisInfo(6, "1.4.0", listOf(
            FiturBaru("🧭", "Panduan Travel", "Menu baru untuk mencari kota tujuan: wisata, kuliner khas, spot foto, dan tombol pencarian hotel ke Traveloka, Google Maps, dan Booking.com. Data awal 6 kota (data sampel). Wisata bisa dijadikan titik dinas, dan kota bisa dijadikan dinas baru.",
                listOf("Ketuk menu Panduan di bawah.", "Ketik kota atau makanan di kolom cari.", "Buka sebuah kota, lalu pilih tab Wisata, Kuliner, Hotel, atau Spot foto."), null),
        )),
        RilisInfo(5, "1.3.1", listOf(
            FiturBaru("🔍", "Pencarian Beranda lebih pintar", "Ketik nama kota atau tempat di “Mau dinas ke mana?” dan hasilnya dikelompokkan: Dinas, Titik, Temuan, Aset, dan Jadwal. Ketuk hasilnya untuk langsung membuka. Kalau belum ada, tersedia tombol untuk membuat dinas baru dengan nama itu.",
                listOf("Di Beranda ketik misalnya “Tobelo”.", "Ketuk salah satu hasil, atau “Buat dinas” bila belum ada."), null),
            FiturBaru("✨", "Analisis AI Gemini lebih andal", "Saat server Gemini sibuk atau lambat, aplikasi mencoba ulang sendiri dan beralih ke model cadangan, sehingga lebih jarang gagal atau habis waktu.",
                listOf("Buka sebuah aset dengan foto.", "Ketuk “Analisis AI (Gemini)”. Tunggu beberapa detik bila server sibuk."), null),
        )),
        RilisInfo(4, "1.3.0", listOf(
            FiturBaru("🎨", "Tampilan baru", "Warna navy dan emas, kartu lebih bulat dengan bayangan lembut, dan tombol utama berwarna emas. Data dan fitur lama tidak berubah.",
                listOf("Buka Beranda.", "Lihat kartu “Dinas aktif” dan deretan aksi cepat bulat."), null),
            FiturBaru("🔍", "Cari dinas dan aksi cepat", "Beranda kini bertanya “Mau dinas ke mana?” dengan kolom cari untuk menyaring daftar dinas, serta pintasan ke Aset, Uang Muka, Jadwal, Galeri, Dokumen, Peta, dan Audit.",
                listOf("Ketik sebagian nama dinas di kolom cari.", "Ketuk lingkaran aksi cepat untuk langsung pindah menu."), null),
        )),
        RilisInfo(3, "1.2.0", listOf(
            FiturBaru("✨", "Catatan perubahan dan panduan fitur", "Setelah aplikasi diperbarui, daftar yang berubah muncul otomatis beserta panduan singkat. Bisa dibuka lagi kapan saja.",
                listOf("Buka Profil lalu Pengaturan.", "Ketuk “Apa yang baru & panduan” atau “Riwayat perubahan semua versi”."), Screen.Settings),
            FiturBaru("🧾", "Laporan realisasi format LPBPD", "Satu ketukan menghasilkan Excel siap cetak untuk Finance: kolom Akomodasi, Transportasi, Konsumsi, Uang Saku, Lain-lain, rumus otomatis, bon sementara, selisih dana, dan kotak tanda tangan.",
                listOf("Buka menu Uang Muka dan pilih pengajuan.", "Ketuk Ekspor di kanan atas.", "Pilih “Laporan realisasi (LPBPD)”."), Screen.UangMuka),
            FiturBaru("🏷️", "Kategori bon baru dan buatan sendiri", "Ada Tiket Pesawat, Speedboat / Kapal, dan Uang Saku. Butuh jenis lain? Tambahkan sendiri, misalnya “Kapal selam”, lalu pilih kolom laporannya.",
                listOf("Buka sebuah bon.", "Di bagian TAG ketuk “+ Kategori baru”.", "Isi nama dan pilih kolom laporan."), Screen.UangMuka),
            FiturBaru("💵", "Pencairan tambahan (bon sementara ke-2 dst)", "Dana yang cair lebih dari sekali kini dicatat semua, jadi sisa/kurang dana dihitung benar.",
                listOf("Buka pengajuan lalu ketuk Ubah.", "Isi “Pencairan tambahan”, satu baris: tanggal lalu jumlah (mis. 2026-10-07 2290000)."), Screen.UangMuka),
            FiturBaru("🖼️", "Logo, nama PT, dan tanda tangan laporan", "Logo dan identitas pada kepala laporan bisa diganti sendiri.",
                listOf("Buka Profil lalu Pengaturan.", "Cari bagian “Laporan realisasi (LPBPD)”.", "Ganti logo dan isi nama, kota, penyiap, pemeriksa."), Screen.Settings),
        )),
        RilisInfo(2, "1.1.0", listOf(
            FiturBaru("🔐", "Lisensi online dan pemberitahuan update", "Pencabutan lisensi kini berlaku di aplikasi ini. Bila ada versi baru, muncul pemberitahuan lengkap dengan apa yang berubah.",
                listOf("Tanpa internet aplikasi tetap bisa dipakai.", "Staff HO perlu online sesekali (maks. 30 hari) untuk verifikasi."), null),
        )),
    )

    var tampil by mutableStateOf(false)
    var panduan by mutableStateOf(false)
    var semua by mutableStateOf(false)
    var indeks by mutableStateOf(0)

    fun rilisBelumDilihat(dilihat: Int, sekarang: Int) = daftar.filter { it.kode > dilihat && it.kode <= sekarang }
    fun fiturPanduan(dilihat: Int, sekarang: Int): List<FiturBaru> = (if (semua) daftar else rilisBelumDilihat(dilihat, sekarang).ifEmpty { daftar.take(1) }).flatMap { it.fitur }
}

@Composable
fun ApaBaruDialog(vm: MainViewModel, versiKode: Int, versiNama: String) {
    if (!ApaBaru.tampil && !ApaBaru.panduan) return
    val dilihat = remember { vm.settings.versiDilihat }
    val rilis = remember(ApaBaru.semua) { if (ApaBaru.semua) ApaBaru.daftar else ApaBaru.rilisBelumDilihat(dilihat, versiKode).ifEmpty { ApaBaru.daftar.take(1) } }
    fun tutup() { vm.settings.versiDilihat = versiKode; ApaBaru.tampil = false; ApaBaru.panduan = false; ApaBaru.semua = false; ApaBaru.indeks = 0 }

    if (ApaBaru.panduan) {
        val fitur = remember(ApaBaru.semua) { ApaBaru.fiturPanduan(dilihat, versiKode) }
        val i = ApaBaru.indeks.coerceIn(0, fitur.lastIndex)
        val f = fitur[i]
        AlertDialog(onDismissRequest = { tutup() },
            title = { Text("${f.ikon} ${f.judul}") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Panduan ${i + 1} dari ${fitur.size}", color = C.Muted, style = MaterialTheme.typography.labelSmall)
                    Text(f.isi, style = MaterialTheme.typography.bodyMedium)
                    f.langkah.forEachIndexed { n, l -> Text("${n + 1}. $l", style = MaterialTheme.typography.bodyMedium, color = C.Muted) }
                }
            },
            confirmButton = {
                Row {
                    if (f.tujuan != null) TextButton(onClick = { val t = f.tujuan; tutup(); vm.push(t) }) { Text("Coba sekarang") }
                    if (i < fitur.lastIndex) TextButton(onClick = { ApaBaru.indeks = i + 1 }) { Text("Berikutnya") } else TextButton(onClick = { tutup() }) { Text("Selesai") }
                }
            },
            dismissButton = { Row { if (i > 0) TextButton(onClick = { ApaBaru.indeks = i - 1 }) { Text("Sebelumnya") }; TextButton(onClick = { tutup() }) { Text("Tutup") } } })
        return
    }
    AlertDialog(onDismissRequest = { tutup() },
        title = { Text("Yang baru di versi $versiNama") },
        text = {
            Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                rilis.forEach { r -> r.fitur.forEach { f ->
                    Column { Text("${f.ikon} ${f.judul}", fontWeight = FontWeight.Bold, fontSize = 15.sp); Text(f.isi, color = C.Muted, style = MaterialTheme.typography.bodyMedium) }
                } }
            }
        },
        confirmButton = { TextButton(onClick = { ApaBaru.tampil = false; ApaBaru.panduan = true; ApaBaru.indeks = 0 }) { Text("Lihat panduan") } },
        dismissButton = { TextButton(onClick = { tutup() }) { Text("Tutup") } })
}
