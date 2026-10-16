package id.tub.dinas.ui.screens

import android.Manifest
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import id.tub.dinas.MainViewModel
import id.tub.dinas.aset.AsetImport
import id.tub.dinas.domain.*
import id.tub.dinas.media.EncryptedImage
import id.tub.dinas.ui.components.*
import id.tub.dinas.ui.theme.C
import id.tub.dinas.um.*
import java.io.File
import java.util.UUID

private val Ok = Color(0xFF1B8A5A)

@Composable
fun UmScreen(vm: MainViewModel) {
    val c = vm.um
    val list by c.bundles.collectAsState()
    val detail = c.detailId?.let { id -> list.firstOrNull { it.um.id == id } }
    BackHandler(enabled = c.detailId != null) { c.detailId = null }

    var askScope by remember { mutableStateOf<List<UmBundle>?>(null) }
    var req by remember { mutableStateOf<Pair<String, List<UmBundle>>?>(null) }
    fun run(uri: Uri?) { val r = req; req = null; if (uri != null && r != null) c.ekspor(uri, r.first, r.second) }
    val createXlsx = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) { run(it) }
    val createJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { run(it) }
    val importPick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) c.impor(uri) }

    Column(Modifier.fillMaxSize().background(C.Bg)) {
        if (detail == null) UmList(vm, list, onExport = { askScope = list }, onImport = { importPick.launch(arrayOf("*/*")) })
        else UmDetail(vm, detail, onExport = { askScope = listOf(detail) })
    }

    c.editUm?.let { UmDialog(vm, it) }
    c.editBonId?.let { id -> BonEditor(vm, id) }
    c.viewer?.let { (bonId, idx) -> BonFotoViewer(vm, bonId, idx) }
    askScope?.let { scope ->
        AlertDialog(
            onDismissRequest = { askScope = null },
            title = { Text("Ekspor ${if (scope.size == 1) "pengajuan" else "semua pengajuan (${scope.size})"}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Excel: rekap + rincian bon dengan foto, bisa diimpor kembali. JSON: lengkap dengan foto, untuk tukar data antar-perangkat/PC.", color = C.Muted, style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = { TextButton(onClick = { req = "xlsx" to scope; askScope = null; createXlsx.launch("Uang Muka ${todayIso()}.xlsx") }) { Text("Excel") } },
            dismissButton = {
                Row {
                    TextButton(onClick = { req = "json" to scope; askScope = null; createJson.launch("uangmuka-${todayIso()}.json") }) { Text("JSON") }
                    TextButton(onClick = { askScope = null }) { Text("Batal") }
                }
            },
        )
    }
}

@Composable
private fun ColumnScope.UmList(vm: MainViewModel, list: List<UmBundle>, onExport: () -> Unit, onImport: () -> Unit) {
    val c = vm.um
    Header("Uang Muka", onBack = { vm.pop() }) {
        HeaderAction("Impor", onImport)
        if (list.isNotEmpty()) HeaderAction("Ekspor", onExport)
    }
    Box(Modifier.weight(1f)) {
        if (list.isEmpty()) Empty("Belum ada pengajuan. Ketuk “+ Pengajuan baru” untuk mencatat uang muka, lalu tambahkan bon realisasinya lengkap dengan foto.")
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (list.isNotEmpty()) item {
                Panel {
                    Text("RINGKASAN", style = MaterialTheme.typography.labelSmall, color = C.Muted)
                    Spacer(Modifier.height(4.dp))
                    KV("Diajukan", rupiah(list.sumOf { it.um.jumlahDiajukan }))
                    KV("Sudah cair", rupiah(list.sumOf { it.um.jumlahCair }))
                    KV("Total bon", rupiah(list.sumOf { it.totalBon }))
                    val sisa = list.filter { it.um.status != UmStatus.DIAJUKAN }.sumOf { it.sisa }
                    KV(if (sisa >= 0) "Sisa (harus dikembalikan)" else "Kurang (ditagihkan)", rupiah(Math.abs(sisa)), if (sisa >= 0) Ok else C.Danger)
                }
            }
            items(list, key = { it.um.id }) { b -> UmCard(b) { c.detailId = b.um.id } }
        }
    }
    Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) { AmberButton("+ Pengajuan baru", Modifier.fillMaxWidth()) { c.pengajuanBaru() } }
}

