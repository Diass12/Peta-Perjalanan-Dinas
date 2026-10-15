package id.tub.dinas.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import id.tub.dinas.MainViewModel
import id.tub.dinas.domain.CoordParser
import id.tub.dinas.domain.DinasSnapshot
import id.tub.dinas.domain.OwnerType
import id.tub.dinas.media.EncryptedImage
import id.tub.dinas.ui.DetailTab
import id.tub.dinas.ui.Editor
import id.tub.dinas.ui.Screen
import id.tub.dinas.ui.components.*
import id.tub.dinas.ui.fmtDateTime
import id.tub.dinas.ui.map.MapPin
import id.tub.dinas.ui.map.OsmMap
import id.tub.dinas.ui.map.PinKind
import id.tub.dinas.ui.theme.C

@Composable
fun PetaTab(vm: MainViewModel, s: DinasSnapshot) {
    val ctx = LocalContext.current
    var sat by remember { mutableStateOf(vm.settings.satellite) }
    var delId by remember { mutableStateOf<String?>(null) }
    var toTemuan by remember { mutableStateOf<String?>(null) }
    val pins = s.titik.mapNotNull { t -> t.geo?.let { MapPin("t:${t.id}", it, t.title, PinKind.DOK) } } +
        s.temuan.mapNotNull { t -> t.geo?.let { MapPin("m:${t.id}", it, t.title, PinKind.LAP) } }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().height(230.dp)) {
            OsmMap(pins, sat, Modifier.fillMaxSize(), onPinClick = { id ->
                if (id.startsWith("t:")) vm.editor = Editor.Titik(id.removePrefix("t:")) else vm.editor = Editor.Temuan(id.removePrefix("m:"), false)
            })
            Row(Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                Chip(if (sat) "Satelit" else "Peta", true, C.Ink) { sat = !sat; vm.settings.satellite = sat }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("● Dokumentasi", color = C.PinDok, style = MaterialTheme.typography.bodyMedium)
            Text("● Laporan", color = C.PinLap, style = MaterialTheme.typography.bodyMedium)
        }
        Box(Modifier.weight(1f)) {
            if (s.titik.isEmpty()) Empty("Belum ada titik dokumentasi. Ketuk “+ Titik” lalu kunci GPS dan tambahkan foto.")
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 80.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(s.titik, key = { it.id }) { t ->
                    val fotos = s.fotoOf(OwnerType.TITIK, t.id)
                    Panel(onClick = { vm.editor = Editor.Titik(t.id) }) {
                        Text(t.title, style = MaterialTheme.typography.titleMedium)
                        Text(t.geo?.let { CoordParser.format(it) + (it.accuracyM?.let { a -> "  ±$a m" } ?: "") } ?: "Belum ada lokasi", color = if (t.geo != null) C.Muted else C.Danger, style = MaterialTheme.typography.bodyMedium)
                        Text(fmtDateTime(t.recordedAt), color = C.Muted, style = MaterialTheme.typography.bodyMedium)
                        if (t.note.isNotBlank()) Text(t.note, Modifier.padding(top = 4.dp))
                        if (fotos.isNotEmpty()) {
                            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                fotos.take(4).forEachIndexed { i, f ->
                                    EncryptedImage(vm.repo.vault, f.path, Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)).clickable { vm.push(Screen.Photo(s.dinas.id, t.id, i)) }, maxSide = 200)
                                }
                                if (fotos.size > 4) Text("+${fotos.size - 4}", color = C.Muted, modifier = Modifier.align(Alignment.CenterVertically))
                            }
                        }
                        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = { toTemuan = t.id }) { Text("Jadikan temuan") }
                            t.geo?.let { g -> TextButton(onClick = { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(CoordParser.mapsUrl(g)))) } }) { Text("Maps") } }
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { delId = t.id }) { Text("Hapus", color = C.Danger) }
                        }
                    }
                }
            }
            AmberButton("+ Titik", Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
                vm.work { val id = vm.repo.saveTitik(s.dinas.id, null, "", "", null); vm.editor = Editor.Titik(id) }
            }
        }
    }
    delId?.let { id -> Confirm("Hapus titik?", "Titik beserta fotonya dihapus permanen.", onConfirm = { vm.work { vm.repo.deleteTitik(id) } }, onDismiss = { delId = null }) }
    toTemuan?.let { id -> Confirm("Jadikan temuan laporan?", "Titik ini dipindah ke tab Temuan beserta foto dan lokasinya, lalu dihapus dari daftar dokumentasi.", "Pindahkan",
        onConfirm = { vm.work { vm.repo.titikToTemuan(id)?.let { n -> vm.tab = DetailTab.TEMUAN; vm.editor = Editor.Temuan(n, false) } } }, onDismiss = { toTemuan = null }) }
}
