package id.tub.dinas.ui.screens

import android.Manifest
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import id.tub.dinas.MainViewModel
import id.tub.dinas.domain.DokumenJenis
import id.tub.dinas.domain.DokumenModel
import id.tub.dinas.media.EncryptedImage
import id.tub.dinas.media.PhotoPipeline
import id.tub.dinas.ui.Screen
import id.tub.dinas.ui.components.*
import id.tub.dinas.ui.theme.C
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

@Composable
private fun StatusDok(m: DokumenModel, hariIni: String) {
    val st = m.status(hariIni)
    val col = when (st) { "Kedaluwarsa" -> C.Danger; "Segera habis" -> C.Amber; else -> C.Ok }
    Text("[$st]", color = col, fontSize = 12.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
        modifier = Modifier.background(col.copy(alpha = 0.10f), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 3.dp))
}

/** Trip Inventory: paspor, visa, tiket, voucher hotel, surat tugas, ID card — dengan lampiran terenkripsi. */
@Composable
fun DokumenScreen(vm: MainViewModel) {
    val list by remember { vm.repo.observeDokumen() }.collectAsState(initial = emptyList())
    var edit by remember { mutableStateOf<String?>(null) }   // "" = baru
    val hariIni = id.tub.dinas.um.todayIso()
    Column(Modifier.fillMaxSize().background(C.Bg)) {
        Header("Dokumen & Tiket", onBack = { vm.pop() }) { HeaderAction("+ Baru") { edit = "" } }
        if (list.isEmpty()) Empty("Simpan paspor, visa, tiket, voucher hotel, surat tugas, dan ID card di sini supaya mudah dibuka tanpa takut hilang. Semua lampiran dienkripsi di perangkat.")
        else LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(list, key = { it.id }) { d ->
                Panel(onClick = { edit = d.id }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (d.tipe == "jpg" && d.path.isNotEmpty()) EncryptedImage(vm.repo.vault, d.path, Modifier.size(64.dp).itemFrame(12.dp), maxSide = 220)
                        else Box(Modifier.size(64.dp).background(C.BrandSoft, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) { Text(d.jenis.ikon, fontSize = 28.sp) }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(d.judul.ifEmpty { d.jenis.label }, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                            Text(listOf(d.jenis.label, d.nomor).filter { it.isNotBlank() }.joinToString(" · "), color = C.Muted, fontSize = 13.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                StatusDok(d, hariIni)
                                if (d.berlakuSampai.isNotBlank()) Text("s/d ${id.tub.dinas.um.fmtTgl(d.berlakuSampai)}", color = C.Muted, fontSize = 12.sp)
                                if (d.tipe == "pdf") Text("PDF", color = C.Muted, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
    edit?.let { DokumenEditor(vm, it) { edit = null } }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DokumenEditor(vm: MainViewModel, id: String, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val all by remember { vm.repo.observeDokumen() }.collectAsState(initial = emptyList())
    val cur = all.firstOrNull { it.id == id }
    var docId by remember { mutableStateOf(id) }
    var jenis by remember { mutableStateOf(cur?.jenis ?: DokumenJenis.SURAT_TUGAS) }
    var judul by remember { mutableStateOf(cur?.judul ?: "") }
    var nomor by remember { mutableStateOf(cur?.nomor ?: "") }
    var sampai by remember { mutableStateOf(cur?.berlakuSampai ?: "") }
    var catatan by remember { mutableStateOf(cur?.catatan ?: "") }
    var hapus by remember { mutableStateOf(false) }
    val now = all.firstOrNull { it.id == docId }

    suspend fun pastikanTersimpan(): String {
        val m = DokumenModel(docId, jenis, judul.trim(), nomor.trim(), sampai, catatan.trim())
        val newId = vm.repo.saveDokumen(m); docId = newId; return newId
    }
    fun simpan(tutup: Boolean) = vm.work(onDone = { if (tutup) onClose() }) { pastikanTersimpan() }

    var camPath by rememberSaveable { mutableStateOf<String?>(null) }
    fun pasangFoto(uri: Uri, temp: File?) = vm.work {
        try {
            val did = pastikanTersimpan()
            val p = withContext(Dispatchers.IO) { PhotoPipeline.process(vm.getApplication<android.app.Application>(), uri, vm.settings.photoPreset, null) }
            vm.repo.setDokumenFile(did, jpeg = p.jpeg)
        } finally { temp?.delete() }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val p = camPath; camPath = null
        if (p != null) { val f = File(p); if (ok) pasangFoto(Uri.fromFile(f), f) else f.delete() }
    }
    fun launchCamera() {
        val f = File(File(ctx.cacheDir, "kamera").apply { mkdirs() }, "dok_${UUID.randomUUID()}.jpg")
        camPath = f.absolutePath
        camera.launch(FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f))
    }
    val camPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) launchCamera() else vm.toast("Izin kamera ditolak. Pakai Galeri atau PDF.") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { u -> if (u != null) pasangFoto(u, null) }
    val pdfPick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) vm.work {
            val did = pastikanTersimpan()
            val ins = ctx.contentResolver.openInputStream(u) ?: throw IllegalArgumentException("PDF tidak dapat dibuka")
            ins.use { vm.repo.setDokumenFile(did, pdf = it) }
        }
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        BackHandler(onBack = onClose)
        Column(Modifier.fillMaxSize().background(C.Bg)) {
            Header(if (id.isEmpty() && now == null) "Dokumen baru" else "Ubah dokumen", onBack = onClose) { HeaderAction("Simpan") { simpan(true) } }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp).navigationBarsPadding().imePadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Panel {
                    Text("JENIS", style = MaterialTheme.typography.labelSmall, color = C.Muted)
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        DokumenJenis.entries.forEach { j -> Chip("${j.ikon} ${j.label}", jenis == j) { jenis = j } }
                    }
                }
                Field(judul, { judul = it }, "Judul (mis. Surat Tugas Halmahera Okt 2026)", singleLine = true)
                Field(nomor, { nomor = it }, "Nomor / kode booking", singleLine = true)
                DateField("Berlaku sampai (kosongkan bila tidak ada)", sampai, { sampai = it })
                Field(catatan, { catatan = it }, "Catatan", minLines = 2)
                Panel {
                    Text("LAMPIRAN", style = MaterialTheme.typography.labelSmall, color = C.Muted)
                    Spacer(Modifier.height(6.dp))
                    val d = now
                    if (d != null && d.path.isNotEmpty()) {
                        if (d.tipe == "jpg") EncryptedImage(vm.repo.vault, d.path, Modifier.fillMaxWidth().height(220.dp).itemFrame(12.dp), maxSide = 900)
                        else SoftButton("📄 Buka PDF", Modifier.fillMaxWidth()) { vm.push(Screen.Pdf(d.path, d.judul.ifEmpty { d.jenis.label })); onClose() }
                        TextButton(onClick = { vm.work { vm.repo.hapusDokumenFile(d.id) } }) { Text("Hapus lampiran", color = C.Danger) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconTextButton(id.tub.dinas.R.drawable.ic_kamera, "Foto") {
                            if (androidx.core.content.ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) launchCamera() else camPerm.launch(Manifest.permission.CAMERA)
                        }
                        IconTextButton(id.tub.dinas.R.drawable.ic_galeri, "Galeri", primary = false) { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                        SoftButton("PDF") { pdfPick.launch(arrayOf("application/pdf")) }
                    }
                    Text("Lampiran disimpan terenkripsi. Memilih lampiran baru mengganti yang lama.", color = C.Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                }
                if (now != null) SoftButton("Hapus dokumen ini", Modifier.fillMaxWidth(), danger = true) { hapus = true }
            }
            Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) { AmberButton("Simpan", Modifier.fillMaxWidth()) { simpan(true) } }
        }
        if (hapus) Confirm("Hapus dokumen?", "Dokumen dan lampirannya dihapus permanen.", onConfirm = { hapus = false; vm.work(onDone = onClose) { vm.repo.deleteDokumen(docId) } }, onDismiss = { hapus = false })
    }
}
