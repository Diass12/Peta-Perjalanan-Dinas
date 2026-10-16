package id.tub.dinas.ui.screens

import id.tub.dinas.ui.components.Text
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import id.tub.dinas.MainViewModel
import id.tub.dinas.domain.DinasSnapshot
import id.tub.dinas.domain.Status
import id.tub.dinas.domain.TemuanModel
import id.tub.dinas.ui.components.*
import id.tub.dinas.ui.theme.C
import androidx.compose.foundation.layout.Row as R

@Composable
fun TindakTab(vm: MainViewModel, s: DinasSnapshot) {
    val list = s.temuan.sortedBy { it.order }
    var poinDialog by remember { mutableStateOf<Pair<String?, String>?>(null) }
    var delPoin by remember { mutableStateOf<String?>(null) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        itemsIndexed(list, key = { _, t -> t.id }) { i, t ->
            Panel {
                Text("${i + 1}. ${t.title}", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                SaveField(t.id + "a", t.action, "Tindak lanjut", minLines = 2) { v -> save(vm, s, t.copy(action = v)) }
                Spacer(Modifier.height(6.dp))
                R(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SaveField(t.id + "p", t.pic, "Penanggung jawab", Modifier.weight(1f)) { v -> save(vm, s, t.copy(pic = v)) }
                    DateField("Target", id.tub.dinas.um.inputToIso(t.target)?.takeIf { it.isNotEmpty() } ?: "", { iso -> save(vm, s, t.copy(target = id.tub.dinas.um.isoToInput(iso))) }, Modifier.weight(1f), display = t.target)
                }
                R(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Status.values().forEach { st -> Chip(st.label, t.status == st) { save(vm, s, t.copy(status = st)) } }
                }
            }
        }
        item {
            Panel {
                Text("Perbaikan sistem", style = MaterialTheme.typography.titleMedium)
                if (s.poin.isEmpty()) Text("Belum ada catatan perbaikan sistem.", color = C.Muted, modifier = Modifier.padding(vertical = 6.dp))
                s.poin.sortedBy { it.order }.forEachIndexed { i, p ->
                    R(Modifier.padding(top = 6.dp)) {
                        Text("${i + 1}. ${p.text}", Modifier.weight(1f))
                        TextButton(onClick = { poinDialog = p.id to p.text }) { Text("Ubah") }
                        TextButton(onClick = { delPoin = p.id }) { Text("Hapus", color = C.Danger) }
                    }
                }
                Spacer(Modifier.height(6.dp))
                SoftButton("+ Poin") { poinDialog = null to "" }
            }
        }
    }
    poinDialog?.let { (id, text) ->
        var t by remember(id) { mutableStateOf(text) }
        AlertDialog(onDismissRequest = { poinDialog = null }, title = { Text(if (id == null) "Poin baru" else "Ubah poin") },
            text = { Field(t, { t = it }, "Isi poin", minLines = 3) },
            confirmButton = { TextButton(enabled = t.isNotBlank(), onClick = { vm.work { vm.repo.savePoin(s.dinas.id, id, t) }; poinDialog = null }) { Text("Simpan") } },
            dismissButton = { TextButton(onClick = { poinDialog = null }) { Text("Batal") } })
    }
    delPoin?.let { id -> Confirm("Hapus poin?", "Poin ini dihapus.", onConfirm = { vm.work { vm.repo.deletePoin(id) } }, onDismiss = { delPoin = null }) }
}

private fun save(vm: MainViewModel, s: DinasSnapshot, t: TemuanModel) = vm.work { vm.repo.saveTemuan(s.dinas.id, t) }

/** Kolom teks yang menyimpan saat fokus hilang, jadi tidak menulis ke database tiap ketukan. */
@Composable
fun SaveField(key: String, value: String, label: String, modifier: Modifier = Modifier, minLines: Int = 1, onSave: (String) -> Unit) {
    var v by remember(key, value) { mutableStateOf(value) }
    OutlinedTextField(v, { v = it }, modifier.fillMaxWidth().onFocusChanged { if (!it.isFocused && v != value) onSave(v) },
        label = { Text(label) }, minLines = minLines)
}
