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
fun AsetScreen(vm: MainViewModel, embedded: Boolean = false) {
    val c = vm.aset
    val list by c.register.collectAsState()
    val fotos by c.fotos.collectAsState()
    val ctx = LocalContext.current
    var scanMenu by remember { mutableStateOf(false) }
    var exportDialog by remember { mutableStateOf(false) }
    var exportReq by remember { mutableStateOf<Pair<Int, List<AsetModel>>?>(null) }
    var resetAsk by remember { mutableStateOf(false) }
    var camPath by rememberSaveable { mutableStateOf<String?>(null) }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val p = camPath; camPath = null
        if (p != null) { val f = File(p); if (ok) c.scan(android.net.Uri.fromFile(f), f) else f.delete() }
    }
    fun launchCamera() {
        val dir = File(ctx.cacheDir, "kamera").apply { mkdirs() }
        val f = File(dir, "scan_${UUID.randomUUID()}.jpg")
        camPath = f.absolutePath
        camera.launch(FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f))
    }
    val camPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) launchCamera() else vm.toast("Izin kamera ditolak. Pakai Galeri untuk memilih foto.")
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) c.scan(uri) }
    val importPick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) c.siapkanImport(uri) }
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) { uri ->
        val req = exportReq; exportReq = null
        if (uri != null && req != null) c.exportMemo(uri, req.first, req.second)
    }

    val filtered = remember(list, c.query, c.kategori, c.barcode, c.auditFilter, c.peringatan, fotos) { c.filtered(list) }

    var menu by remember { mutableStateOf(false) }
    var masterOpen by remember { mutableStateOf(false) }
    var aksi by remember { mutableStateOf<String?>(null) }      // dialog aksi massal yang sedang terbuka
    val sel = c.selected
    Column(Modifier.fillMaxSize().background(C.Bg)) {
        if (!embedded) Header("Aset", onBack = { vm.pop() })
        // satu baris atas: tab + satu menu aksi
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Chip("Daftar (${list.size})", c.tab == 0, onClick = { c.tab = 0 })
            Chip("Audit", c.tab == 1, onClick = { c.tab = 1 })
            Chip("Ringkasan", c.tab == 2, onClick = { c.tab = 2 })
            Spacer(Modifier.weight(1f))
            Box {
                Text("⋯", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = if (id.tub.dinas.ui.theme.ThemeState.sky) Color.White else C.Ink,
                    modifier = Modifier.clickable { menu = true }.padding(horizontal = 10.dp))
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem({ Text("Pilih banyak aset") }, { menu = false; c.selMode = true; c.tab = 0 })
                    DropdownMenuItem({ Text("Impor Excel / cadangan") }, { menu = false; importPick.launch(arrayOf("*/*")) })
                    DropdownMenuItem({ Text("Ekspor…") }, { menu = false; exportDialog = true })
                    DropdownMenuItem({ Text("Data Master") }, { menu = false; masterOpen = true })
                    DropdownMenuItem({ Text("Hitung ulang status barcode") }, { menu = false; c.hitungUlang() })
                    DropdownMenuItem({ Text("OCR ulang semua foto") }, { menu = false; c.ocrSemua() })
                    DropdownMenuItem({ Text("Label QR (semua berkode)") }, { menu = false; aksi = "label-semua" })
                    DropdownMenuItem({ Text("Sesi audit baru") }, { menu = false; resetAsk = true })
                }
            }
        }
        Box(Modifier.weight(1f)) {
            when (c.tab) {
                0 -> DaftarTab(vm, list, filtered, fotos)
                1 -> AuditTab(vm, list, fotos) { resetAsk = true }
                else -> AsetRingkasanTab(vm, list, fotos) { kat, per, bc, st ->
                    c.kategori = kat; c.peringatan = per; c.barcode = bc; c.auditFilter = st; c.query = ""; c.tab = 0
                }
            }
        }
        // satu bar aksi di bawah
        if (c.selMode) SelectionBar(vm, list, filtered) { aksi = it }
        else Row(Modifier.fillMaxWidth().then(if (embedded) Modifier else Modifier.navigationBarsPadding()).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val onSky = id.tub.dinas.ui.theme.ThemeState.sky
            Switch(c.auditMode, { c.auditMode = it })
            Text(if (c.auditMode) "Audit\n${list.count { it.state == AuditState.FOUND }}/${list.size}" else "Audit\nmati", fontSize = 11.sp, lineHeight = 13.sp, color = if (onSky) Color.White else C.Muted)
            Spacer(Modifier.weight(1f))
            SoftButton("+ Baru") { c.baru() }
            AmberButton("Scan kode") { scanMenu = true }
        }
    }

    if (masterOpen) MasterDialog(vm) { masterOpen = false }
    when (aksi) {
        "tabel" -> BulkGridDialog(vm, list.filter { it.id in sel }.map { it.id }) { aksi = null }
        "massal" -> BulkEditDialog(vm, sel) { aksi = null }
        "foto" -> BulkFotoDialog(vm, list.filter { it.id in sel }.map { it.id }) { aksi = null }
        "status" -> BulkStatusDialog(vm, sel) { aksi = null }
        "hapus" -> Confirm("Hapus ${sel.size} aset?", "Aset terpilih dan semua fotonya dihapus permanen.", onConfirm = { c.bulkHapus(sel) }, onDismiss = { aksi = null })
        "ekspor" -> { exportDialog = true; aksi = null }
        else -> {}
    }
    c.sibuk?.let { msg ->
        AlertDialog(onDismissRequest = {}, title = { Text("Sedang diproses") }, text = { Text(msg) },
            confirmButton = { TextButton(onClick = { c.berhenti = true }) { Text("Berhenti") } })
    }

    if (scanMenu) AlertDialog(
        onDismissRequest = { scanMenu = false },
        title = { Text("Scan kode aset") },
        text = { Text("Arahkan ke stiker atau label kode aset (QR, barcode, atau teks TUBxx######), lalu ambil foto. Satu foto boleh memuat beberapa label.") },
        confirmButton = {
            TextButton(onClick = {
                scanMenu = false
                if (androidx.core.content.ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) launchCamera()
                else camPerm.launch(Manifest.permission.CAMERA)
            }) { Text("Kamera") }
        },
        dismissButton = { TextButton(onClick = { scanMenu = false; gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text("Galeri") } },
    )

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
private fun DaftarTab(vm: MainViewModel, all: List<AsetModel>, shown: List<AsetModel>, fotos: Map<String, List<AsetFotoModel>>) {
    val c = vm.aset
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Field(c.query, { c.query = it }, "Cari kode, nama, lokasi, dept…", singleLine = true)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item { Chip("Semua kategori", c.kategori == null, onClick = { c.kategori = null }) }
                items(KodeAset.KATEGORI) { k -> Chip(KodeAset.labelKategori(k), c.kategori == k, onClick = { c.kategori = if (c.kategori == k) null else k }) }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(StatusBarcode.entries) { b -> Chip(b.label, c.barcode == b, onClick = { c.barcode = if (c.barcode == b) null else b }) }
                items(AuditState.entries) { s -> Chip(s.label, c.auditFilter == s, onClick = { c.auditFilter = if (c.auditFilter == s) null else s }) }
                items(listOf("nofoto" to "Tanpa foto", "nokode" to "Tanpa kode", "dup" to "Kode ganda")) { (k, l) ->
                    Chip(l, c.peringatan == k, onClick = { c.peringatan = if (c.peringatan == k) null else k })
                }
            }
            Text("${shown.size} dari ${all.size} aset", color = if (id.tub.dinas.ui.theme.ThemeState.sky) Color.White else C.Muted, style = MaterialTheme.typography.labelSmall)
        }
        if (all.isEmpty()) Empty("Belum ada aset. Ketuk Impor untuk memuat Excel Memo FAT atau cadangan AsetScan, atau Scan kode aset untuk mulai.")
        var limit by remember(shown.size, c.query, c.kategori, c.barcode, c.auditFilter, c.peringatan) { mutableIntStateOf(60) }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(shown.take(limit), key = { it.id }) { a -> AsetCard(vm, a, fotos[a.id].orEmpty()) }
            if (shown.size > limit) item { SoftButton("Tampilkan lebih banyak (${shown.size - limit})", Modifier.fillMaxWidth()) { limit += 60 } }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AsetCard(vm: MainViewModel, a: AsetModel, fotos: List<AsetFotoModel>) {
    val c = vm.aset
    Panel(Modifier.combinedClickable(
        onClick = { if (c.selMode) c.toggleSel(a.id) else c.editing = a.id },
        onLongClick = { if (c.selMode) c.toggleSel(a.id) else c.mulaiPilih(a.id) },
    )) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (c.selMode) Checkbox(a.id in c.selected, { c.toggleSel(a.id) })
            val cover = fotos.firstOrNull()
            if (cover != null) EncryptedImage(vm.repo.vault, cover.path, Modifier.size(64.dp).itemFrame(10.dp), maxSide = 200)
            Column(Modifier.weight(1f)) {
                Text(a.code.ifEmpty { "(tanpa kode)" }, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(a.name.ifEmpty { "(tanpa nama)" }, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                val sub = listOf(a.dept, a.location, a.user).filter { it.isNotEmpty() }.joinToString(" · ")
                if (sub.isNotEmpty()) Text(sub, color = C.Muted, style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    BarcodeTag(a.barcode)
                    AuditTag(a.state)
                    if (a.tenure == "lease") Chip("Sewa", false)
                }
            }
        }
    }
}

