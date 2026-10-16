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

@Composable
fun HomeScreen(vm: MainViewModel) {
    val list by vm.dinasList.collectAsState()
    var dialog by remember { mutableStateOf<DinasModel?>(null) }
    var creating by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(C.Bg)) {
        GameHeader(rememberPlayer(vm)) { Pic(id.tub.dinas.R.drawable.ic_setting, 30.dp, Modifier.clickable { vm.push(Screen.Settings) }) }
        Box(Modifier.weight(1f)) {
                        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    val reg by vm.aset.register.collectAsState()
                    val ditemukan = reg.count { it.state == id.tub.dinas.domain.AuditState.FOUND }
                    Panel(onClick = { vm.push(Screen.Aset) }) {
                        Text("Aset & Scan", style = MaterialTheme.typography.titleMedium)
                        Text(if (reg.isEmpty()) "Register aset, scan kode TUB, audit fisik, ekspor Memo FAT" else "${reg.size} aset · $ditemukan ditemukan dalam audit", color = C.Muted, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                item {
                    Panel(onClick = { creating = true }) {
                        Text("+ Dinas baru", style = MaterialTheme.typography.titleMedium)
                        Text("Catat titik dokumentasi dan temuan laporan perjalanan dinas", color = C.Muted, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                item {
                    val um by vm.um.bundles.collectAsState()
                    Panel(onClick = { vm.push(Screen.UangMuka) }) {
                        Text("Uang Muka & Realisasi", style = MaterialTheme.typography.titleMedium)
                        val belum = um.count { it.um.status != id.tub.dinas.domain.UmStatus.SELESAI }
                        Text(if (um.isEmpty()) "Catat pengajuan uang muka dan foto bon realisasi" else "${um.size} pengajuan · $belum belum selesai direalisasi", color = C.Muted, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                item {
                    val jd by vm.jadwal.daftar.collectAsState()
                    val hariIni = id.tub.dinas.um.todayIso()
                    val depan = jd.filter { !it.selesai && it.tanggal >= hariIni }
                    Panel(onClick = { vm.push(Screen.Jadwal) }) {
                        Text("Jadwal", style = MaterialTheme.typography.titleMedium)
                        val next = depan.firstOrNull()
                        Text(if (jd.isEmpty()) "Rekap jadwal audit, meeting, tenggat, dan agenda lainnya" else if (next == null) "${jd.size} agenda · tidak ada yang akan datang" else "${depan.size} akan datang · berikutnya: ${next.judul} (${id.tub.dinas.um.fmtTgl(next.tanggal)})", color = C.Muted, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                items(list, key = { it.id }) { d ->
                    val counts by remember(d.id) { vm.repo.observeCounts(d.id) }.collectAsState(0 to 0)
                    Panel(onClick = { vm.tab = DetailTab.PETA; vm.push(Screen.Detail(d.id)) }) {
                        Text(d.name, style = MaterialTheme.typography.titleMedium)
                        val period = listOf(fmtDate(d.startMs), fmtDate(d.endMs)).filter { it.isNotEmpty() }.joinToString(" – ")
                        if (period.isNotEmpty()) Text(period, color = C.Muted, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(6.dp))
                        Text("${counts.first} titik dokumentasi · ${counts.second} temuan laporan", color = C.Muted, style = MaterialTheme.typography.bodyMedium)
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
    var s by remember { mutableStateOf(fmtInput(existing?.startMs)) }
    var e by remember { mutableStateOf(fmtInput(existing?.endMs)) }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    val sOk = s.isBlank() || parseInputDate(s) != null
    val eOk = e.isBlank() || parseInputDate(e) != null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Dinas baru" else "Ubah dinas") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(name, { name = it }, "Nama dinas", singleLine = true)
                Field(s, { s = it }, "Mulai (dd/mm/yyyy)", singleLine = true)
                if (!sOk) Text("Format tanggal belum benar", color = C.Danger)
                Field(e, { e = it }, "Selesai (dd/mm/yyyy)", singleLine = true)
                if (!eOk) Text("Format tanggal belum benar", color = C.Danger)
                Field(note, { note = it }, "Catatan", minLines = 2)
            }
        },
        confirmButton = { TextButton(enabled = sOk && eOk && name.isNotBlank(), onClick = { onSave(name, parseInputDate(s), parseInputDate(e), note); onDismiss() }) { Text("Simpan") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}
