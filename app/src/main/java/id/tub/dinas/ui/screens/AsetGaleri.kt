package id.tub.dinas.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.tub.dinas.MainViewModel
import id.tub.dinas.domain.AsetModel
import id.tub.dinas.ui.components.Chip
import id.tub.dinas.ui.components.Field
import id.tub.dinas.ui.theme.C

/** Alur "Tambah dari Galeri": pilih cara → (pilih aset) → hasil. */
@Composable
fun GaleriDialogs(vm: MainViewModel, list: List<AsetModel>) {
    val c = vm.aset
    c.galeriUris?.let { uris ->
        AlertDialog(
            onDismissRequest = { c.galeriUris = null },
            title = { Text("${uris.size} foto dipilih") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Pilih cara memprosesnya:", color = C.Muted)
                    @Composable
                    fun Opsi(judul: String, ket: String, go: () -> Unit) = Column(Modifier.fillMaxWidth()) {
                        TextButton(onClick = go) { Text(judul, fontWeight = FontWeight.Bold) }
                        Text(ket, color = C.Muted, style = MaterialTheme.typography.labelSmall)
                    }
                    Opsi("Otomatis (baca kode)", "OCR mendalam tiap foto. Kode terbaca → cocokkan atau buat aset. Satu foto dengan banyak kode memproses banyak aset.") { c.galeri("auto", uris) }
                    Opsi("Ke aset yang sudah ada", "Semua foto ditambahkan ke satu aset pilihan Anda.") { c.pilihAsetUntuk = uris; c.galeriUris = null }
                    Opsi("Jadikan satu aset baru", "Satu aset baru berisi semua foto ini, lalu formulirnya dibuka.") { c.galeri("new", uris) }
                    Opsi("Satu aset per foto", "Seperti otomatis, tetapi foto tanpa kode juga dibuat jadi aset sendiri.") { c.galeri("each", uris) }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { c.galeriUris = null }) { Text("Batal") } },
        )
    }

    c.pilihAsetUntuk?.let { uris ->
        var q by remember { mutableStateOf("") }
        val hasil = remember(q, list) {
            val t = q.trim().lowercase()
            (if (t.isEmpty()) list else list.filter { listOf(it.code, it.name, it.location, it.dept).any { s -> s.lowercase().contains(t) } }).take(50)
        }
        AlertDialog(
            onDismissRequest = { c.pilihAsetUntuk = null },
            title = { Text("Pilih aset tujuan") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Field(q, { q = it }, "Cari kode atau nama", singleLine = true)
                    LazyColumn(Modifier.heightIn(max = 320.dp)) {
                        items(hasil, key = { it.id }) { a ->
                            TextButton(onClick = { c.galeri("existing", uris, a.id) }, modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.fillMaxWidth()) {
                                    Text(a.code.ifEmpty { "(tanpa kode)" }, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                    Text(a.name.ifEmpty { "(tanpa nama)" }, color = C.Muted, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { c.pilihAsetUntuk = null }) { Text("Batal") } },
        )
    }

    c.galeriHasil?.let { h ->
        AlertDialog(
            onDismissRequest = { c.galeriHasil = null },
            title = { Text("Hasil dari galeri") },
            text = {
                Column(Modifier.heightIn(max = 380.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val baru = h.lines.count { it.baru }
                    Text("${h.lines.size - baru} aset cocok · $baru aset baru" + (if (h.dibuat > 0) " · ${h.dibuat} aset tanpa kode dibuat" else "") + (if (h.gagal > 0) " · ${h.gagal} foto gagal" else ""), fontWeight = FontWeight.Bold)
                    if (h.tanpaKode > 0) Text("${h.tanpaKode} foto tanpa kode terbaca.", color = C.Danger)
                    LazyColumn {
                        items(h.lines) { l ->
                            Text(l.kode + (if (l.fuzzy) " (~)" else "") + " — " + (if (l.baru) "baru" else l.nama.ifEmpty { "cocok" }), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = {
                if (h.tanpaKode > 0) TextButton(onClick = { c.buatTanpaKode(h.jpegTanpaKode) }) { Text("Buat ${h.tanpaKode} aset tanpa kode") }
                else TextButton(onClick = { c.galeriHasil = null }) { Text("Tutup") }
            },
            dismissButton = { if (h.tanpaKode > 0) TextButton(onClick = { c.galeriHasil = null }) { Text("Abaikan") } },
        )
    }
}
