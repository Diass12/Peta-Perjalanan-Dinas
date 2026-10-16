package id.tub.dinas.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.tub.dinas.MainViewModel
import id.tub.dinas.domain.CoordParser
import id.tub.dinas.domain.DinasSnapshot
import id.tub.dinas.domain.OwnerType
import id.tub.dinas.domain.TitikModel
import id.tub.dinas.media.EncryptedImage
import id.tub.dinas.route.RoadRouter
import id.tub.dinas.ui.DetailTab
import id.tub.dinas.ui.Editor
import id.tub.dinas.ui.Screen
import id.tub.dinas.ui.components.*
import id.tub.dinas.ui.map.MapPin
import id.tub.dinas.ui.map.MapStyle
import id.tub.dinas.ui.map.OsmMap
import id.tub.dinas.ui.map.PinKind
import id.tub.dinas.ui.theme.C
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal fun km(a: id.tub.dinas.domain.GeoPoint, b: id.tub.dinas.domain.GeoPoint): Double {
    val r = 6371.0
    val dLat = Math.toRadians(b.lat - a.lat); val dLng = Math.toRadians(b.lng - a.lng)
    val h = Math.sin(dLat / 2).let { it * it } + Math.cos(Math.toRadians(a.lat)) * Math.cos(Math.toRadians(b.lat)) * Math.sin(dLng / 2).let { it * it }
    return 2 * r * Math.asin(Math.sqrt(h))
}
private fun fmtKm(d: Double) = if (d < 1.0) "${(d * 1000).toInt()} m" else String.format(Locale("id", "ID"), "%.1f km", d)

/** Rute: garis lurus antar-titik (laut/udara) atau mengikuti jalan. */
@Composable
fun PetaTab(vm: MainViewModel, s: DinasSnapshot) {
    val ctx = LocalContext.current
    var mapStyle by remember { mutableStateOf(MapStyle.from(vm.settings.mapStyle)) }
    var fullOpen by remember { mutableStateOf(false) }
    var onRoad by remember { mutableStateOf(vm.settings.vehicle in listOf("mobil", "motor")) }
    val ordered = s.titik.sortedBy { it.order }
    val wps = ordered.mapNotNull { t -> t.geo?.let { it.lat to it.lng } }
    var route by remember { mutableStateOf<List<org.osmdroid.util.GeoPoint>>(emptyList()) }
    var routeNote by remember { mutableStateOf("") }
    LaunchedEffect(wps, onRoad) {
        val straight = wps.map { org.osmdroid.util.GeoPoint(it.first, it.second) }
        if (wps.size < 2) { route = emptyList(); routeNote = ""; return@LaunchedEffect }
        if (!onRoad) { route = straight; routeNote = "Garis lurus (laut/udara)"; return@LaunchedEffect }
        route = straight; routeNote = "Mengambil rute jalan…"
        val r = RoadRouter.route(wps)
        if (r != null) { route = r.map { org.osmdroid.util.GeoPoint(it.first, it.second) }; routeNote = "Mengikuti jalan" }
        else routeNote = "Rute jalan tidak tersedia (offline?), memakai garis lurus"
    }
    var delId by remember { mutableStateOf<String?>(null) }
    var toTemuan by remember { mutableStateOf<String?>(null) }
    val pins = ordered.mapIndexedNotNull { i, t -> t.geo?.let { MapPin("t:${t.id}", it, t.title, PinKind.DOK, "${i + 1}") } } +
        s.temuan.sortedBy { it.order }.mapIndexedNotNull { i, t -> t.geo?.let { MapPin("m:${t.id}", it, t.title, PinKind.LAP, "T${i + 1}") } }

    @Composable
    fun MapBox(mod: Modifier, full: Boolean) {
        Box(mod) {
            OsmMap(pins, false, Modifier.fillMaxSize(), style = mapStyle, onPinClick = { id ->
                fullOpen = false
                if (id.startsWith("t:")) vm.editor = Editor.Titik(id.removePrefix("t:")) else vm.editor = Editor.Temuan(id.removePrefix("m:"), false)
            }, route = route)
            if (routeNote.isNotEmpty()) Text(routeNote, color = Color.White, fontSize = 12.sp,
                modifier = Modifier.align(Alignment.BottomStart).padding(10.dp).background(Color(0xCC111827), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 3.dp))
            Row(Modifier.align(Alignment.TopEnd).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip(if (onRoad) "🚗 Jalan" else "⛴ Lurus", false, bg = Color.White) { onRoad = !onRoad; vm.settings.vehicle = if (onRoad) "mobil" else "kapal" }
                Chip(mapStyle.label, false, bg = Color.White) {
                    val all = MapStyle.values()
                    mapStyle = all[(mapStyle.ordinal + 1) % all.size]; vm.settings.mapStyle = mapStyle.name
                }
                Chip(if (full) "✕" else "⤢", false, bg = Color.White) { fullOpen = !full }
            }
        }
    }

    if (fullOpen) androidx.compose.ui.window.Dialog(onDismissRequest = { fullOpen = false },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(C.Bg).statusBarsPadding().navigationBarsPadding()) { MapBox(Modifier.fillMaxSize(), true) }
    }

    val dayFmt = SimpleDateFormat("EEEE, d MMMM yyyy", Locale("id", "ID"))
    val timeFmt = SimpleDateFormat("HH:mm", Locale.US)
    val groups = ordered.withIndex().groupBy { dayFmt.format(Date(it.value.recordedAt)) }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { MapBox(Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(16.dp)).border(1.dp, C.Line, RoundedCornerShape(16.dp)), false) }
            item {
                val reg by vm.aset.register.collectAsState()
                val withPhoto = ordered.count { s.fotoOf(OwnerType.TITIK, it.id).isNotEmpty() }
                val found = reg.count { it.state == id.tub.dinas.domain.AuditState.FOUND }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard("Titik berfoto", withPhoto, ordered.size, false, Modifier.weight(1f))
                    StatCard("Aset ditemukan", found, reg.size, true, Modifier.weight(1f))
                }
            }
            if (ordered.isEmpty()) item { Empty("Belum ada titik dokumentasi. Ketuk “+ Titik”, kunci GPS, lalu tambahkan foto.") }
            groups.forEach { (day, rows) ->
                item(key = "d_$day") {
                    val total = rows.zipWithNext().sumOf { (a, b) -> val ga = a.value.geo; val gb = b.value.geo; if (ga != null && gb != null) km(ga, gb) else 0.0 }
                    Column(Modifier.padding(top = 10.dp)) {
                        Text(day, style = MaterialTheme.typography.titleMedium)
                        Text("${rows.size} titik" + if (total > 0) " · ±${fmtKm(total)}" else "", color = C.Muted, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                rows.forEachIndexed { k, (idx, t) ->
                    item(key = t.id) {
                        val prev = if (k > 0) rows[k - 1].value else null
                        val jarak = if (prev?.geo != null && t.geo != null) fmtKm(km(prev.geo!!, t.geo!!)) else null
                        TitikRow(vm, s, t, idx + 1, last = k == rows.lastIndex, jarak = jarak, jam = timeFmt.format(Date(t.recordedAt)),
                            onTemuan = { toTemuan = t.id }, onMaps = { g -> runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(CoordParser.mapsUrl(g)))) } }, onDelete = { delId = t.id })
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().background(Color.White).navigationBarsPadding().padding(12.dp)) {
            AmberButton("📍 Kunci titik GPS baru", Modifier.fillMaxWidth()) {
                vm.work { val id = vm.repo.saveTitik(s.dinas.id, null, "", "", null); vm.editor = Editor.Titik(id) }
            }
        }
    }
    delId?.let { id -> Confirm("Hapus titik?", "Titik beserta fotonya dihapus permanen.", onConfirm = { vm.work { vm.repo.deleteTitik(id) } }, onDismiss = { delId = null }) }
    toTemuan?.let { id -> Confirm("Jadikan temuan laporan?", "Titik ini dipindah ke tab Temuan beserta foto dan lokasinya, lalu dihapus dari daftar dokumentasi.", "Pindahkan",
        onConfirm = { vm.work { vm.repo.titikToTemuan(id)?.let { n -> vm.tab = DetailTab.TEMUAN; vm.editor = Editor.Temuan(n, false) } } }, onDismiss = { toTemuan = null }) }
}

