package id.tub.dinas.ui.screens

import id.tub.dinas.ui.components.Text
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import id.tub.dinas.MainViewModel
import id.tub.dinas.media.EncryptedImage
import id.tub.dinas.ui.components.*
import id.tub.dinas.ui.theme.C

/** Pratinjau "Jadikan aset": tiap foto dinas, kode yang terbaca, dan pilihan Baru / Tempel / Lewati. */
@Composable
fun FotoKeAsetDialog(vm: MainViewModel) {
    val c = vm.aset
    val rencana = c.fotoRencana ?: return
    val pts = remember { vm.settings.ptDaftar.map { it.nama } }
    val jalan = rencana.count { it.aksi != "lewati" }
    Dialog(onDismissRequest = { c.fotoRencana = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = C.Surface) {
            Column(Modifier.fillMaxSize().systemBarsPadding().padding(16.dp)) {
                Text("Jadikan aset", style = MaterialTheme.typography.titleLarge)
                Text("${rencana.count { it.aksi == "baru" }} baru · ${rencana.count { it.aksi == "tempel" }} ditempel · ${rencana.count { it.aksi == "lewati" }} dilewati. Periksa dulu; foto asli tetap ada di dinas.",
                    color = C.Muted, fontSize = 13.sp, modifier = Modifier.padding(vertical = 6.dp))
                if (pts.isNotEmpty()) {
                    Text("PT pemilik aset baru", color = C.Muted, fontSize = 12.sp)
                    Row(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(c.fotoOwner.isBlank(), { c.fotoOwner = "" }, label = { Text("Tanpa PT") })
                        pts.take(4).forEach { n -> FilterChip(c.fotoOwner == n, { c.fotoOwner = n }, label = { Text(n, maxLines = 1) }) }
                    }
                }
                Field(c.fotoLokasi, { c.fotoLokasi = it }, "Lokasi (kosongkan = ikut keterangan temuan/titik)", singleLine = true)
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(rencana, key = { it.foto.id }) { r ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.Bg).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            EncryptedImage(vm.repo.vault, r.foto.path, Modifier.size(72.dp).clip(RoundedCornerShape(10.dp)), maxSide = 240)
                            Column(Modifier.weight(1f)) {
                                Text(if (r.kode.isNotBlank()) r.kode else "Tanpa kode", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(r.catatan, color = C.Muted, fontSize = 12.sp)
                                if (r.aksi == "baru") Field(r.nama, { r.nama = it }, "Nama aset", singleLine = true, modifier = Modifier.padding(top = 4.dp))
                                Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    FilterChip(r.aksi == "baru", { r.aksi = "baru" }, label = { Text("Baru") })
                                    if (r.bisaTempel) FilterChip(r.aksi == "tempel", { r.aksi = "tempel" }, label = { Text("Tempel") })
                                    FilterChip(r.aksi == "lewati", { r.aksi = "lewati" }, label = { Text("Lewati") })
                                }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    SoftButton("Batal", Modifier.weight(1f)) { c.fotoRencana = null }
                    PrimaryButton("Terapkan ($jalan)", Modifier.weight(1f), enabled = jalan > 0) { c.terapkanFoto() }
                }
            }
        }
    }
}
