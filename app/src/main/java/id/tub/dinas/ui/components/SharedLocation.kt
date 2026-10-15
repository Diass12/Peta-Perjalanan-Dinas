package id.tub.dinas.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import id.tub.dinas.MainViewModel
import id.tub.dinas.data.MapsLink
import id.tub.dinas.domain.CoordParser
import id.tub.dinas.domain.GeoPoint
import id.tub.dinas.ui.DetailTab
import id.tub.dinas.ui.Editor
import id.tub.dinas.ui.Screen
import id.tub.dinas.ui.theme.C

/** Muncul saat pengguna membagikan lokasi dari Google Maps ke aplikasi ini. */
@Composable
fun SharedLocationDialog(vm: MainViewModel, text: String) {
    val list by vm.dinasList.collectAsState()
    var geo by remember(text) { mutableStateOf<GeoPoint?>(null) }
    var done by remember(text) { mutableStateOf(false) }
    LaunchedEffect(text) { geo = MapsLink.resolve(text); done = true }
    val name = remember(text) { MapsLink.placeName(text) }

    AlertDialog(
        onDismissRequest = { vm.sharedText = null },
        title = { Text("Lokasi dari Google Maps") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val g = geo
                when {
                    !done -> Text("Membaca tautan…")
                    g == null -> Text("Koordinat tidak ditemukan di tautan ini (mungkin hanya memuat nama tempat atau perangkat sedang offline). Di Google Maps, tekan lama pada peta lalu salin koordinatnya, kemudian pakai tombol Tempel.", color = C.Danger)
                    list.isEmpty() -> Text("Terbaca: ${CoordParser.format(g)}. Buat dinas dulu di beranda, lalu bagikan lagi.")
                    else -> {
                        Text("Terbaca: ${CoordParser.format(g)}", color = C.PinLap)
                        Text("Buat titik dokumentasi \"$name\" di dinas:", color = C.Muted)
                        list.take(6).forEach { d ->
                            SoftButton(d.name, Modifier.fillMaxWidth()) {
                                vm.work(onDone = { }) {
                                    val tid = vm.repo.saveTitik(d.id, null, name, "", g.copy(source = "gmaps"))
                                    vm.sharedText = null
                                    vm.tab = DetailTab.PETA
                                    vm.push(Screen.Detail(d.id))
                                    vm.editor = Editor.Titik(tid)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton({ vm.sharedText = null }) { Text("Tutup") } },
    )
}
