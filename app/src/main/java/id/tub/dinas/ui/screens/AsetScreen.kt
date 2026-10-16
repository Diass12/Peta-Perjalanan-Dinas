package id.tub.dinas.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import id.tub.dinas.MainViewModel
import id.tub.dinas.aset.ScanReport
import id.tub.dinas.domain.*
import id.tub.dinas.media.EncryptedImage
import id.tub.dinas.ui.components.*
import id.tub.dinas.ui.theme.C
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@Composable
fun AsetScreen(vm: MainViewModel) {
    val c = vm.aset
    val list by c.register.collectAsState()
    val fotos by c.fotos.collectAsState()
    val ctx = LocalContext.current
    var exportDialog by remember { mutableStateOf(false) }
    var exportReq by remember { mutableStateOf<Pair<Int, List<AsetModel>>?>(null) }
    var resetAsk by remember { mutableStateOf(false) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(50)) { uris -> if (uris.isNotEmpty()) c.galeriUris = uris }
    val importPick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) c.siapkanImport(uri) }
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) { uri ->
        val req = exportReq; exportReq = null
        if (uri != null && req != null) c.exportMemo(uri, req.first, req.second)
    }

    val filtered = remember(list, c.query, c.kategori, c.barcode, c.auditFilter, c.peringatan, c.deptFilter, c.ownerFilter, fotos) { c.filtered(list) }
    var masterOpen by remember { mutableStateOf(false) }
    var aksi by remember { mutableStateOf<String?>(null) }      // dialog aksi massal yang sedang terbuka
    val sel = c.selected

    androidx.activity.compose.BackHandler(enabled = c.tab != 0) { c.tab = 0 }
    androidx.activity.compose.BackHandler(enabled = c.selMode) { c.selesaiPilih() }

    val goFilter: (id.tub.dinas.aset.AsetController.() -> Unit) -> Unit = { f -> c.resetFilter(); c.f(); c.tab = 1 }
    val scanAudit = { c.auditMode = true; c.scannerBuka = true }
    val doGallery = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    val doImport = { importPick.launch(arrayOf("*/*")) }
    val doExport = { exportDialog = true }
    val doLabels = { vm.toast("Label QR: menyusul di pembaruan berikutnya") }

    Column(Modifier.fillMaxSize().background(A.Bg)) {
        Box(Modifier.weight(1f)) {
            when (c.tab) {
                0 -> AHome(vm, list, fotos, scanAudit, goFilter)
                1 -> AAssets(vm, list, filtered, fotos, doGallery, doExport, doLabels)
                2 -> AAudit(vm, list, fotos, scanAudit) { resetAsk = true }
                else -> AMenu(vm, list.size, doImport, doExport, { masterOpen = true }, doGallery, doLabels) { c.tab = 1; c.selMode = true }
            }
        }
        if (c.selMode && c.tab == 1) ASelectionBar(vm, filtered) { aksi = it }
        ANav(c.tab, { c.tab = it; if (it != 1) c.selMode = false }) { c.scannerBuka = true }
    }

    if (masterOpen) MasterDialog(vm) { masterOpen = false }
    when (aksi) {
        "tabel" -> BulkGridDialog(vm, list.filter { it.id in sel }.map { it.id }) { aksi = null }
        "massal" -> BulkEditDialog(vm, sel) { aksi = null }
        "foto" -> BulkFotoDialog(vm, list.filter { it.id in sel }.map { it.id }) { aksi = null }
        "status" -> BulkStatusDialog(vm, sel) { aksi = null }
        "hapus" -> Confirm("Hapus ${sel.size} aset?", "Aset terpilih dan semua fotonya dihapus permanen.", onConfirm = { c.bulkHapus(sel) }, onDismiss = { aksi = null })
        "ekspor" -> { exportDialog = true; aksi = null }
        "label" -> { doLabels(); aksi = null }
        else -> {}
    }
    c.sibuk?.let { msg ->
        AlertDialog(onDismissRequest = {}, title = { Text("Sedang diproses") }, text = { Text(msg) },
            confirmButton = { TextButton(onClick = { c.berhenti = true }) { Text("Berhenti") } })
    }

    if (c.scannerBuka) AsetScanner(vm) { c.scannerBuka = false }
    GaleriDialogs(vm, list)

    c.report?.let { rep -> ScanReportDialog(vm, rep) }
    c.importState?.let { st -> ImportDialog(vm, st) }
    c.editing?.let { id -> AsetEditor(vm, id) }

    if (exportDialog) ExportDialog(
        total = list.size, terfilter = filtered.size, terpilih = sel.size,
        onDismiss = { exportDialog = false },
        onGo = { n, cakupan ->
            exportDialog = false
            val scope = when (cakupan) { 1 -> filtered; 2 -> list.filter { it.id in sel }; else -> list }
            exportReq = n to scope
            create.launch("Physical Check ${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())}.xlsx")
        },
    )
    if (resetAsk) Confirm("Sesi audit baru?", "Semua status audit dikembalikan ke “Belum”. Data aset dan foto tidak berubah.", "Mulai", onConfirm = { c.resetAudit() }, onDismiss = { resetAsk = false })
}

