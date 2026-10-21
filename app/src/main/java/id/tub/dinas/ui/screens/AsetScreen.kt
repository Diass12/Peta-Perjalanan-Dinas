package id.tub.dinas.ui.screens

import id.tub.dinas.ui.components.Chip
import id.tub.dinas.ui.components.Field
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import id.tub.dinas.domain.KodeAset
import id.tub.dinas.domain.AsetFotoModel
import androidx.compose.foundation.border
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
import id.tub.dinas.export.MemoFat
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
    var clearAsk by remember { mutableStateOf(false) }
    var clearBackup by remember { mutableStateOf(true) }
    var clearMaster by remember { mutableStateOf(true) }
    val createClearBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> if (uri != null) c.hapusSemua(uri, clearMaster) }
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
    val doBackup = { exportReq = Triple("json", 4, list); createJson.launch("AsetScan backup ${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())}.json") }
    val doExport = { exportFmt = "xlsx" }
    val doLabels = { exportFmt = "label" }

    Column(Modifier.fillMaxSize().background(A.Bg)) {
        Box(Modifier.weight(1f)) {
            when (c.tab) {
                0 -> AHome(vm, list, fotos, scanAudit, goFilter)
                1 -> AAssets(vm, list, filtered, fotos, doGallery, doExport, doLabels)
                2 -> AAudit(vm, list, fotos, scanAudit) { resetAsk = true }
                else -> AMenu(vm, list.size, doImport, doExport, { masterOpen = true }, doGallery, doLabels, doBackup, doImport, { clearAsk = true }) { c.tab = 1; c.selMode = true }
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
            onOpsi = { c.opsiXlsx = it },
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
    if (clearAsk) ClearDialog(vm, list, fotos, clearBackup, clearMaster, { clearBackup = it }, { clearMaster = it }, { clearAsk = false }) { backup ->
        clearAsk = false
        if (backup) createClearBackup.launch("AsetScan backup sebelum hapus ${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())}.json") else c.hapusSemua(null, clearMaster)
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
private fun ClearDialog(vm: MainViewModel, list: List<AsetModel>, fotos: Map<String, List<AsetFotoModel>>, backup: Boolean, master: Boolean,
                        onBackup: (Boolean) -> Unit, onMaster: (Boolean) -> Unit, onDismiss: () -> Unit, onGo: (Boolean) -> Unit) {
    var teks by remember { mutableStateOf("") }
    val dup = list.map { KodeAset.norm(it.code) }.filter { it.isNotEmpty() }.groupingBy { it }.eachCount().count { it.value > 1 }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = A.Card,
        title = { Text("🗑 Hapus database", color = A.Ink) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Semua aset, foto, video, dan dokumen aset akan dihapus dari aplikasi ini. Tidak bisa dibatalkan (kecuali dari cadangan).", color = A.Ink)
                Text("Saat ini: ${list.size} aset · kode ganda: $dup · tanpa foto: ${list.count { fotos[it.id].isNullOrEmpty() }} · tanpa kode: ${list.count { it.code.isBlank() }}",
                    color = Color(0xFF7A5200), modifier = Modifier.fillMaxWidth().background(Color(0xFFFEF3C7), RoundedCornerShape(12.dp)).padding(10.dp))
                CekBaris("Unduh cadangan JSON data saat ini dulu (disarankan)", backup, onBackup)
                CekBaris("Kembalikan daftar master (pilihan dropdown) ke bawaan", master, onMaster)
                Field(teks, { teks = it }, "Ketik HAPUS untuk melanjutkan", singleLine = true)
            }
        },
        confirmButton = { TextButton(enabled = teks.trim().equals("HAPUS", true), onClick = { onGo(backup) }) { Text("Hapus", color = A.Red) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

private fun nilaiRingkas(a: AsetModel) = listOf("Kode" to a.code, "Nama" to a.name, "Dept" to a.dept, "Lokasi" to a.location, "PT" to a.owner, "Kondisi" to a.cond).filter { it.second.isNotBlank() }

@Composable
private fun ImportDialog(vm: MainViewModel, st: id.tub.dinas.aset.ImportState) {
    val r = st.report
    val daftar = r.items
    var terpilih by remember(st) { mutableStateOf(daftar.filter { it.status != "sama" }.map { it.key }.toSet()) }
    var filter by remember { mutableStateOf("ubah") }
    val tampil = daftar.filter { filter == "semua" || it.status == filter }
    androidx.compose.ui.window.Dialog(onDismissRequest = { vm.aset.batalImport() }, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(A.Bg).statusBarsPadding().navigationBarsPadding()) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Import aset — periksa perubahan", color = A.Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("${r.info}. ${r.total} baris dibaca, ${r.ganda} ganda di file dilewati.", color = A.Muted, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Perbarui", st.timpa) { if (!st.timpa) vm.aset.ubahMode(true) }
                    Chip("Lengkapi", !st.timpa) { if (st.timpa) vm.aset.ubahMode(false) }
                }
                Text(if (st.timpa) "Perbarui: nilai tak kosong di file menggantikan database. Sel kosong tidak menghapus." else "Lengkapi: hanya mengisi kolom database yang kosong.", color = A.Muted, fontSize = 11.sp)
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Chip("Berubah ${r.update}", filter == "ubah") { filter = "ubah" }
                    Chip("Baru ${r.baru}", filter == "baru") { filter = "baru" }
                    Chip("Tidak berubah ${r.sama}", filter == "sama") { filter = "sama" }
                    Chip("Semua ${daftar.size}", filter == "semua") { filter = "semua" }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { terpilih = terpilih + tampil.map { it.key } }) { Text("Pilih yang tampil") }
                    TextButton(onClick = { terpilih = terpilih - tampil.map { it.key }.toSet() }) { Text("Kosongkan") }
                    Spacer(Modifier.weight(1f))
                    Text("${terpilih.size} dipilih", color = A.Accent, fontWeight = FontWeight.Bold)
                }
            }
            LazyColumn(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (tampil.isEmpty()) item { Text("Tidak ada baris di kategori ini.", color = A.Muted, modifier = Modifier.padding(16.dp)) }
                items(tampil, key = { it.key }) { it ->
                    val on = it.key in terpilih
                    Column(Modifier.fillMaxWidth().background(A.Card, RoundedCornerShape(14.dp)).border(1.dp, if (on) A.Accent else A.Line, RoundedCornerShape(14.dp)).padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = on, onCheckedChange = { c -> terpilih = if (c) terpilih + it.key else terpilih - it.key })
                            Column(Modifier.weight(1f)) {
                                Text(it.label, color = A.Ink, fontWeight = FontWeight.Bold, maxLines = 1)
                                if (it.baru.name.isNotBlank() && it.baru.name != it.label) Text(it.baru.name, color = A.Muted, fontSize = 12.sp, maxLines = 1)
                            }
                            Text(when (it.status) { "baru" -> "BARU"; "ubah" -> "BERUBAH"; else -> "SAMA" }, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                color = when (it.status) { "baru" -> A.Green; "ubah" -> A.Amber; else -> A.Muted })
                        }
                        when (it.status) {
                            "ubah" -> {
                                it.diff.forEach { (k, lama, baru) ->
                                    Column(Modifier.padding(start = 12.dp, top = 6.dp)) {
                                        Text(k, color = A.Muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                        Text("Database : ${lama.ifBlank { "(kosong)" }}", color = A.Ink, fontSize = 13.sp)
                                        Text("Import   : ${baru.ifBlank { "(kosong)" }}", color = A.Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                if (it.tambahFoto > 0) Text("+ ${it.tambahFoto} foto dari file", color = A.Muted, fontSize = 12.sp, modifier = Modifier.padding(start = 12.dp, top = 6.dp))
                            }
                            "baru" -> Column(Modifier.padding(start = 12.dp, top = 6.dp)) {
                                Text("Import   :", color = A.Muted, fontSize = 11.sp)
                                Text(nilaiRingkas(it.baru).joinToString(" · ") { (k, v) -> "$k: $v" }, color = A.Accent, fontSize = 13.sp)
                            }
                            else -> Text("Database = Import", color = A.Muted, fontSize = 12.sp, modifier = Modifier.padding(start = 12.dp, top = 4.dp))
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().background(A.Card).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { vm.aset.batalImport() }, Modifier.weight(1f)) { Text("Batal") }
                Button(onClick = { vm.aset.terapkanImport(terpilih) }, Modifier.weight(2f), enabled = terpilih.isNotEmpty()) { Text("Konfirmasi (${terpilih.size})") }
            }
        }
    }
}

@Composable
private fun ExportDialog(awal: String, total: Int, terfilter: Int, terpilih: Int, onDismiss: () -> Unit, onOpsi: (MemoFat.Opsi) -> Unit, onGo: (String, Int, Int) -> Unit) {
    var fmt by remember { mutableStateOf(awal) }
    var n by remember { mutableIntStateOf(2) }
    var cakupan by remember { mutableIntStateOf(if (terpilih > 0) 2 else 0) }
    var o by remember { mutableStateOf(MemoFat.Opsi()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (fmt == "label") "Cetak label QR" else "Ekspor laporan") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                if (fmt == "xlsx") {
                    Text("Sheet yang diekspor", style = MaterialTheme.typography.labelSmall)
                    CekBaris("Tagged", o.tagged) { o = MemoFat.Opsi(it, o.untagged, o.tanpaKode, o.foto, o.barcode, o.remarks, o.nbv, o.pemilik, o.asli) }
                    CekBaris("Untagged", o.untagged) { o = MemoFat.Opsi(o.tagged, it, o.tanpaKode, o.foto, o.barcode, o.remarks, o.nbv, o.pemilik, o.asli) }
                    CekBaris("Belum ada kode aset", o.tanpaKode) { o = MemoFat.Opsi(o.tagged, o.untagged, it, o.foto, o.barcode, o.remarks, o.nbv, o.pemilik, o.asli) }
                    Text("Data yang disertakan (kolom inti selalu ada)", style = MaterialTheme.typography.labelSmall)
                    CekBaris("Foto", o.foto) { o = MemoFat.Opsi(o.tagged, o.untagged, o.tanpaKode, it, o.barcode, o.remarks, o.nbv, o.pemilik, o.asli) }
                    CekBaris("Status barcode", o.barcode) { o = MemoFat.Opsi(o.tagged, o.untagged, o.tanpaKode, o.foto, it, o.remarks, o.nbv, o.pemilik, o.asli) }
                    CekBaris("Keterangan (remarks)", o.remarks) { o = MemoFat.Opsi(o.tagged, o.untagged, o.tanpaKode, o.foto, o.barcode, it, o.nbv, o.pemilik, o.asli) }
                    CekBaris("Blok NBV (nilai buku, harga, dll.)", o.nbv) { o = MemoFat.Opsi(o.tagged, o.untagged, o.tanpaKode, o.foto, o.barcode, o.remarks, it, o.pemilik, o.asli) }
                    CekBaris("PT pemilik & kepemilikan", o.pemilik) { o = MemoFat.Opsi(o.tagged, o.untagged, o.tanpaKode, o.foto, o.barcode, o.remarks, o.nbv, it, o.asli) }
                    if (o.foto) {
                        Text("Mutu foto", style = MaterialTheme.typography.labelSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Chip("Ringan (900px)", !o.asli, onClick = { o = MemoFat.Opsi(o.tagged, o.untagged, o.tanpaKode, o.foto, o.barcode, o.remarks, o.nbv, o.pemilik, false) })
                            Chip("Asli", o.asli, onClick = { o = MemoFat.Opsi(o.tagged, o.untagged, o.tanpaKode, o.foto, o.barcode, o.remarks, o.nbv, o.pemilik, true) })
                        }
                    }
                }
                if (fmt != "label") {
                    Text("Foto per aset", style = MaterialTheme.typography.labelSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { (if (fmt == "xlsx") 1..4 else 0..4).forEach { k -> Chip("$k", n == k, onClick = { n = k }) } }
                }
            }
        },
        confirmButton = { TextButton(enabled = fmt != "xlsx" || o.tagged || o.untagged || o.tanpaKode, onClick = { onOpsi(o); onGo(fmt, if (fmt == "xlsx") n.coerceAtLeast(1) else n, cakupan) }) { Text(if (fmt == "label") "Buat label" else "Ekspor") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

@Composable
private fun CekBaris(label: String, on: Boolean, ubah: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { ubah(!on) }, verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.runtime.CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides androidx.compose.ui.unit.Dp.Unspecified) {
            Checkbox(checked = on, onCheckedChange = ubah)
        }
        Spacer(Modifier.width(8.dp))
        Text(label, Modifier.padding(vertical = 6.dp))
    }
}
