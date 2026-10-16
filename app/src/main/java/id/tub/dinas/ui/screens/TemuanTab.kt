package id.tub.dinas.ui.screens

import id.tub.dinas.ui.components.Text
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
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            if (list.isEmpty()) Empty("Belum ada temuan laporan. Ketuk “+ Temuan”, atau pindahkan titik dari tab Peta.")
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(list, key = { it.id }) { t ->
                    val i = list.indexOf(t)
                    val fotos = s.fotoOf(OwnerType.TEMUAN, t.id)
                    var menu by remember { mutableStateOf(false) }
                    Panel(onClick = { vm.editor = Editor.Temuan(t.id, false) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(28.dp).background(C.OkSoft, androidx.compose.foundation.shape.CircleShape), contentAlignment = Alignment.Center) { Text("T${i + 1}", color = C.Ok, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                            Spacer(Modifier.width(10.dp))
                            Text(t.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            StatusPill(t.status)
                            Box {
                                Text("⋮", fontSize = 22.sp, color = C.Muted, modifier = Modifier.clickable { menu = true }.padding(horizontal = 8.dp))
                                DropdownMenu(menu, { menu = false }) {
                                    if (i > 0) DropdownMenuItem({ Text("Naikkan") }, { menu = false; reorder(vm, s, list.map { it.id }, i, i - 1) })
                                    if (i < list.lastIndex) DropdownMenuItem({ Text("Turunkan") }, { menu = false; reorder(vm, s, list.map { it.id }, i, i + 1) })
                                    DropdownMenuItem({ Text("Hapus", color = C.Danger) }, { menu = false; delId = t.id })
                                }
                            }
                        }
                        if (t.code.isNotBlank()) Text(t.code, color = C.Muted, style = MaterialTheme.typography.bodyMedium)
                        if (t.description.isNotBlank()) Text(t.description, maxLines = 3, modifier = Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodyMedium)
                        if (fotos.isNotEmpty()) {
                            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                fotos.take(3).forEachIndexed { k, f ->
                                    Box {
                                        EncryptedImage(vm.repo.vault, f.path, Modifier.size(88.dp).itemFrame(12.dp).clickable { vm.push(Screen.Photo(s.dinas.id, t.id, k)) }, maxSide = 300)
                                        if (k == 2 && fotos.size > 3) Text("+${fotos.size - 3}", color = Color.White, fontWeight = FontWeight.Bold,
                                            modifier = Modifier.align(Alignment.Center).background(Color(0x99111827), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 2.dp))
                                    }
                                }
                            }
                        }
                        if (t.activeLenses.isNotEmpty()) Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) { t.activeLenses.forEach { LensTag(it) } }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().background(Color.White).navigationBarsPadding().padding(12.dp)) {
            AmberButton("+ Temuan", Modifier.fillMaxWidth()) {
                vm.work { val id = vm.repo.newTemuan(s.dinas.id, null); vm.editor = Editor.Temuan(id, true) }
            }
        }
    }
    delId?.let { id -> Confirm("Hapus temuan?", "Temuan, foto, dan PDF lampirannya dihapus permanen.", onConfirm = { vm.work { vm.repo.deleteTemuan(id) } }, onDismiss = { delId = null }) }
}

private fun reorder(vm: MainViewModel, s: DinasSnapshot, ids: List<String>, from: Int, to: Int) {
    val m = ids.toMutableList(); val x = m.removeAt(from); m.add(to, x)
    vm.work { vm.repo.reorderTemuan(s.dinas.id, m) }
}
