package id.tub.dinas.ui.screens

import id.tub.dinas.ui.components.Text
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
    var exportFmt by remember { mutableStateOf<String?>(null) }          // xlsx | pdf | html | label (null = dialog tertutup)
    var exportReq by remember { mutableStateOf<Triple<String, Int, List<AsetModel>>?>(null) }
    var resetAsk by remember { mutableStateOf(false) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(50)) { uris -> if (uris.isNotEmpty()) c.galeriUris = uris }
    val importPick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) c.siapkanImport(uri) }
    fun jalankan(uri: android.net.Uri?) {
        val req = exportReq; exportReq = null
        if (uri == null || req == null) return
        if (req.first == "xlsx") c.exportMemo(uri, req.second, req.third) else c.exportLain(uri, req.first, req.second, req.third)
    }
    val createXlsx = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) { jalankan(it) }
    val createPdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { jalankan(it) }
    val createJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { jalankan(it) }
    val createHtml = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/html")) { jalankan(it) }

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
    val doExport = { exportFmt = "xlsx" }
    val doLabels = { exportFmt = "label" }

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
        "ekspor" -> { exportFmt = "xlsx"; aksi = null }
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

    exportFmt?.let { fmt0 ->
        ExportDialog(
            awal = fmt0, total = list.size, terfilter = filtered.size, terpilih = sel.size,
            onDismiss = { exportFmt = null },
            onGo = { fmt, n, cakupan ->
                exportFmt = null
                val scope = when (cakupan) { 1 -> filtered; 2 -> list.filter { it.id in sel }; else -> list }
                exportReq = Triple(fmt, n, scope)
                val tgl = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                when (fmt) {
                    "xlsx" -> createXlsx.launch("Physical Check $tgl.xlsx")
                    "pdf" -> createPdf.launch("Laporan Aset $tgl.pdf")
                    "html" -> createHtml.launch("Laporan Aset $tgl.html")
                    "json" -> createJson.launch("asetscan-backup-$tgl.json")
                    else -> createPdf.launch("Label QR Aset $tgl.pdf")
                }
            },
        )
    }
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !st.timpa, onClick = { if (st.timpa) vm.aset.ubahMode(false) }, label = { Text("Lengkapi") })
                    FilterChip(selected = st.timpa, onClick = { if (!st.timpa) vm.aset.ubahMode(true) }, label = { Text("Perbarui") })
                }
                Text(if (st.timpa) "Perbarui: isi sel Excel yang tidak kosong menggantikan data di aplikasi (mis. PT Pemilik). Sel kosong tidak menghapus. Perubahan tiap kolom ditampilkan di bawah." else "Lengkapi: hanya mengisi kolom yang masih kosong; data lama tidak ditimpa.", color = C.Muted, style = MaterialTheme.typography.labelSmall)
                Text("• ${r.baru} aset baru\n• ${r.update} akan ${if (st.timpa) "diperbarui" else "dilengkapi"}\n• ${r.sama} sama, dilewati\n• ${r.ganda} ganda di dalam file, dilewati\n• ${r.foto} foto akan ditambahkan")
                if (r.perubahan.isNotEmpty()) {
                    Text("${r.totalPerubahan} perubahan terdeteksi", fontWeight = FontWeight.Bold)
                    Column(Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        r.perubahan.forEach { Text(it, fontSize = 12.sp) }
                        if (r.totalPerubahan > r.perubahan.size) Text("… dan ${r.totalPerubahan - r.perubahan.size} lainnya", color = C.Muted, fontSize = 12.sp)
                    }
                }
                Text("Foto ditambahkan ke aset yang belum punya foto. Cadangan dulu bila data penting.", color = C.Muted, style = MaterialTheme.typography.labelSmall)
            }
        },
        confirmButton = { TextButton(enabled = r.baru + r.update > 0, onClick = { vm.aset.terapkanImport() }) { Text("Impor ${r.baru + r.update}") } },
        dismissButton = { TextButton(onClick = { vm.aset.batalImport() }) { Text("Batal") } },
    )
}

@Composable
private fun ExportDialog(awal: String, total: Int, terfilter: Int, terpilih: Int, onDismiss: () -> Unit, onGo: (String, Int, Int) -> Unit) {
    var fmt by remember { mutableStateOf(awal) }
    var n by remember { mutableIntStateOf(2) }
    var cakupan by remember { mutableIntStateOf(if (terpilih > 0) 2 else 0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (fmt == "label") "Cetak label QR" else "Ekspor laporan") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Format", style = MaterialTheme.typography.labelSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Chip("Excel", fmt == "xlsx", onClick = { fmt = "xlsx" })
                    Chip("PDF", fmt == "pdf", onClick = { fmt = "pdf" })
                    Chip("HTML", fmt == "html", onClick = { fmt = "html" })
                    Chip("JSON", fmt == "json", onClick = { fmt = "json" })
                    Chip("Label QR", fmt == "label", onClick = { fmt = "label" })
                }
                Text(when (fmt) {
                    "xlsx" -> "Memo FAT: tiga sheet Tagged, Untagged, Belum Ada Kode Aset."
                    "pdf" -> "Daftar aset per baris dengan foto, siap cetak."
                    "json" -> "Cadangan format AsetScan v2 lengkap dengan foto & keterangan; bisa diimpor ke Aset Scan PC dan sebaliknya. Pilih 0 foto untuk berkas kecil."
                    "html" -> "Satu berkas mandiri dengan pencarian dan zoom foto; bisa dibuka di browser mana pun."
                    else -> "PDF stiker label A4 (3 × 8) berisi QR kode aset. Hanya aset yang punya kode."
                }, color = C.Muted, style = MaterialTheme.typography.bodyMedium)
                Text("Cakupan", style = MaterialTheme.typography.labelSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Chip("Semua ($total)", cakupan == 0, onClick = { cakupan = 0 })
                    Chip("Terfilter ($terfilter)", cakupan == 1, onClick = { cakupan = 1 })
                    if (terpilih > 0) Chip("Terpilih ($terpilih)", cakupan == 2, onClick = { cakupan = 2 })
                }
                if (fmt != "label") {
                    Text("Foto per aset", style = MaterialTheme.typography.labelSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { (if (fmt == "xlsx") 1..4 else 0..4).forEach { k -> Chip("$k", n == k, onClick = { n = k }) } }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onGo(fmt, if (fmt == "xlsx") n.coerceAtLeast(1) else n, cakupan) }) { Text(if (fmt == "label") "Buat label" else "Ekspor") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}
