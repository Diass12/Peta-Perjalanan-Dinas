package id.tub.dinas.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import id.tub.dinas.ui.fmtDate
import id.tub.dinas.ui.theme.ThemeState
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import id.tub.dinas.MainViewModel
import id.tub.dinas.domain.DinasSnapshot
import id.tub.dinas.domain.OwnerType
import id.tub.dinas.ui.DetailTab
import id.tub.dinas.ui.Editor
import id.tub.dinas.ui.Screen
import id.tub.dinas.ui.components.*
import id.tub.dinas.ui.theme.C

@Composable
fun DetailScreen(vm: MainViewModel, dinasId: String) {
    val snap by remember(dinasId) { vm.repo.observeSnapshot(dinasId) }.collectAsState(initial = null)
    var menu by remember { mutableStateOf(false) }
    var editDinas by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val xlsx = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) { uri ->
        if (uri != null) vm.exportExcel(dinasId, uri)
    }
    val s = snap
    Column(Modifier.fillMaxSize()) {
        val player = rememberPlayer(vm)
        GameHeader(player) {
            Box {
                Text("⋮", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { menu = true }.padding(horizontal = 14.dp, vertical = 6.dp))
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem({ Text("Ubah dinas") }, { menu = false; editDinas = true })
                    DropdownMenuItem({ Text("Tindak lanjut") }, { menu = false; vm.tab = DetailTab.TINDAK })
                    DropdownMenuItem({ Text("Export Excel") }, {
                        menu = false
                        val n = (s?.dinas?.name ?: "Dinas").replace(Regex("[^A-Za-z0-9 _-]"), "").trim().replace(' ', '-').ifEmpty { "Dinas" }
                        xlsx.launch("Laporan-$n.xlsx")
                    })
                    DropdownMenuItem({ Text("Mode presentasi") }, { menu = false; vm.push(Screen.Present(dinasId)) })
                    DropdownMenuItem({ Text("Pengaturan") }, { menu = false; vm.push(Screen.Settings) })
                    DropdownMenuItem({ Text("Hapus dinas", color = C.Danger) }, { menu = false; confirmDelete = true })
                }
            }
        }
        run {
            val ink = if (ThemeState.sky) Color.White else C.Ink
            val period = s?.dinas?.let { listOf(fmtDate(it.startMs), fmtDate(it.endMs)).filter { x -> x.isNotEmpty() }.joinToString(" – ") }.orEmpty()
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 10.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("‹", color = ink, fontSize = 34.sp, modifier = Modifier.clickable { vm.pop() }.padding(horizontal = 12.dp))
                Column(Modifier.weight(1f)) {
                    Text(s?.dinas?.name ?: "Dinas", color = ink, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                    Text(if (vm.tab == DetailTab.PETA) "Rute & titik dokumentasi" else vm.tab.label, color = ink.copy(alpha = 0.8f), fontSize = 12.sp)
                }
                val chip = period.ifEmpty { s?.let { "${it.titik.size} titik" }.orEmpty() }
                if (chip.isNotEmpty()) Text(chip, color = Color(0xFF1D4ED8), fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                    modifier = Modifier.background(Color.White, RoundedCornerShape(20.dp)).padding(horizontal = 12.dp, vertical = 6.dp))
            }
        }
        if (s == null) Empty("Memuat…")
        else Box(Modifier.weight(1f)) {
            when (vm.tab) {
                DetailTab.PETA -> PetaTab(vm, s)
                DetailTab.TEMUAN -> TemuanTab(vm, s)
                DetailTab.RINGKASAN -> RingkasanTab(s)
                DetailTab.TINDAK -> TindakTab(vm, s)
            }
        }
        val order = listOf(DetailTab.PETA, DetailTab.TEMUAN, DetailTab.PETA, DetailTab.RINGKASAN)
        WhiteNav(
            listOf(id.tub.dinas.R.drawable.ic_peta to "Peta", id.tub.dinas.R.drawable.ic_temuan to "Temuan", id.tub.dinas.R.drawable.ic_lensa_aset to "Aset", id.tub.dinas.R.drawable.ic_ringkasan to "Ringkasan"),
            order.indexOf(vm.tab),
        ) { if (it == 2) vm.push(Screen.Aset) else vm.tab = order[it] }
    }
    if (editDinas && s != null) DinasDialog(s.dinas, { editDinas = false }) { n, a, b, note -> vm.work { vm.repo.updateDinas(s.dinas.copy(name = n, startMs = a, endMs = b, note = note)) } }
    if (confirmDelete) Confirm("Hapus dinas?", "Semua titik, temuan, foto, dan PDF di dinas ini dihapus permanen. Buat cadangan dulu bila perlu.",
        onConfirm = { vm.work(onDone = { vm.pop() }) { vm.repo.deleteDinas(dinasId) } }, onDismiss = { confirmDelete = false })

    val ed = vm.editor
    if (ed != null && s != null) {
        Dialog(onDismissRequest = { closeEditor(vm, s) }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            when (ed) {
                is Editor.Titik -> TitikEditor(vm, s, ed.id, onClose = { closeEditor(vm, s) }, onSaved = { vm.editor = null })
                is Editor.Temuan -> TemuanEditor(vm, s, ed.id, onClose = { closeEditor(vm, s) }, onSaved = { vm.editor = null })
            }
        }
    }
}

/** Menutup editor; entri baru yang masih kosong dibuang supaya tidak menumpuk sampah. */
fun closeEditor(vm: MainViewModel, s: DinasSnapshot) {
    val ed = vm.editor
    vm.editor = null
    when (ed) {
        is Editor.Titik -> {
            val t = s.titik.firstOrNull { it.id == ed.id }
            if (t != null && t.geo == null && t.note.isBlank() && s.fotoOf(OwnerType.TITIK, t.id).isEmpty() && t.title.startsWith("Titik ")) vm.work { vm.repo.deleteTitik(t.id) }
        }
        is Editor.Temuan -> {
            val t = s.temuan.firstOrNull { it.id == ed.id }
            if (ed.isNew && t != null && t.description.isBlank() && t.activeLenses.isEmpty() && t.geo == null &&
                s.fotoOf(OwnerType.TEMUAN, t.id).isEmpty() && t.title.startsWith("Temuan ")) vm.work { vm.repo.deleteTemuan(t.id) }
        }
        null -> {}
    }
}
