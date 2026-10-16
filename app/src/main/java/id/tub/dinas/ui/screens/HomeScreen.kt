package id.tub.dinas.ui.screens

import androidx.compose.foundation.background
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
        Header("Dokumentasi Dinas", actions = { TextButton(onClick = { vm.push(Screen.Settings) }) { Pic(id.tub.dinas.R.drawable.ic_setting, 34.dp) } })
        Box(Modifier.weight(1f)) {
            if (list.isEmpty()) Empty("Belum ada dinas. Ketuk “+ Dinas baru” untuk mulai mencatat titik dokumentasi dan temuan laporan.")
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                  Box {
                    androidx.compose.foundation.Image(
                        androidx.compose.ui.res.painterResource(id.tub.dinas.R.drawable.peta_indonesia), null,
                        Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(20.dp)).border(1.dp, Color(0x886FD8FF), RoundedCornerShape(20.dp)),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop, alignment = androidx.compose.ui.Alignment.CenterEnd)
                  }
                }
                item {
                    val xp by remember { vm.repo.observeXp() }.collectAsState(Triple(0, 0, 0))
                    val pts = xp.first * 10 + xp.second * 20 + xp.third * 50
                    val lv = Math.floor(Math.sqrt(pts / 25.0)).toInt() + 1
                    val from = 25 * (lv - 1) * (lv - 1); val to = 25 * lv * lv
                    val title = when { lv >= 20 -> "Veteran Lapangan"; lv >= 10 -> "Auditor Lapangan"; lv >= 5 -> "Surveyor"; else -> "Pemula Lapangan" }
                    Panel {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Avatar(id.tub.dinas.ui.theme.ThemeState.avatar, 56.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(id.tub.dinas.ui.theme.ThemeState.playerName, style = MaterialTheme.typography.titleMedium)
                                Text("Lv. $lv  $title", color = androidx.compose.ui.graphics.Color(0xFFB88A00), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                Spacer(Modifier.height(4.dp))
                                XpBar((pts - from).toFloat() / (to - from))
                                Text("$pts / $to XP", color = C.Muted, style = MaterialTheme.typography.labelSmall)
                            }
                            if (xp.third > 0) { Spacer(Modifier.width(8.dp)); Pic(id.tub.dinas.R.drawable.ic_lencana, 40.dp) }
                        }
                    }
                }
                item {
                    val reg by vm.aset.register.collectAsState()
                    val ditemukan = reg.count { it.state == id.tub.dinas.domain.AuditState.FOUND }
                    Panel(onClick = { vm.push(Screen.Aset) }) {
                        Text("Aset & Scan", style = MaterialTheme.typography.titleMedium)
                        Text(if (reg.isEmpty()) "Register aset, scan kode TUB, audit fisik, ekspor Memo FAT" else "${reg.size} aset · $ditemukan ditemukan dalam audit", color = C.Muted, style = MaterialTheme.typography.bodyMedium)
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
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
            AmberButton("+ Dinas baru", Modifier.fillMaxWidth()) { creating = true }
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
