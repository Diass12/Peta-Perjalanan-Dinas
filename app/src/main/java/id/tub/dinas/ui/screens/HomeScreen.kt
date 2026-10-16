package id.tub.dinas.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import id.tub.dinas.MainViewModel
import id.tub.dinas.domain.DinasModel
import id.tub.dinas.ui.*
import id.tub.dinas.ui.components.*
import id.tub.dinas.ui.theme.C
import id.tub.dinas.ui.theme.ThemeState

@Composable
fun HomeScreen(vm: MainViewModel) {
    val list by vm.dinasList.collectAsState()
    var creating by remember { mutableStateOf(false) }
    val reg by vm.aset.register.collectAsState()
    val um by vm.um.bundles.collectAsState()
    val jd by vm.jadwal.daftar.collectAsState()
    val hariIni = id.tub.dinas.um.todayIso()
    val depan = jd.filter { !it.selesai && it.tanggal >= hariIni }
    val next = depan.firstOrNull()
    val ditemukan = reg.count { it.state == id.tub.dinas.domain.AuditState.FOUND }
    val belumSelesai = um.count { it.um.status != id.tub.dinas.domain.UmStatus.SELESAI }

    Column(Modifier.fillMaxSize().background(C.Bg)) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Halo, ${ThemeState.playerName}", style = MaterialTheme.typography.titleLarge)
                Text(java.text.SimpleDateFormat("EEEE, d MMMM yyyy", java.util.Locale("id", "ID")).format(java.util.Date()), color = C.Muted, style = MaterialTheme.typography.bodyMedium)
            }
            RoundIcon("⚙️") { vm.push(Screen.Settings) }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MenuTile("📦", Color(0xFF2563EB), "Aset & Scan", if (reg.isEmpty()) "Register, scan, audit" else "${reg.size} aset · $ditemukan ditemukan", Modifier.weight(1f).fillMaxHeight()) { vm.push(Screen.Aset) }
                        MenuTile("💵", Color(0xFF15803D), "Uang Muka", if (um.isEmpty()) "Pengajuan & bon" else "${um.size} pengajuan · $belumSelesai berjalan", Modifier.weight(1f).fillMaxHeight()) { vm.push(Screen.UangMuka) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MenuTile("🗓️", Color(0xFF7C3AED), "Jadwal", if (jd.isEmpty()) "Audit, meeting, tenggat" else "${depan.size} akan datang", Modifier.weight(1f).fillMaxHeight()) { vm.push(Screen.Jadwal) }
                        MenuTile("➕", Color(0xFFD97706), "Dinas baru", "Titik & temuan lapangan", Modifier.weight(1f).fillMaxHeight()) { creating = true }
                    }
                }
            }
            if (next != null) item {
                Panel(onClick = { vm.push(Screen.Jadwal) }) {
                    Text("Jadwal terdekat", style = MaterialTheme.typography.labelSmall, color = C.Muted)
                    Spacer(Modifier.height(4.dp))
                    Text(next.judul, style = MaterialTheme.typography.titleMedium)
                    Text(listOf(id.tub.dinas.um.fmtTgl(next.tanggal), next.jamMulai, next.lokasi).filter { it.isNotBlank() }.joinToString(" · "), color = C.Muted, style = MaterialTheme.typography.bodyMedium)
                }
            }
            item { SectionTitle("Dinas") }
            if (list.isEmpty()) item { Empty("Belum ada dinas. Ketuk “Dinas baru” untuk mulai mencatat titik dokumentasi dan temuan.") }
            items(list, key = { it.id }) { d ->
                val counts by remember(d.id) { vm.repo.observeCounts(d.id) }.collectAsState(0 to 0)
                Panel(onClick = { vm.tab = DetailTab.PETA; vm.push(Screen.Detail(d.id)) }) {
                    Text(d.name, style = MaterialTheme.typography.titleMedium)
                    val period = listOf(fmtDate(d.startMs), fmtDate(d.endMs)).filter { it.isNotEmpty() }.joinToString(" – ")
                    if (period.isNotEmpty()) Text(period, color = C.Muted, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Chip("📍 ${counts.first} titik", false)
                        Chip("📝 ${counts.second} temuan", false)
                    }
                }
            }
        }
    }
    if (creating) DinasDialog(null, onDismiss = { creating = false }) { name, s, e, note ->
        vm.work { val id = vm.repo.createDinas(name, s, e, note); vm.tab = DetailTab.PETA; vm.push(Screen.Detail(id)) }
    }
}

@Composable
fun DinasDialog(existing: DinasModel?, onDismiss: () -> Unit, onSave: (String, Long?, Long?, String) -> Unit) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var s by remember { mutableStateOf(msToIso(existing?.startMs)) }
    var e by remember { mutableStateOf(msToIso(existing?.endMs)) }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    val sOk = true
    val eOk = e.isBlank() || s.isBlank() || e >= s
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Dinas baru" else "Ubah dinas") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(name, { name = it }, "Nama dinas", singleLine = true)
                DateField("Mulai", s, { s = it })
                DateField("Selesai", e, { e = it })
                if (!eOk) Text("Tanggal selesai tidak boleh sebelum tanggal mulai", color = C.Danger)
                Field(note, { note = it }, "Catatan", minLines = 2)
            }
        },
        confirmButton = { TextButton(enabled = sOk && eOk && name.isNotBlank(), onClick = { onSave(name, isoToMs(s), isoToMs(e), note); onDismiss() }) { Text("Simpan") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}
