package id.tub.dinas.ui.screens

import id.tub.dinas.ui.components.Text
import android.net.Uri
import kotlinx.coroutines.launch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asImageBitmap
import id.tub.dinas.MainViewModel
import id.tub.dinas.data.PhotoPreset
import id.tub.dinas.security.AppLock
import id.tub.dinas.ui.components.*
import id.tub.dinas.ui.theme.C
import id.tub.dinas.ui.theme.ThemeState

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val s = vm.settings
    var lock by remember { mutableStateOf(s.lockEnabled) }
    var timeout by remember { mutableIntStateOf(s.lockTimeoutSec) }
    var secure by remember { mutableStateOf(s.secureAlways) }
    var preset by remember { mutableStateOf(s.photoPreset) }
    var sat by remember { mutableStateOf(s.satellite) }
    var gpsHigh by remember { mutableStateOf(s.gpsHigh) }
    var extra by remember { mutableStateOf(s.newsExtra) }
    var pendingPass by remember { mutableStateOf("") }
    var askBackupPass by remember { mutableStateOf(false) }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }

    val createBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) vm.exportBackup(uri, pendingPass)
        pendingPass = ""
    }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) restoreUri = uri }

    Column(Modifier.fillMaxSize().background(C.Bg)) {
        Header("Pengaturan", onBack = { vm.pop() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Panel {
                Text("Profil", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                var nm by remember { mutableStateOf(ThemeState.playerName) }
                Field(nm, { nm = it.take(24); ThemeState.playerName = nm; s.playerName = nm }, "Nama", singleLine = true)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    var snd by remember { mutableStateOf(s.sfx) }
                    Text("Efek suara", Modifier.weight(1f))
                    Switch(snd, { snd = it; s.sfx = it; Sfx.enabled = it; if (it) Sfx.pick() })
                }
            }
            Panel {
                Text("Keamanan", style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Kunci aplikasi (sidik jari / PIN)", Modifier.weight(1f))
                    Switch(lock, { v ->
                        if (v && !AppLock.available(ctx)) vm.toast("Atur kunci layar perangkat dulu (PIN/pola/sidik jari)")
                        else { lock = v; s.lockEnabled = v }
                    })
                }
                if (lock) {
                    Label("Kunci otomatis setelah di latar belakang")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(0 to "Segera", 30 to "30 dtk", 300 to "5 mnt", 900 to "15 mnt").forEach { (sec, label) ->
                            Chip(label, timeout == sec) { timeout = sec; s.lockTimeoutSec = sec; vm.lockTimeoutSec = sec }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Blokir tangkapan layar selalu")
                        Text("Tampilan “aplikasi terbaru” selalu disamarkan saat kunci aktif.", color = C.Muted, style = MaterialTheme.typography.bodyMedium)
                    }
                    Switch(secure, { secure = it; s.secureAlways = it })
                }
                Text("Foto, PDF, dan database dienkripsi di perangkat. Kunci disimpan di Android Keystore.", color = C.Muted, style = MaterialTheme.typography.bodyMedium)
            }
            Panel {
                Text("Foto", style = MaterialTheme.typography.titleMedium)
                Label("Kualitas simpan")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PhotoPreset.values().forEach { p -> Chip("${p.label} (${p.maxSide}px)", preset == p) { preset = p; s.photoPreset = p } }
                }
                Text("Foto dikecilkan saat disimpan; lokasi asli (EXIF) tidak ikut tersimpan.", color = C.Muted, style = MaterialTheme.typography.bodyMedium)
            }
            Panel {
                Text("Peta", style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Citra satelit (Esri World Imagery)", Modifier.weight(1f))
                    Switch(sat, { sat = it; s.satellite = it })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Akurasi GPS tinggi")
                        Text("Tinggi: GPS penuh tiap detik (area terpencil, lebih boros baterai). Normal: hemat baterai.", color = C.Muted, style = MaterialTheme.typography.bodyMedium)
                    }
                    Switch(gpsHigh, { gpsHigh = it; s.gpsHigh = it })
                }
                Text("Peta butuh internet untuk memuat petak; area yang sudah dilihat tersimpan sementara.", color = C.Muted, style = MaterialTheme.typography.bodyMedium)
            }
            Panel {
                Text("Wawasan & Berita", style = MaterialTheme.typography.titleMedium)
                val ex = id.tub.dinas.news.NewsFeed.parseExtra(extra)
                if (ex.isEmpty()) Text("Belum ada sumber RSS tambahan. Tambahkan dari tab Wawasan.", color = C.Muted, style = MaterialTheme.typography.bodyMedium)
                ex.forEach { x ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${x.nama}\n${x.url}", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { extra = extra.lines().filterNot { it.endsWith("|" + x.url) }.joinToString("\n"); s.newsExtra = extra }) { Text("Hapus", color = C.Danger) }
                    }
                }
            }
            Panel {
                Text("Tampilan", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Chip("☀️ Terang", !id.tub.dinas.ui.theme.ThemeState.dark) { id.tub.dinas.ui.theme.ThemeState.dark = false; s.dark = false }
                    Chip("🌙 Gelap", id.tub.dinas.ui.theme.ThemeState.dark) { id.tub.dinas.ui.theme.ThemeState.dark = true; s.dark = true }
                }
                Text("Mode gelap: hitam kebiruan dengan gradasi ungu–jingga seperti Aset Scan.", color = C.Muted, style = MaterialTheme.typography.bodyMedium)
            }
            Panel {
                Text("Bahasa aplikasi", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    id.tub.dinas.i18n.AppLang.entries.forEach { l ->
                        Chip(l.nama, id.tub.dinas.i18n.Tr.lang == l) { id.tub.dinas.i18n.Tr.lang = l; s.lang = l.code }
                    }
                }
                Text("Menu, tombol, dan petunjuk diterjemahkan. Data yang Anda ketik (nama dinas, judul, catatan) dan format tanggal tetap seperti aslinya. Sinkronisasi cloud belum tersedia; gunakan Cadangan di bawah untuk memindahkan data antar-perangkat.", color = C.Muted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
            }
            Panel {
                var key by remember { mutableStateOf(s.geminiKey) }
                var model by remember { mutableStateOf(s.geminiModel) }
                var daftar by remember { mutableStateOf<List<String>>(emptyList()) }
                var cek by remember { mutableStateOf(false) }
                val scopeAi = androidx.compose.runtime.rememberCoroutineScope()
                Text("Analisis AI (Gemini, opsional)", style = MaterialTheme.typography.titleMedium)
                Text("Untuk mengenali barang dan membaca stiker kode aset dari foto. Isi kunci gratis dari aistudio.google.com/apikey. Kunci hanya disimpan di perangkat ini. Saat dipakai, foto aset dikirim ke Google; tanpa kunci, tidak ada data yang dikirim.", color = C.Muted, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                Field(key, { key = it.trim() }, "Kunci API Gemini (AIza…)", singleLine = true)
                Spacer(Modifier.height(6.dp))
                Field(model, { model = it.trim() }, "Model", singleLine = true)
                if (daftar.isNotEmpty()) @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { daftar.forEach { d -> Chip(d, model == d) { model = d } } }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SoftButton(if (cek) "Memeriksa…" else "Cek model untuk kunci saya", Modifier.weight(1f)) {
                        if (key.isBlank()) vm.toast("Isi kunci dulu")
                        else if (!cek) { cek = true; scopeAi.launch {
                            try { daftar = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { id.tub.dinas.ai.Gemini.modelTersedia(key) }; vm.toast("${daftar.size} model tersedia") }
                            catch (e: Exception) { vm.toast(e.message ?: "Gagal memeriksa") } finally { cek = false }
                        } }
                    }
                    PrimaryButton("Simpan", Modifier.weight(1f)) { s.geminiKey = key; s.geminiModel = model; vm.toast("Pengaturan Gemini disimpan") }
                }
            }
            Panel {
                var riwayat by remember { mutableStateOf(false) }
                val idPerangkat = remember { id.tub.dinas.security.Lisensi.idPerangkat(ctx) }
                var kodeLis by remember { mutableStateOf("") }
                Text("Info aplikasi", style = MaterialTheme.typography.titleMedium)
                val ho = id.tub.dinas.security.Lisensi.ho
                Text(if (ho) "Lisensi: Staff HO (lengkap) · ${id.tub.dinas.security.Lisensi.keterangan}" else "Lisensi: Staff Site · NBV disembunyikan", color = if (ho) C.Ok else C.Muted, style = MaterialTheme.typography.bodyMedium)
                Text("ID perangkat: $idPerangkat", color = C.Muted, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(6.dp))
                if (!ho) {
                    Field(kodeLis, { kodeLis = it }, "Kode lisensi Staff HO", singleLine = true)
                    Spacer(Modifier.height(6.dp))
                    SoftButton("Aktifkan Staff HO", Modifier.fillMaxWidth()) {
                        val r = id.tub.dinas.security.LisensiKode.periksa(kodeLis, idPerangkat, java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date()).toInt())
                        if (r.ok && r.peran == "HO") { s.licenseCode = kodeLis; id.tub.dinas.security.Lisensi.muat(ctx, kodeLis); kodeLis = ""; vm.toast("Staff HO aktif") }
                        else vm.toast(if (r.ok) "Kode ini bukan untuk Staff HO" else r.pesan)
                    }
                } else SoftButton("Kembali ke Staff Site", Modifier.fillMaxWidth()) { s.licenseCode = ""; id.tub.dinas.security.Lisensi.muat(ctx, ""); vm.toast("Kembali ke Staff Site") }
                Spacer(Modifier.height(6.dp))
                SoftButton("📜 Riwayat versi", Modifier.fillMaxWidth()) { riwayat = true }
                if (riwayat) AlertDialog(onDismissRequest = { riwayat = false }, title = { Text("Riwayat versi") },
                    text = { Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        RIWAYAT.forEach { (v, l) -> Column { Text(v, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold); l.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) } } }
                    } },
                    confirmButton = { TextButton(onClick = { riwayat = false }) { Text("Tutup") } })
            }
            Panel {
                var lp by remember { mutableStateOf(s.lapPerusahaan) }; var lk by remember { mutableStateOf(s.lapKota) }
                var ln by remember { mutableStateOf(s.lapPenyiap) }; var lj by remember { mutableStateOf(s.lapJabatan) }; var lc by remember { mutableStateOf(s.lapPemeriksa) }
                Text("Laporan realisasi (LPBPD)", style = MaterialTheme.typography.titleMedium)
                Text("Kepala dan tanda tangan pada laporan biaya perjalanan dinas hasil ekspor.", color = C.Muted, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(6.dp))
                var logoVer by remember { mutableStateOf(0) }
                val logoFile = remember { java.io.File(ctx.filesDir, "logo_laporan.png") }
                val pilihLogo = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
                    if (uri != null) runCatching {
                        val bmp = ctx.contentResolver.openInputStream(uri)!!.use { android.graphics.BitmapFactory.decodeStream(it) }!!
                        val sc = minOf(1f, 500f / maxOf(bmp.width, bmp.height))
                        val kecil = if (sc < 1f) android.graphics.Bitmap.createScaledBitmap(bmp, (bmp.width * sc).toInt().coerceAtLeast(1), (bmp.height * sc).toInt().coerceAtLeast(1), true) else bmp
                        logoFile.outputStream().use { kecil.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                        logoVer++; vm.toast("Logo laporan disimpan")
                    }.onFailure { vm.toast("Gambar tidak dapat dibaca") }
                }
                val logoBmp = remember(logoVer) { runCatching { android.graphics.BitmapFactory.decodeStream(if (logoFile.exists()) logoFile.inputStream() else ctx.assets.open("logo_tub.png")) }.getOrNull() }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (logoBmp != null) androidx.compose.foundation.Image(logoBmp.asImageBitmap(), "Logo laporan", Modifier.size(72.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SoftButton("Ganti logo") { pilihLogo.launch("image/*") }
                        if (logoFile.exists()) SoftButton("Pakai logo bawaan") { logoFile.delete(); logoVer++; vm.toast("Logo bawaan dipakai") }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Field(lp, { lp = it; s.lapPerusahaan = it }, "Nama perusahaan", singleLine = true)
                Spacer(Modifier.height(6.dp)); Field(lk, { lk = it; s.lapKota = it }, "Kota (untuk tanggal cetak)", singleLine = true)
                Spacer(Modifier.height(6.dp)); Field(ln, { ln = it; s.lapPenyiap = it }, "Dipersiapkan oleh (nama)", singleLine = true)
                Spacer(Modifier.height(6.dp)); Field(lj, { lj = it; s.lapJabatan = it }, "Jabatan penyiap (mis. SPV. Accounting)", singleLine = true)
                Spacer(Modifier.height(6.dp)); Field(lc, { lc = it; s.lapPemeriksa = it }, "Diperiksa oleh (mis. Finance)", singleLine = true)
            }
            Panel {
                Text("Cadangan", style = MaterialTheme.typography.titleMedium)
                Text("Cadangan memuat semua dinas beserta foto dan PDF. Beri kata sandi supaya berkas aman bila berpindah tangan.", color = C.Muted, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton("Buat cadangan") { askBackupPass = true }
                    SoftButton("Pulihkan") { openBackup.launch(arrayOf("*/*")) }
                }
            }
            Text("Versi 1.1.0 · tanpa analitik dan iklan. Data dinas tidak dikirim ke server; hanya kode lisensi dan ID perangkat diperiksa online.", color = C.Muted, style = MaterialTheme.typography.bodyMedium)
        }
    }

    if (askBackupPass) PasswordDialog("Kata sandi cadangan", "Kosongkan untuk cadangan tanpa kata sandi (tidak disarankan).", confirmLabel = "Lanjut",
        onDismiss = { askBackupPass = false }) { p ->
        pendingPass = p
        createBackup.launch("Cadangan-Dinas-${java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.US).format(java.util.Date())}.dnsbak")
    }
    restoreUri?.let { u ->
        PasswordDialog("Pulihkan cadangan", "Isi kata sandi bila cadangan dilindungi. Dinas dengan ID sama akan ditimpa.", confirmLabel = "Pulihkan",
            onDismiss = { restoreUri = null }) { p -> vm.restoreBackup(u, p) }
    }
}

