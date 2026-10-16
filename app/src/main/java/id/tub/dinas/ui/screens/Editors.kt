package id.tub.dinas.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import id.tub.dinas.MainViewModel
import id.tub.dinas.media.StampSource
import id.tub.dinas.domain.*
import id.tub.dinas.ui.Screen
import id.tub.dinas.ui.components.*
import id.tub.dinas.ui.map.MapPin
import id.tub.dinas.ui.map.PinKind
import id.tub.dinas.ui.theme.C
import id.tub.dinas.ui.theme.screenBackground

private fun pinsExcept(s: DinasSnapshot, exceptId: String): List<MapPin> =
    s.titik.filter { it.id != exceptId }.mapNotNull { t -> t.geo?.let { MapPin("t:${t.id}", it, t.title, PinKind.DOK) } } +
        s.temuan.filter { it.id != exceptId }.mapNotNull { t -> t.geo?.let { MapPin("m:${t.id}", it, t.title, PinKind.LAP) } }

@Composable
fun TitikEditor(vm: MainViewModel, s: DinasSnapshot, id: String, onClose: () -> Unit, onSaved: () -> Unit) {
    val t = s.titik.firstOrNull { it.id == id }
    if (t == null) { LaunchedEffect(Unit) { kotlinx.coroutines.delay(3000); onSaved() }; Box(Modifier.fillMaxSize().background(C.Bg), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = C.Brand) }; return }
    var title by remember(id) { mutableStateOf(if (t.title.startsWith("Titik ") && t.geo == null && t.note.isBlank()) "" else t.title) }
    var note by remember(id) { mutableStateOf(t.note) }
    var geo by remember(id) { mutableStateOf(t.geo) }
    var exif by remember(id) { mutableStateOf<GeoPoint?>(null) }
    val fotos = s.fotoOf(OwnerType.TITIK, id)
    fun persist(after: (() -> Unit)? = null) = vm.work(onDone = after) { vm.repo.saveTitik(s.dinas.id, id, title, note, geo) }

    Column(Modifier.fillMaxSize().screenBackground().imePadding()) {
        Header("Titik dokumentasi", onBack = onClose, actions = { HeaderAction("Simpan") { persist(onSaved) } })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Field(title, { title = it }, "Judul titik (mis. Gerbang utama)", singleLine = true)
            GeoSection(vm, geo, { geo = it }, exif, pinsExcept(s, id))
            PhotoSection(vm, s.dinas.id, OwnerType.TITIK, id, fotos, onOpen = { i -> persist { vm.push(Screen.Photo(s.dinas.id, id, i)) } }, onExif = { if (exif == null) exif = it },
                stampOf = { StampSource(title, note, geo, null) })
            MediaSection(vm, s.dinas.id, OwnerType.TITIK, id, s.mediaOf(OwnerType.TITIK, id))
            Field(note, { note = it }, "Catatan", minLines = 3)
            AmberButton("Simpan titik", Modifier.fillMaxWidth()) { persist(onSaved) }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun TemuanEditor(vm: MainViewModel, s: DinasSnapshot, id: String, onClose: () -> Unit, onSaved: () -> Unit) {
    val t = s.temuan.firstOrNull { it.id == id }
    if (t == null) { LaunchedEffect(Unit) { kotlinx.coroutines.delay(3000); onSaved() }; Box(Modifier.fillMaxSize().background(C.Bg), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = C.Brand) }; return }
    var title by remember(id) { mutableStateOf(if (t.title.startsWith("Temuan ") && t.description.isBlank()) "" else t.title) }
    var code by remember(id) { mutableStateOf(t.code) }
    var desc by remember(id) { mutableStateOf(t.description) }
    var lens by remember(id) { mutableStateOf(t.lens) }
    var geo by remember(id) { mutableStateOf(t.geo) }
    var exif by remember(id) { mutableStateOf<GeoPoint?>(null) }
    val fotos = s.fotoOf(OwnerType.TEMUAN, id)
    val lampiran = s.lampiranOf(id)
    var delLamp by remember { mutableStateOf<String?>(null) }

    fun current() = t.copy(title = title, code = code, description = desc, lens = lens, geo = geo)
    fun persist(after: (() -> Unit)? = null) = vm.work(onDone = after) { vm.repo.saveTemuan(s.dinas.id, current()) }

    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.addPdf(s.dinas.id, id, uri) }

    Column(Modifier.fillMaxSize().screenBackground().imePadding()) {
        Header("Temuan laporan", onBack = onClose, actions = { HeaderAction("Simpan") { persist(onSaved) } })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Field(title, { title = it }, "Judul temuan", singleLine = true)
            Field(code, { code = it }, "Kode aset / lokasi (opsional)", singleLine = true)
            PhotoSection(vm, s.dinas.id, OwnerType.TEMUAN, id, fotos, onOpen = { i -> persist { vm.push(Screen.Photo(s.dinas.id, id, i)) } }, onExif = { if (exif == null) exif = it },
                stampOf = { StampSource(title, desc.take(100), geo, null) })
            MediaSection(vm, s.dinas.id, OwnerType.TEMUAN, id, s.mediaOf(OwnerType.TEMUAN, id))
            Field(desc, { desc = it }, "Penjelasan", minLines = 4)

            Panel {
                Text("LENSA LAPORAN", style = MaterialTheme.typography.labelSmall, color = C.Muted)
                Text("Pilih lensa yang relevan. Lensa yang tidak dipilih tidak muncul di ringkasan dan Excel.", color = C.Muted, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Lens.values().forEach { l ->
                        val on = lens[l] != null
                        Chip(l.label, on, C.lens(l), C.lensBg(l)) { lens = lens + (l to (if (on) null else "")) }
                    }
                }
                Lens.values().forEach { l ->
                    val v = lens[l]
                    if (v != null) {
                        Spacer(Modifier.height(8.dp))
                        Field(v, { nv -> lens = lens + (l to nv) }, "Keterangan ${l.label}", minLines = 2)
                    }
                }
            }

            GeoSection(vm, geo, { geo = it }, exif, pinsExcept(s, id))

            Panel {
                Text("LAMPIRAN PDF (${lampiran.size})", style = MaterialTheme.typography.labelSmall, color = C.Muted)
                Spacer(Modifier.height(6.dp))
                lampiran.forEach { l ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(l.name)
                            Text("${l.sizeBytes / 1024} KB", color = C.Muted, style = MaterialTheme.typography.bodyMedium)
                        }
                        TextButton(onClick = { persist { vm.push(Screen.Pdf(l.path, l.name)) } }) { Text("Buka") }
                        TextButton(onClick = { delLamp = l.id }) { Text("Hapus", color = C.Danger) }
                    }
                }
                Spacer(Modifier.height(6.dp))
                SoftButton("+ Lampirkan PDF") { pdfPicker.launch(arrayOf("application/pdf")) }
            }
            AmberButton("Simpan temuan", Modifier.fillMaxWidth()) { persist(onSaved) }
            Spacer(Modifier.height(24.dp))
        }
    }
    delLamp?.let { lid -> Confirm("Hapus lampiran?", "PDF dihapus permanen dari aplikasi.", onConfirm = { vm.work { vm.repo.deleteLampiran(lid) } }, onDismiss = { delLamp = null }) }
}
