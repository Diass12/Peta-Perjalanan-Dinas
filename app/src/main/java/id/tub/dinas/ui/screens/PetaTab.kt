package id.tub.dinas.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.unit.sp
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
import id.tub.dinas.ui.map.VEHICLES
import id.tub.dinas.route.RoadRouter
import id.tub.dinas.ui.theme.ThemeState
import id.tub.dinas.ui.theme.C

@Composable
fun PetaTab(vm: MainViewModel, s: DinasSnapshot) {
    val ctx = LocalContext.current
    var mapStyle by remember { mutableStateOf(id.tub.dinas.ui.map.MapStyle.from(vm.settings.mapStyle)) }
    var illus by remember { mutableStateOf(false) }
    var fullOpen by remember { mutableStateOf(false) }
    var vehicleKey by remember { mutableStateOf(vm.settings.vehicle) }
    val skin = VEHICLES.firstOrNull { it.key == vehicleKey } ?: VEHICLES[0]
    val wps = s.titik.sortedBy { it.order }.mapNotNull { t -> t.geo?.let { it.lat to it.lng } }
    var route by remember { mutableStateOf<List<org.osmdroid.util.GeoPoint>>(emptyList()) }
    var routeNote by remember { mutableStateOf("") }
    LaunchedEffect(wps, skin.key) {
        val straight = wps.map { org.osmdroid.util.GeoPoint(it.first, it.second) }
        if (wps.size < 2) { route = emptyList(); routeNote = ""; return@LaunchedEffect }
        if (!skin.onRoad) { route = straight; routeNote = "Rute lurus (laut/udara)"; return@LaunchedEffect }
        route = straight; routeNote = "Mengambil rute jalan…"
        val r = RoadRouter.route(wps)
        if (r != null) { route = r.map { org.osmdroid.util.GeoPoint(it.first, it.second) }; routeNote = "Mengikuti jalan (OpenStreetMap)" }
        else routeNote = "Rute jalan tidak tersedia (offline?), memakai garis lurus"
    }
    val lastGeo = s.titik.sortedBy { it.order }.lastOrNull { it.geo != null }?.geo
    var delId by remember { mutableStateOf<String?>(null) }
    var toTemuan by remember { mutableStateOf<String?>(null) }
    val pins = s.titik.mapNotNull { t -> t.geo?.let { MapPin("t:${t.id}", it, t.title, PinKind.DOK) } } +
        s.temuan.mapNotNull { t -> t.geo?.let { MapPin("m:${t.id}", it, t.title, PinKind.LAP) } }

    @Composable
    fun MapBox(mod: Modifier, full: Boolean) {
Box(mod) {
            if (illus) androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(id.tub.dinas.R.drawable.peta_halmahera), null,
                Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
            else OsmMap(pins, false, Modifier.fillMaxSize(), style = mapStyle, onPinClick = { id ->
                fullOpen = false
                if (id.startsWith("t:")) vm.editor = Editor.Titik(id.removePrefix("t:")) else vm.editor = Editor.Temuan(id.removePrefix("m:"), false)
            }, route = route, vehicle = if (ThemeState.fx) skin else null)
            if (!illus) Column(Modifier.align(Alignment.BottomStart).padding(10.dp)) {
                if (routeNote.isNotEmpty()) Text(routeNote, color = Color.White, fontSize = 11.sp,
                    modifier = Modifier.background(Color(0xAA0B2545), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 2.dp))
                Spacer(Modifier.height(4.dp))
                lastGeo?.let { g -> Text("📍 " + CoordParser.format(g), color = Color.White, fontSize = 11.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    modifier = Modifier.background(Color(0xE6111827), RoundedCornerShape(14.dp)).padding(horizontal = 10.dp, vertical = 5.dp)) }
            }
            Row(Modifier.align(Alignment.TopEnd).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip(if (full) "✕ Tutup" else "⤢ Penuh", true, C.Ink) { fullOpen = !full }
                Chip("Ilustrasi", illus, C.Ink) { illus = !illus }
                if (!illus) Chip(mapStyle.label, true, C.Ink) {
                    val all = id.tub.dinas.ui.map.MapStyle.values()
                    mapStyle = all[(mapStyle.ordinal + 1) % all.size]; vm.settings.mapStyle = mapStyle.name
                }
            }
        }
    }

    if (fullOpen) androidx.compose.ui.window.Dialog(onDismissRequest = { fullOpen = false },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color(0xFF0B2545)).statusBarsPadding().navigationBarsPadding()) {
            MapBox(Modifier.fillMaxSize(), true)
        }
    }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 90.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Box(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(32.dp)).padding(6.dp)) {
                        MapBox(Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(27.dp)), false)
                    }
                }
                item {
                    Column {
                        Text("PILIH KENDARAAN PERJALANAN:", color = if (ThemeState.sky) Color.White else C.Muted, fontSize = 11.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            VEHICLES.forEach { v ->
                                val on = v.key == skin.key
                                Column(Modifier.weight(1f).background(if (on) Color(0xFF2563EB) else Color.White, RoundedCornerShape(18.dp))
                                    .clickable { vehicleKey = v.key; vm.settings.vehicle = v.key }.padding(vertical = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally) {
                                    androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(v.res), v.key, Modifier.size(34.dp))
                                    Text(v.key.replaceFirstChar { it.uppercase() }, fontSize = 10.sp, maxLines = 1, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = if (on) Color.White else Color(0xFF64748B))
                                }
                            }
                        }
                    }
                }
                item {
                    val reg by vm.aset.register.collectAsState()
                    val withPhoto = s.titik.count { s.fotoOf(OwnerType.TITIK, it.id).isNotEmpty() }
                    val found = reg.count { it.state == id.tub.dinas.domain.AuditState.FOUND }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatCard("PROGRES / DOKUMENTASI", withPhoto, s.titik.size, false, Modifier.weight(1f))
                        StatCard("AKURASI / AUDIT ASET", found, reg.size, true, Modifier.weight(1f))
                    }
                }
                item {
                    Row(Modifier.background(Color(0xCCFFFFFF), RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("● Dokumentasi", color = Color(0xFFB88A00), style = MaterialTheme.typography.bodyMedium)
                        Text("● Laporan", color = C.PinLap, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (s.titik.isEmpty()) item { Empty("Belum ada titik dokumentasi. Ketuk “+ Titik” lalu kunci GPS dan tambahkan foto.") }
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