@Composable
private fun KV(k: String, v: String, color: Color = C.Ink) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(k, color = C.Muted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(v, color = color, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun UmCard(b: UmBundle, onClick: () -> Unit) {
    val u = b.um
    Panel(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(u.judul.ifEmpty { "(tanpa judul)" }, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Chip(u.status.label, true, color = when (u.status) { UmStatus.SELESAI -> Ok; UmStatus.CAIR -> C.Ink; else -> C.Muted })
        }
        Text(listOf(u.nomor, fmtTgl(u.tglPengajuan)).filter { it.isNotEmpty() }.joinToString(" · "), color = C.Muted, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(6.dp))
        KV("Dana", rupiah(b.dana))
        KV("Bon (${b.bons.size})", rupiah(b.totalBon))
        if (b.bons.isNotEmpty() || u.status != UmStatus.DIAJUKAN) KV(if (b.sisa >= 0) "Sisa" else "Kurang", rupiah(Math.abs(b.sisa)), if (b.sisa >= 0) Ok else C.Danger)
    }
}

@Composable
private fun ColumnScope.UmDetail(vm: MainViewModel, b: UmBundle, onExport: () -> Unit) {
    val c = vm.um
    val u = b.um
    var hapus by remember { mutableStateOf(false) }
    val dinas by vm.dinasList.collectAsState()
    Header(u.judul.ifEmpty { u.nomor }, onBack = { c.detailId = null }) {
        HeaderAction("Ubah") { c.editUm = u }
        HeaderAction("Ekspor", onExport)
    }
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Panel {
            Text("PENGAJUAN", style = MaterialTheme.typography.labelSmall, color = C.Muted)
            Spacer(Modifier.height(4.dp))
            KV("No. pengajuan", u.nomor.ifEmpty { "-" })
            KV("Pemohon", u.pemohon.ifEmpty { "-" })
            KV("Tgl pengajuan", fmtTgl(u.tglPengajuan).ifEmpty { "-" })
            KV("Diajukan", rupiah(u.jumlahDiajukan))
            KV("Tgl cair", fmtTgl(u.tglCair).ifEmpty { "-" })
            KV("Dicairkan", rupiah(u.jumlahCair))
            u.dinasId?.let { id -> dinas.firstOrNull { it.id == id }?.let { KV("Dinas", it.name) } }
            if (u.catatan.isNotBlank()) Text(u.catatan, color = C.Muted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { UmStatus.entries.forEach { s -> Chip(s.label, u.status == s, onClick = { c.ubahStatus(u, s) }) } }
        }
        Panel {
            Text("REALISASI", style = MaterialTheme.typography.labelSmall, color = C.Muted)
            Spacer(Modifier.height(4.dp))
            KV("Dana uang muka", rupiah(b.dana))
            KV("Total bon (${b.bons.size})", rupiah(b.totalBon))
            KV(if (b.sisa >= 0) "Sisa (dikembalikan ke perusahaan)" else "Kurang (ditagihkan ke perusahaan)", rupiah(Math.abs(b.sisa)), if (b.sisa >= 0) Ok else C.Danger)
            val per = b.perKategori()
            if (per.isNotEmpty()) {
                Spacer(Modifier.height(8.dp)); Text("Per kategori", style = MaterialTheme.typography.labelSmall, color = C.Muted)
                per.forEach { (k, v) -> KV(k.label, rupiah(v)) }
            }
        }
        if (b.bons.isEmpty()) Text("Belum ada bon. Ketuk “+ Tambah bon”, pilih tag, isi jumlah, lalu foto bonnya.", color = C.Muted, modifier = Modifier.padding(8.dp))
        b.bons.forEach { bb -> BonRow(vm, bb) }
        SoftButton("Hapus pengajuan ini", Modifier.fillMaxWidth(), danger = true) { hapus = true }
    }
    Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) { AmberButton("+ Tambah bon", Modifier.fillMaxWidth()) { c.bonBaru(u.id) } }
    if (hapus) Confirm("Hapus pengajuan?", "Pengajuan beserta ${b.bons.size} bon dan fotonya dihapus permanen.", onConfirm = { hapus = false; c.hapusUm(u.id) }, onDismiss = { hapus = false })
}

@Composable
private fun BonRow(vm: MainViewModel, bb: BonBundle) {
    val x = bb.bon
    Panel(onClick = { vm.um.editBonId = x.id }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (bb.fotos.isNotEmpty()) EncryptedImage(vm.repo.vault, bb.fotos.first().path, Modifier.size(64.dp).itemFrame(10.dp), maxSide = 240)
            else Box(Modifier.size(64.dp).itemFrame(10.dp), contentAlignment = Alignment.Center) { Text("Tanpa foto", color = C.Muted, style = MaterialTheme.typography.labelSmall) }
            Column(Modifier.weight(1f)) {
                Text(x.kategori.label, fontWeight = FontWeight.Bold)
                Text(listOf(fmtTgl(x.tanggal), x.uraian).filter { it.isNotBlank() }.joinToString(" · "), color = C.Muted, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                if (bb.fotos.size > 1) Text("${bb.fotos.size} foto", color = C.Muted, style = MaterialTheme.typography.labelSmall)
            }
            Text(rupiah(x.jumlah), fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UmDialog(vm: MainViewModel, ex: UangMukaModel) {
    val c = vm.um
    val dinas by vm.dinasList.collectAsState()
    var nomor by remember(ex.id) { mutableStateOf(ex.nomor) }
    var judul by remember(ex.id) { mutableStateOf(ex.judul) }
    var pemohon by remember(ex.id) { mutableStateOf(ex.pemohon) }
    var tgl by remember(ex.id) { mutableStateOf(ex.tglPengajuan) }
    var diaj by remember(ex.id) { mutableStateOf(if (ex.jumlahDiajukan == 0.0) "" else ex.jumlahDiajukan.toLong().toString()) }
    var tglC by remember(ex.id) { mutableStateOf(ex.tglCair) }
    var cair by remember(ex.id) { mutableStateOf(if (ex.jumlahCair == 0.0) "" else ex.jumlahCair.toLong().toString()) }
    var cat by remember(ex.id) { mutableStateOf(ex.catatan) }
    var dinasId by remember(ex.id) { mutableStateOf(ex.dinasId) }
    AlertDialog(
        onDismissRequest = { c.editUm = null },
        title = { Text(if (ex.id.isEmpty()) "Pengajuan uang muka" else "Ubah pengajuan") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(judul, { judul = it }, "Keperluan / judul (mis. Dinas Halmahera)", singleLine = true)
                Field(nomor, { nomor = it }, "No. pengajuan", singleLine = true)
                Field(pemohon, { pemohon = it }, "Pemohon", singleLine = true)
                DateField("Tgl pengajuan", tgl, { tgl = it }, clearable = false)
                Field(diaj, { diaj = it }, "Jumlah diajukan (Rp)", singleLine = true)
                DateField("Tgl cair (kosong bila belum cair)", tglC, { tglC = it })
                Field(cair, { cair = it }, "Jumlah dicairkan (Rp)", singleLine = true)
                Field(cat, { cat = it }, "Catatan", minLines = 2)
                if (dinas.isNotEmpty()) {
                    Text("Terkait dinas", style = MaterialTheme.typography.labelSmall, color = C.Muted)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Chip("Tidak ada", dinasId == null, onClick = { dinasId = null })
                        dinas.forEach { d -> Chip(d.name, dinasId == d.id, onClick = { dinasId = d.id }) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = judul.isNotBlank(), onClick = {
                val dj = AsetImport.parseAngka(diaj) ?: 0.0; val cr = AsetImport.parseAngka(cair) ?: 0.0
                var st = ex.status
                if (cr > 0 && st == UmStatus.DIAJUKAN) st = UmStatus.CAIR
                c.simpanUm(ex.copy(nomor = nomor.trim(), judul = judul.trim(), pemohon = pemohon.trim(), tglPengajuan = tgl, jumlahDiajukan = dj,
                    tglCair = tglC, jumlahCair = cr, catatan = cat.trim(), dinasId = dinasId, status = st))
            }) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = { c.editUm = null }) { Text("Batal") } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BonEditor(vm: MainViewModel, bonId: String) {
    val c = vm.um
    val ctx = LocalContext.current
    val all by c.bundles.collectAsState()
    val bb = all.flatMap { it.bons }.firstOrNull { it.bon.id == bonId }
    if (bb == null) return
    val b = bb.bon
    var kat by remember(bonId) { mutableStateOf(b.kategori) }
    var tgl by remember(bonId) { mutableStateOf(b.tanggal) }
    var ur by remember(bonId) { mutableStateOf(b.uraian) }
    var jml by remember(bonId) { mutableStateOf(if (b.jumlah == 0.0) "" else b.jumlah.toLong().toString()) }
    var hapus by remember { mutableStateOf(false) }
    val tglOk = tgl.isNotEmpty()

    var camPath by rememberSaveable { mutableStateOf<String?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val p = camPath; camPath = null
        if (p != null) { val f = File(p); if (ok) c.tambahFoto(bonId, listOf(Uri.fromFile(f)), f) else f.delete() }
    }
    fun launchCamera() {
        val f = File(File(ctx.cacheDir, "kamera").apply { mkdirs() }, "bon_${UUID.randomUUID()}.jpg")
        camPath = f.absolutePath
        camera.launch(FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f))
    }
    val camPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) launchCamera() else vm.toast("Izin kamera ditolak. Pakai Galeri untuk memilih foto bon.")
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(20)) { uris -> if (uris.isNotEmpty()) c.tambahFoto(bonId, uris) }

    fun simpan() = c.simpanBon(b.copy(kategori = kat, tanggal = tgl.ifEmpty { b.tanggal }, uraian = ur.trim(), jumlah = AsetImport.parseAngka(jml) ?: 0.0))

    Dialog(onDismissRequest = { c.tutupBon(bonId) }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        BackHandler { c.tutupBon(bonId) }
        Column(Modifier.fillMaxSize().background(C.Bg)) {
            Header("Bon realisasi", onBack = { c.tutupBon(bonId) }) { HeaderAction("Simpan") { if (tglOk) simpan() } }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp).navigationBarsPadding().imePadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Panel {
                    Text("TAG", style = MaterialTheme.typography.labelSmall, color = C.Muted)
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        BonKategori.entries.forEach { k -> Chip(k.label, kat == k, onClick = { kat = k }) }
                    }
                }
                DateField("Tanggal bon", tgl, { tgl = it }, clearable = false)
                Field(jml, { jml = it }, "Jumlah (Rp)", singleLine = true)
                Field(ur, { ur = it }, "Uraian (mis. Taksi Tobelo–Ternate, Hotel 2 malam)", minLines = 2)
                Panel {
                    Text("FOTO BON (${bb.fotos.size})", style = MaterialTheme.typography.labelSmall, color = C.Muted)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconTextButton(id.tub.dinas.R.drawable.ic_kamera, "Foto bon") {
                            if (androidx.core.content.ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) launchCamera()
                            else camPerm.launch(Manifest.permission.CAMERA)
                        }
                        IconTextButton(id.tub.dinas.R.drawable.ic_galeri, "Galeri", primary = false) { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                    }
                    bb.fotos.forEachIndexed { i, f ->
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            EncryptedImage(vm.repo.vault, f.path, Modifier.size(84.dp).itemFrame(10.dp).clickable { c.viewer = bonId to i }, maxSide = 300)
                            Column(Modifier.weight(1f)) {
                                Text("Foto ${i + 1}", style = MaterialTheme.typography.bodyMedium)
                                Row {
                                    TextButton(onClick = { c.viewer = bonId to i }) { Text("Lihat") }
                                    TextButton(onClick = { c.hapusFoto(f.id) }) { Text("Hapus", color = C.Danger) }
                                }
                            }
                        }
                    }
                }
                SoftButton("Hapus bon ini", Modifier.fillMaxWidth(), danger = true) { hapus = true }
            }
            Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) { AmberButton("Simpan bon", Modifier.fillMaxWidth(), enabled = tglOk) { simpan() } }
        }
        if (hapus) Confirm("Hapus bon?", "Bon dan ${bb.fotos.size} fotonya dihapus permanen.", onConfirm = { hapus = false; c.hapusBon(bonId) }, onDismiss = { hapus = false })
    }
}

@Composable
private fun BonFotoViewer(vm: MainViewModel, bonId: String, start: Int) {
    val c = vm.um
    val all by c.bundles.collectAsState()
    val fotos = all.flatMap { it.bons }.firstOrNull { it.bon.id == bonId }?.fotos.orEmpty()
    var i by remember(bonId, start) { mutableIntStateOf(start) }
    if (fotos.isEmpty()) { c.viewer = null; return }
    val idx = i.coerceIn(0, fotos.lastIndex)
    Dialog(onDismissRequest = { c.viewer = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            EncryptedImage(vm.repo.vault, fotos[idx].path, Modifier.fillMaxSize(), maxSide = 1800, contentScale = ContentScale.Fit)
            Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(enabled = idx > 0, onClick = { i = idx - 1 }) { Text("‹ Sebelumnya", color = Color.White) }
                Text("${idx + 1} / ${fotos.size}", color = Color.White)
                TextButton(enabled = idx < fotos.lastIndex, onClick = { i = idx + 1 }) { Text("Berikutnya ›", color = Color.White) }
            }
            TextButton(onClick = { c.viewer = null }, modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding()) { Text("Tutup ✕", color = Color.White) }
        }
    }
}
