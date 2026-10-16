package id.tub.dinas.ui.screens

import id.tub.dinas.ui.components.Text
import android.net.Uri
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
                Text("Cadangan", style = MaterialTheme.typography.titleMedium)
                Text("Cadangan memuat semua dinas beserta foto dan PDF. Beri kata sandi supaya berkas aman bila berpindah tangan.", color = C.Muted, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton("Buat cadangan") { askBackupPass = true }
                    SoftButton("Pulihkan") { openBackup.launch(arrayOf("*/*")) }
                }
            }
            Text("Versi 1.0.0 · tanpa analitik, tanpa iklan, data tidak dikirim ke server.", color = C.Muted, style = MaterialTheme.typography.bodyMedium)
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