@Composable
private fun TitikRow(vm: MainViewModel, s: DinasSnapshot, t: TitikModel, nomor: Int, last: Boolean, jarak: String?, jam: String,
                     onTemuan: () -> Unit, onMaps: (id.tub.dinas.domain.GeoPoint) -> Unit, onDelete: () -> Unit) {
    val fotos = s.fotoOf(OwnerType.TITIK, t.id)
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Column(Modifier.width(36.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(28.dp).background(C.Brand, CircleShape), contentAlignment = Alignment.Center) { Text("$nomor", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
            if (!last) Box(Modifier.width(2.dp).weight(1f).background(C.Line))
        }
        Column(Modifier.weight(1f).padding(bottom = if (last) 0.dp else 8.dp)) {
            if (jarak != null) Text("↓ $jarak dari titik sebelumnya", color = C.Muted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp, start = 2.dp))
            Panel(onClick = { vm.editor = Editor.Titik(t.id) }) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text(t.title, style = MaterialTheme.typography.titleMedium)
                        Text(t.geo?.let { CoordParser.format(it) + (it.accuracyM?.let { a -> "  ±$a m" } ?: "") } ?: "Belum ada lokasi", color = if (t.geo != null) C.Muted else C.Danger, fontSize = 13.sp)
                        Text("🕒 $jam", color = C.Muted, fontSize = 13.sp)
                    }
                    if (fotos.isNotEmpty()) Box {
                        EncryptedImage(vm.repo.vault, fotos.first().path, Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)).clickable { vm.push(Screen.Photo(s.dinas.id, t.id, 0)) }, maxSide = 240)
                        if (fotos.size > 1) Text("+${fotos.size - 1}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).background(Color(0xAA111827), RoundedCornerShape(8.dp)).padding(horizontal = 6.dp, vertical = 1.dp))
                    }
                    Box {
                        Text("⋮", fontSize = 22.sp, color = C.Muted, modifier = Modifier.clickable { menu = true }.padding(horizontal = 8.dp))
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem({ Text("Jadikan temuan") }, { menu = false; onTemuan() })
                            t.geo?.let { g -> DropdownMenuItem({ Text("Buka di Maps") }, { menu = false; onMaps(g) }) }
                            DropdownMenuItem({ Text("Hapus", color = C.Danger) }, { menu = false; onDelete() })
                        }
                    }
                }
                if (t.note.isNotBlank()) Text(t.note, color = C.Ink, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}