@Composable
private fun ScanReportDialog(vm: MainViewModel, rep: ScanReport) {
    val c = vm.aset
    AlertDialog(
        onDismissRequest = { c.report = null },
        title = { Text(if (rep.lines.isEmpty()) "Kode tidak terbaca" else "Hasil scan") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (rep.lines.isEmpty()) {
                    Text("Tidak ada kode aset yang terbaca di foto ini. Coba dekatkan label, tambah cahaya, atau isi kode secara manual.")
                    if (rep.diabaikan > 0) Text("${rep.diabaikan} barcode/QR dengan isi bukan kode aset diabaikan.", color = C.Muted)
                } else rep.lines.forEach { l ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(l.kode + if (l.fuzzy) "  (~ dikoreksi)" else "", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            Text(listOf(l.nama, l.catatan).filter { it.isNotEmpty() }.joinToString(" · "), color = if (l.baru) C.Danger else C.Muted, style = MaterialTheme.typography.bodyMedium)
                        }
                        l.asetId?.let { id -> TextButton(onClick = { c.report = null; c.editing = id }) { Text("Buka") } }
                    }
                }
                if (rep.lines.isNotEmpty() && rep.diabaikan > 0) Text("${rep.diabaikan} barcode/QR bukan kode aset diabaikan.", color = C.Muted, style = MaterialTheme.typography.labelSmall)
            }
        },
        confirmButton = {
            if (rep.lines.isEmpty() && rep.jpeg != null) TextButton(onClick = { c.buatDariFoto(rep.jpeg) }) { Text("Buat aset dari foto ini") }
            else TextButton(onClick = { c.report = null }) { Text("Tutup") }
        },
        dismissButton = { if (rep.lines.isEmpty()) TextButton(onClick = { c.report = null }) { Text("Tutup") } },
    )
}

@Composable
private fun ImportDialog(vm: MainViewModel, st: id.tub.dinas.aset.ImportState) {
    val r = st.report
    AlertDialog(
        onDismissRequest = { vm.aset.batalImport() },
        title = { Text("Import aset") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(r.info, color = C.Muted)
                Text("${r.total} baris dibaca", fontWeight = FontWeight.Bold)
                Text("• ${r.baru} aset baru\n• ${r.update} akan dilengkapi (hanya kolom yang kosong; data lama tidak ditimpa)\n• ${r.sama} sama, dilewati\n• ${r.ganda} ganda di dalam file, dilewati\n• ${r.foto} foto akan ditambahkan")
                Text("Foto ditambahkan ke aset yang belum punya foto. Cadangan dulu bila data penting.", color = C.Muted, style = MaterialTheme.typography.labelSmall)
            }
        },
        confirmButton = { TextButton(enabled = r.baru + r.update > 0, onClick = { vm.aset.terapkanImport() }) { Text("Impor ${r.baru + r.update}") } },
        dismissButton = { TextButton(onClick = { vm.aset.batalImport() }) { Text("Batal") } },
    )
}

@Composable
private fun ExportDialog(total: Int, terfilter: Int, terpilih: Int, onDismiss: () -> Unit, onGo: (Int, Int) -> Unit) {
    var n by remember { mutableIntStateOf(2) }
    var cakupan by remember { mutableIntStateOf(if (terpilih > 0) 2 else 0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ekspor Excel (Memo FAT)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Tiga sheet: Tagged, Untagged, Belum Ada Kode Aset.", color = C.Muted)
                Text("Cakupan", style = MaterialTheme.typography.labelSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Chip("Semua ($total)", cakupan == 0, onClick = { cakupan = 0 })
                    Chip("Terfilter ($terfilter)", cakupan == 1, onClick = { cakupan = 1 })
                    if (terpilih > 0) Chip("Terpilih ($terpilih)", cakupan == 2, onClick = { cakupan = 2 })
                }
                Text("Foto per aset", style = MaterialTheme.typography.labelSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { (1..4).forEach { k -> Chip("$k", n == k, onClick = { n = k }) } }
            }
        },
        confirmButton = { TextButton(onClick = { onGo(n, cakupan) }) { Text("Ekspor") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}