@Composable
fun BarcodeTag(b: StatusBarcode?) {
    val (fg, bg) = when (b) {
        StatusBarcode.TAGGED -> Color(0xFF14532D) to Color(0xFFC6E0B4)
        StatusBarcode.UNTAGGED -> Color(0xFF7A5200) to Color(0xFFFFE699)
        else -> Color(0xFF444444) to Color(0xFFD9D9D9)
    }
    Text(b?.label ?: "Non Aset", color = fg, fontSize = 11.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.background(bg, androidx.compose.foundation.shape.RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp))
}

@Composable
fun AuditTag(s: AuditState) {
    val (fg, bg) = when (s) {
        AuditState.FOUND -> Color(0xFF0F766E) to Color(0xFFD8F0EC)
        AuditState.MISSING -> Color(0xFFB3261E) to Color(0xFFF8D7D4)
        AuditState.DAMAGED -> Color(0xFFB45309) to Color(0xFFFBEBD9)
        AuditState.PENDING -> Color(0xFF5B6B78) to Color(0xFFECE9E0)
    }
    Text("[${s.label.uppercase()}]", color = fg, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold,
        modifier = Modifier.background(bg, androidx.compose.foundation.shape.RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp))
}

@Composable
private fun AuditTab(vm: MainViewModel, list: List<AsetModel>, fotos: Map<String, List<AsetFotoModel>>, onReset: () -> Unit) {
    val c = vm.aset
    var sel by remember { mutableStateOf(AuditState.PENDING) }
    val counts = AuditState.entries.associateWith { s -> list.count { it.state == s } }
    val done = list.size - (counts[AuditState.PENDING] ?: 0)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Panel {
                Text("Progres audit", style = MaterialTheme.typography.titleMedium)
                Text("$done dari ${list.size} aset sudah diperiksa", color = C.Muted)
                Spacer(Modifier.height(6.dp))
                SegBar(if (list.isEmpty()) 0f else done.toFloat() / list.size, Color(0xFF34F5A5))
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AuditState.entries.forEach { s -> Chip("${s.label} ${counts[s]}", sel == s, onClick = { sel = s }) }
                }
                Spacer(Modifier.height(8.dp))
                SoftButton("Sesi audit baru", Modifier.fillMaxWidth(), danger = true) { onReset() }
            }
        }
        val rows = list.filter { it.state == sel }
        if (rows.isEmpty()) item { Empty("Tidak ada aset berstatus ${sel.label}.") }
        items(rows.take(200), key = { it.id }) { a ->
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val cover = fotos[a.id]?.firstOrNull()
                    if (cover != null) EncryptedImage(vm.repo.vault, cover.path, Modifier.size(48.dp).itemFrame(8.dp), maxSide = 150)
                    Column(Modifier.weight(1f).clickable { c.editing = a.id }) {
                        Text(a.code.ifEmpty { "(tanpa kode)" }, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        Text(a.name.ifEmpty { "(tanpa nama)" }, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    AuditState.entries.filter { it != a.state }.forEach { s ->
                        TextButton(onClick = { c.setState(a, s) }) { Text(s.label, fontSize = 12.sp) }
                    }
                }
            }
        }
        if (rows.size > 200) item { Text("Menampilkan 200 dari ${rows.size}. Gunakan filter di tab Daftar.", color = C.Muted) }
    }
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
