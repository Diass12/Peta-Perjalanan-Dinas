package id.tub.dinas.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.tub.dinas.MainViewModel
import id.tub.dinas.domain.*
import id.tub.dinas.media.EncryptedImage
import id.tub.dinas.ui.Screen
import id.tub.dinas.ui.components.*
import id.tub.dinas.ui.theme.C
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private class Sel(val s: DinasSnapshot, val f: FotoModel, val judul: String, val geo: GeoPoint?, val index: Int)

@Composable
fun GaleriScreen(vm: MainViewModel) {
    val snaps by rememberSnapshots(vm)
    var perTanggal by remember { mutableStateOf(false) }
    val all = remember(snaps) {
        snaps.orEmpty().flatMap { s ->
            s.foto.map { f ->
                val own = s.fotoOf(f.ownerType, f.ownerId)
                val (judul, geo) = if (f.ownerType == OwnerType.TITIK) s.titik.firstOrNull { it.id == f.ownerId }.let { (it?.title ?: "Titik") to it?.geo }
                else s.temuan.firstOrNull { it.id == f.ownerId }.let { (it?.title ?: "Temuan") to it?.geo }
                Sel(s, f, judul, geo, own.indexOfFirst { it.id == f.id }.coerceAtLeast(0))
            }
        }
    }
    val tgl = SimpleDateFormat("d MMM yyyy", Locale("id", "ID")); val jam = SimpleDateFormat("HH:mm", Locale("id", "ID"))
    fun waktu(x: Sel) = x.f.takenAt ?: x.s.dinas.startMs
    val groups: List<Pair<String, List<Sel>>> = if (perTanggal) all.groupBy { waktu(it)?.let { t -> tgl.format(Date(t)) } ?: "Tanpa tanggal" }.toList().sortedByDescending { g -> g.second.maxOfOrNull { waktu(it) ?: 0L } ?: 0L }
    else all.groupBy { it.s.dinas.name }.toList()
    Column(Modifier.fillMaxSize().background(C.Bg)) {
        Header("Galeri Lapangan", onBack = { vm.pop() })
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Per lokasi/dinas", !perTanggal) { perTanggal = false }
            Chip("Per tanggal", perTanggal) { perTanggal = true }
        }
        if (snaps == null) Empty("Memuat…") else if (all.isEmpty()) Empty("Belum ada foto. Foto dari titik dan temuan akan muncul di sini lengkap dengan koordinat GPS dan waktu.")
        else LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            groups.forEach { (judul, fotos) ->
                item(key = "h$judul") { SectionTitle("$judul · ${fotos.size} foto") }
                items(fotos.chunked(2), key = { row -> row.first().f.id }) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { x ->
                            val shape = RoundedCornerShape(14.dp)
                            Column(Modifier.weight(1f).clip(shape).background(androidx.compose.ui.graphics.Color.White, shape).border(1.dp, C.Line, shape)
                                .clickable { vm.push(Screen.Photo(x.s.dinas.id, x.f.ownerId, x.index)) }) {
                                EncryptedImage(vm.repo.vault, x.f.path, Modifier.fillMaxWidth().height(120.dp), maxSide = 360)
                                Column(Modifier.padding(8.dp)) {
                                    Text(x.judul, fontSize = 13.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, maxLines = 1)
                                    waktu(x)?.let { t -> Text("${tgl.format(Date(t))} ${if (x.f.takenAt != null) jam.format(Date(t)) else ""}".trim(), color = C.Muted, fontSize = 11.sp) }
                                    Text(x.geo?.let { String.format(Locale.US, "%.5f, %.5f", it.lat, it.lng) } ?: "Tanpa koordinat", color = if (x.geo != null) C.Ok else C.Muted, fontSize = 11.sp)
                                    if (x.f.caption.isNotBlank()) Text(x.f.caption, fontSize = 11.sp, maxLines = 2)
                                }
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
