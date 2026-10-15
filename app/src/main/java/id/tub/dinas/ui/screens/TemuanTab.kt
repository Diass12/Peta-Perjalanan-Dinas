package id.tub.dinas.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import id.tub.dinas.MainViewModel
import id.tub.dinas.domain.DinasSnapshot
import id.tub.dinas.domain.OwnerType
import id.tub.dinas.media.EncryptedImage
import id.tub.dinas.ui.Editor
import id.tub.dinas.ui.Screen
import id.tub.dinas.ui.components.*
import id.tub.dinas.ui.theme.C

@Composable
fun TemuanTab(vm: MainViewModel, s: DinasSnapshot) {
    var delId by remember { mutableStateOf<String?>(null) }
    val list = s.temuan.sortedBy { it.order }
    Box(Modifier.fillMaxSize()) {
        if (list.isEmpty()) Empty("Belum ada temuan laporan. Ketuk “+ Temuan”, atau pindahkan titik dari tab Peta.")
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 80.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(list, key = { it.id }) { t ->
                val i = list.indexOf(t)
                val fotos = s.fotoOf(OwnerType.TEMUAN, t.id)
                Panel(onClick = { vm.editor = Editor.Temuan(t.id, false) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}. ${t.title}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        if (t.status == id.tub.dinas.domain.Status.SELESAI) { Pic(id.tub.dinas.R.drawable.ic_lencana, 30.dp); Spacer(Modifier.width(6.dp)) }
                        StatusPill(t.status)
                    }
                    if (t.code.isNotBlank()) Text(t.code, color = C.Muted, style = MaterialTheme.typography.bodyMedium)
                    if (fotos.isNotEmpty()) {
                        Box(Modifier.padding(top = 8.dp).fillMaxWidth().height(150.dp).itemFrame(14.dp).clickable { vm.push(Screen.Photo(s.dinas.id, t.id, 0)) }) {
                            EncryptedImage(vm.repo.vault, fotos[0].path, Modifier.matchParentSize(), maxSide = 900)
                            Text("📷 ${fotos.size}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).background(Color(0xAA0B2545), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 3.dp))
                        }
                    }
                    if (t.description.isNotBlank()) Text(t.description, maxLines = 3, modifier = Modifier.padding(top = 4.dp))
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) { t.activeLenses.forEach { LensTag(it) } }
                    if (fotos.size > 1) {
                        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            fotos.drop(1).take(4).forEachIndexed { k, f ->
                                EncryptedImage(vm.repo.vault, f.path, Modifier.size(56.dp).itemFrame(8.dp).clickable { vm.push(Screen.Photo(s.dinas.id, t.id, k + 1)) }, maxSide = 200)
                            }
                            if (fotos.size > 5) Text("+${fotos.size - 5}", color = C.Muted, modifier = Modifier.align(Alignment.CenterVertically))
                        }
                    }
                    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(enabled = i > 0, onClick = { reorder(vm, s, list.map { it.id }, i, i - 1) }) { Text("↑") }
                        TextButton(enabled = i < list.lastIndex, onClick = { reorder(vm, s, list.map { it.id }, i, i + 1) }) { Text("↓") }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { delId = t.id }) { Text("Hapus", color = C.Danger) }
                    }
                }
            }
        }
        AmberButton("+ Temuan", Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
            vm.work { val id = vm.repo.newTemuan(s.dinas.id, null); vm.editor = Editor.Temuan(id, true) }
        }
    }
    delId?.let { id -> Confirm("Hapus temuan?", "Temuan, foto, dan PDF lampirannya dihapus permanen.", onConfirm = { vm.work { vm.repo.deleteTemuan(id) } }, onDismiss = { delId = null }) }
}

private fun reorder(vm: MainViewModel, s: DinasSnapshot, ids: List<String>, from: Int, to: Int) {
    val m = ids.toMutableList(); val x = m.removeAt(from); m.add(to, x)
    vm.work { vm.repo.reorderTemuan(s.dinas.id, m) }
}
