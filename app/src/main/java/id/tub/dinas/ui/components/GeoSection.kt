package id.tub.dinas.ui.components

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import id.tub.dinas.MainViewModel
import id.tub.dinas.domain.CoordParser
import id.tub.dinas.domain.GeoPoint
import id.tub.dinas.ui.map.MapPin
import id.tub.dinas.ui.map.OsmMap
import id.tub.dinas.ui.map.PinKind
import id.tub.dinas.ui.theme.C
import id.tub.dinas.ui.theme.screenBackground

/** Pengatur lokasi: kunci GPS, tempel koordinat, pilih di peta, atau pakai lokasi dari foto. */
@Composable
fun GeoSection(vm: MainViewModel, geo: GeoPoint?, onGeo: (GeoPoint?) -> Unit, exifGeo: GeoPoint?, otherPins: List<MapPin>) {
    val ctx = LocalContext.current
    val gps by vm.tracker.state.collectAsState()
    var pasteOpen by remember { mutableStateOf(false) }
    var pickOpen by remember { mutableStateOf(false) }

    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) vm.tracker.start() else vm.toast("Izin lokasi ditolak. Anda masih bisa menempel koordinat atau memilih di peta.")
    }
    DisposableEffect(Unit) { onDispose { vm.tracker.stop() } }

    Panel {
        Text("LOKASI", style = MaterialTheme.typography.labelSmall, color = C.Muted)
        Spacer(Modifier.height(4.dp))
        if (geo != null) {
            Text(CoordParser.format(geo), style = MaterialTheme.typography.titleMedium)
            val extra = listOfNotNull(geo.accuracyM?.let { "±$it m" }, geo.source.takeIf { it.isNotEmpty() }).joinToString(" · ")
            if (extra.isNotEmpty()) Text(extra, color = C.Muted, style = MaterialTheme.typography.bodyMedium)
        } else Text("Belum ada lokasi", color = C.Muted)

        if (gps.running) {
            Spacer(Modifier.height(8.dp))
            val f = gps.fix
            Text(if (f != null) "GPS: ${CoordParser.format(f)}  (±${f.accuracyM ?: "?"} m)" else (gps.message ?: "Mencari sinyal…"), color = C.PinLap)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                AmberButton("Kunci lokasi", enabled = f != null) { onGeo(f); vm.tracker.stop() }
                SoftButton("Batal") { vm.tracker.stop() }
            }
        } else {
            gps.message?.takeIf { it.startsWith("Izin") || it.contains("tidak") }?.let { Text(it, color = C.Danger) }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconTextButton(id.tub.dinas.R.drawable.ic_gps, "Ambil GPS") { if (vm.tracker.hasPermission()) vm.tracker.start() else perm.launch(Manifest.permission.ACCESS_FINE_LOCATION) }
                SoftButton("Tempel") { pasteOpen = true }
                SoftButton("Peta") { pickOpen = true }
            }
        }
        if (exifGeo != null && geo == null) {
            Spacer(Modifier.height(6.dp))
            SoftButton("Pakai lokasi dari foto (${CoordParser.format(exifGeo)})") { onGeo(exifGeo) }
        }
        if (geo != null && !gps.running) {
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SoftButton("Buka di Maps") { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(CoordParser.mapsUrl(geo)))) } }
                SoftButton("Hapus lokasi", danger = true) { onGeo(null) }
            }
        }
    }

    if (pasteOpen) PasteDialog(onDismiss = { pasteOpen = false }) { g -> onGeo(g.copy(source = "tempel")) }
    if (pickOpen) PickerDialog(vm, geo, otherPins, onDismiss = { pickOpen = false }) { g -> onGeo(g) }
}

@Composable
private fun PasteDialog(onDismiss: () -> Unit, onOk: (GeoPoint) -> Unit) {
    val clip = LocalClipboardManager.current
    var text by remember { mutableStateOf(clip.getText()?.text?.takeIf { CoordParser.parse(it) != null } ?: "") }
    val parsed = CoordParser.parse(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tempel koordinat") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Salin dari Google Maps (tahan titik → salin koordinat) atau tempel tautan Maps.", color = C.Muted)
                Field(text, { text = it }, "Koordinat atau tautan", minLines = 2)
                if (text.isNotBlank()) Text(if (parsed != null) "Terbaca: ${CoordParser.format(parsed)}" else "Belum terbaca. Contoh: 1.234567, 127.800000", color = if (parsed != null) C.PinLap else C.Danger)
            }
        },
        confirmButton = { TextButton(enabled = parsed != null, onClick = { onOk(parsed!!); onDismiss() }) { Text("Pakai") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

@Composable
private fun PickerDialog(vm: MainViewModel, current: GeoPoint?, others: List<MapPin>, onDismiss: () -> Unit, onOk: (GeoPoint) -> Unit) {
    var sel by remember { mutableStateOf(current) }
    var sat by remember { mutableStateOf(vm.settings.satellite) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().screenBackground()) {
            Header("Pilih lokasi di peta", onBack = onDismiss, actions = { HeaderAction(if (sat) "Peta" else "Satelit") { sat = !sat } })
            val pins = others + listOfNotNull(sel?.let { MapPin("pick", it, "Dipilih", PinKind.PICK) })
            OsmMap(pins, sat, Modifier.weight(1f).fillMaxWidth(), onTap = { sel = it.copy(source = "peta") }, fitKey = current?.lat ?: 0.0)
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(sel?.let { CoordParser.format(it) } ?: "Ketuk peta untuk memilih", Modifier.weight(1f))
                AmberButton("Pakai", enabled = sel != null) { onOk(sel!!); onDismiss() }
            }
        }
    }
}