@Composable
fun PasswordDialog(title: String, hint: String, confirmLabel: String, onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var p by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(hint, color = C.Muted)
                OutlinedTextField(p, { p = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Kata sandi") }, visualTransformation = PasswordVisualTransformation())
            }
        },
        confirmButton = { TextButton(onClick = { onOk(p); onDismiss() }) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

private val RIWAYAT = listOf(
    "Terbaru" to listOf(
        "1.1.0: cek lisensi online (pencabutan berlaku), pengingat dan kunci versi lama",
        "Laporan realisasi format LPBPD (ekspor Excel), kategori Tiket/Speedboat/Uang Saku, pencairan tambahan",
        "Analisis AI (Gemini) dan Baca kode dari foto di editor aset",
        "Import Excel/JSON mendeteksi semua perubahan per kolom (mode Perbarui)",
        "Ekspor Excel: pilih sheet, kolom, dan mutu foto",
        "Hiburan: repositori komik/novel dan pemutar video yang diisi sendiri; novel Inggris (Gutenberg)",
        "Perbaikan tautan BMKG; kunci tanda tangan tetap agar APK bisa diperbarui",
    ),
    "Redesign Enterprise Minimalist" to listOf(
        "Menu bawah: Beranda, Peta & GPS, Audit & Dokumen, Wawasan & Berita",
        "Profil, Dokumen & Tiket, Galeri Lapangan, pilihan bahasa (5 bahasa)",
        "Perbaikan: kamera audit tanpa dinas, temuan pending ganda, perbesar foto aset",
    ),
    "1.0.0" to listOf("Dinas, peta GPS, temuan, uang muka, jadwal, Aset Scan, ekspor, cadangan terenkripsi"),
)
