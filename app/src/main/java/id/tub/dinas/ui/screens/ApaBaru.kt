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
        RilisInfo(22, "1.7.12", listOf(
            FiturBaru("🧾", "Bon di web & APK kini sama", "Isian bon di versi web/PC kini bergaya kartu seperti di APK (kategori berupa pilihan, terbilang otomatis). Label foto dan berkas di form Aset dan Bon juga dirapikan serta diterjemahkan.",
                listOf("Buka Uang Muka, lalu Tambah bon."), null),
        )),
        RilisInfo(21, "1.7.11", listOf(
            FiturBaru("✨", "Baca bon dengan Gemini", "Tombol Baca otomatis di foto bon kini memakai Gemini bila kunci Gemini sudah diisi di Pengaturan, sehingga tulisan tangan dan struk sulit terbaca lebih akurat. Selain jumlah dan tanggal, Gemini mengusulkan uraian dan kategori. Foto dikirim ke Google hanya saat tombol dipakai; tanpa kunci, pembacaan tetap di perangkat. Hasil selalu Anda periksa dulu.",
                listOf("Isi kunci Gemini di Pengaturan (sekali saja).", "Buka bon, lampirkan foto, ketuk Baca otomatis (Gemini), lalu Pakai hasil."), null),
        )),
        RilisInfo(20, "1.7.10", listOf(
            FiturBaru("🧭", "Tombol aksi Aset berlabel", "Tombol di atas daftar Aset kini memakai ikon beserta nama (Pilih, Galeri, Ekspor, Label QR, Tambah) sehingga fungsinya jelas tanpa ditebak.",
                listOf("Buka Aset; tombol ada di bawah kolom cari."), null),
        )),
        RilisInfo(19, "1.7.9", listOf(
            FiturBaru("🌐", "Terjemahan lebih lengkap", "Sekitar 150 teks yang sebelumnya tetap berbahasa Indonesia saat bahasa aplikasi diganti kini ikut diterjemahkan (Inggris, Mandarin, Jepang, Korea): form aset, bon, Insights, Panduan, Studio Foto, Pengaturan, dan kondisi aset (Baik, Cukup, Rusak) beserta riwayat aset.",
                listOf("Ganti bahasa di Pengaturan, lalu periksa form Aset dan Bon."), null),
        )),
        RilisInfo(18, "1.7.8", listOf(
            FiturBaru("📦", "Form aset lebih rapi", "Isian aset kini dikelompokkan menjadi Media & Lampiran (foto, baca kode, AI, video, dokumen), Informasi Utama, Kepemilikan & Distribusi, serta Status & Kondisi. Chip kondisi dan status berwarna (hijau, oranye, merah), tombol Simpan dan Buat temuan melayang di bawah, dan Unduh kartu / Hapus aset dipindah ke menu ⋯ di kanan atas agar tidak terpencet. Data aset tidak berubah.",
                listOf("Buka Aset, lalu ketuk salah satu aset.", "Ketuk ⋯ di kanan atas untuk Unduh kartu aset atau Hapus aset."), null),
        )),
        RilisInfo(17, "1.7.7", listOf(
            FiturBaru("🧾", "Layar bon baru, terbilang, dan baca otomatis (OCR)", "Isian bon kini bergaya baru: kategori, tanggal, jumlah dengan terbilang otomatis (mis. “Satu Juta Empat Ratus Lima Puluh Ribu Rupiah”), uraian maksimal 200 karakter, dan foto bon. Di tiap foto ada tombol Baca otomatis (OCR) yang mengusulkan jumlah, tanggal, dan nama toko dari foto bon. Pembacaan berjalan di perangkat tanpa internet, dan hasilnya selalu Anda periksa dulu sebelum dipakai.",
                listOf("Buka Uang Muka, lalu ketuk bon atau tambah bon.", "Lampirkan foto bon, lalu ketuk Baca otomatis (OCR) di bawah foto.", "Pilih jumlah yang benar, lalu ketuk Pakai hasil."), null),
        )),
        RilisInfo(16, "1.7.6", listOf(
            FiturBaru("🧩", "Widget lebih ringkas", "Tampilan widget dipadatkan agar tombol Kunci GPS dan Kamera tidak lagi terpotong pada ukuran standar.",
                listOf("Bila widget lama masih terpotong, hapus lalu pasang ulang."), null),
        )),
        RilisInfo(15, "1.7.5", listOf(
            FiturBaru("🧩", "Widget layar utama", "Widget baru menampilkan dinas aktif, sisa anggaran, biaya yang sudah keluar, serta tombol cepat Kunci GPS dan Kamera. Bila kunci aplikasi aktif, nominal disembunyikan kecuali Anda menyalakan “Tampilkan nominal di widget” di Pengaturan.",
                listOf("Tekan lama layar utama HP, pilih Widget, lalu cari Dokumentasi Dinas.", "Ketuk Kunci GPS atau Kamera di widget untuk langsung membuka aplikasi di aksi itu."), null),
        )),
        RilisInfo(14, "1.7.4", listOf(
            FiturBaru("🛠️", "Perbaikan berita Insights", "Berita Detik kini muncul (alamat sumbernya diperbaiki) dan gambar berita Tempo kini tampil.",
                listOf("Buka Insights lalu ketuk Muat ulang."), null),
        )),
        RilisInfo(13, "1.7.3", listOf(
            FiturBaru("📰", "Insights berwarna dengan gambar & lebih banyak sumber", "Menu Insights kini bergaya baru: berita utama bergambar, kolom TERKINI, penyaring kategori (Nasional, Ekonomi, Teknologi, Internasional, Olahraga, Hiburan) dan penerbit, daftar berita dengan gambar kecil, serta Info penting BMKG. Sumber bertambah: CNN Indonesia, CNBC Indonesia, Antara, Tempo, Detik, dan BBC Indonesia. Sumber yang sedang tidak bisa dimuat dilewati otomatis.",
                listOf("Buka Insights di menu bawah.", "Ketuk kategori atau penerbit untuk menyaring; ketuk ＋ untuk menambah sumber RSS sendiri."), null),
        )),
        RilisInfo(12, "1.7.2", listOf(
            FiturBaru("📋", "Tampilan menu Audit baru", "Menu Audit & Dokumen kini memiliki ringkasan di atas (jumlah aset, discrepancy, biaya terpakai), kartu Temuan lapangan dengan kotak status berwarna, kartu Verifikasi aset dengan bar progres, Ringkasan biaya dengan persentase terpakai, dan Akses cepat berwarna ke Dokumen, Galeri, Jadwal, dan Ringkasan.",
                listOf("Buka menu Audit di bawah.", "Ketuk kotak status atau kartu mana pun untuk membuka daftarnya."), null),
        )),
        RilisInfo(11, "1.7.1", listOf(
            FiturBaru("📦", "Tampilan Register Aset baru", "Dasbor aset bergaya baru: header biru tua dengan tanggal, kartu progres audit dengan tombol Mulai Scan Audit, empat kartu ringkasan, bagian Per Kategori, Departemen, Status Barcode, PT, dan Kualitas Data yang lebih rapi, serta tombol Scan bulat di menu bawah. Data dan cara kerja aset tidak berubah.",
                listOf("Buka Aset dari Beranda.", "Ketuk kartu atau baris mana pun untuk memfilter daftar aset."), null),
        )),
        RilisInfo(10, "1.7.0", listOf(
            FiturBaru("🌊", "Tampilan Beranda baru", "Beranda bergaya aplikasi travel: header pemandangan, kolom cari dengan tombol panah, kartu dinas aktif biru, kartu anggaran, tombol Kunci GPS dan Kamera audit yang lebih jelas, ikon menu pastel, dan menu bawah dengan garis biru. Semua data dan cara kerja tetap sama.",
                listOf("Buka Beranda.", "Ketik di kolom cari untuk mencari dinas, titik, temuan, aset, atau jadwal."), null),
        )),
        RilisInfo(9, "1.6.1", listOf(
            FiturBaru("🧭", "Panduan Travel dengan tampilan baru", "Daftar wisata dan kuliner bawaan dihapus. Sekarang ketik nama kota atau tempat, lalu ketuk panah: aplikasi mengambil ringkasan dan foto dari Wikipedia, dan menyediakan tombol langsung ke Google Maps (wisata, kuliner, hotel), Traveloka, Booking.com, dan Wikivoyage. Destinasi bisa disimpan, dijadikan dinas baru, atau ditambahkan sebagai titik dinas.",
                listOf("Buka menu Panduan.", "Ketik kota, misalnya Purwakarta, lalu ketuk panah biru.", "Ketuk Cari di internet atau salah satu kartu di bawahnya."), null),
        )),
        RilisInfo(8, "1.6.0", listOf(
            FiturBaru("🎨", "Studio Foto", "Percantik foto dinas dengan filter (Cerah, Hangat, Sejuk, Hitam putih, Sepia, Vintage, Dramatis) dan bingkai (Putih, Hitam, Polaroid dengan tulisan, Emas). Hasilnya disimpan sebagai foto baru di titik atau temuan yang sama; foto asli tidak berubah.",
                listOf("Buka sebuah foto di titik atau temuan.", "Ketuk ikon 🎨 di pojok atas.", "Pilih filter dan bingkai, lalu Simpan sebagai foto baru."), null),
            FiturBaru("🧩", "Kolase foto", "Gabungkan 2 sampai 4 foto dari satu dinas menjadi satu gambar dengan beberapa tata letak dan warna latar.",
                listOf("Di Studio Foto ketuk tab Kolase.", "Pilih 2 sampai 4 foto, tata letak, dan warna latar.", "Simpan sebagai foto baru."), null),
        )),
        RilisInfo(7, "1.5.0", listOf(
            FiturBaru("📷", "Foto asli di Panduan", "Kartu destinasi, wisata, dan kuliner kini menampilkan foto asli dari Wikipedia/Wikimedia Commons lengkap dengan nama fotografer dan lisensinya. Foto disimpan di HP setelah dimuat. Bila sinyal lemah, kartu tetap tampil tanpa foto.",
                listOf("Buka menu Panduan dengan internet aktif.", "Tunggu foto muncul; ketuk keterangan foto untuk melihat sumbernya."), null),
            FiturBaru("🌏", "Data jauh lebih banyak", "Kini 14 daerah: ditambah Halmahera, Ternate, Batam, Aceh, serta Kalimantan Timur, Barat, Selatan, dan Tengah, dengan lebih banyak wisata dan kuliner (data sampel).",
                listOf("Buka Panduan lalu geser Destinasi populer.", "Atau ketik nama daerah di kolom cari."), null),
            FiturBaru("🌐", "Cari di internet", "Kalau kota yang diketik belum ada di data bawaan, ketuk Cari di internet untuk ringkasan dan foto dari Wikipedia, plus tombol wisata, kuliner, dan hotel di Maps.",
                listOf("Ketik nama kota yang belum ada, misalnya Padang.", "Ketuk Cari di internet."), null),
        )),
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
