package id.tub.dinas.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import id.tub.dinas.MainViewModel
import id.tub.dinas.domain.*
import id.tub.dinas.media.EncryptedImage
import id.tub.dinas.ui.components.*
import id.tub.dinas.ui.theme.C
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/** Formulir aset layar penuh. Aset baru baru tersimpan setelah “Simpan”; foto hanya bisa ditambah setelah aset tersimpan. */
@Composable
fun AsetEditor(vm: MainViewModel, asetId: String) {
    val c = vm.aset
    val list by c.register.collectAsState()
    val fotosAll by c.fotos.collectAsState()
    val existing = list.firstOrNull { it.id == asetId }
    val isSaved = existing != null
    val base = existing ?: c.pendingNew?.takeIf { it.id == asetId }
    if (base == null) { // aset sudah dihapus
        LaunchedEffect(Unit) { c.editing = null }
        return
    }
    var m by remember(asetId) { mutableStateOf(base) }
    var nbvTxt by remember(asetId) { mutableStateOf(base.nbv?.let { fmtNum(it) } ?: "") }
    var hargaTxt by remember(asetId) { mutableStateOf(base.harga?.let { fmtNum(it) } ?: "") }
    var masaTxt by remember(asetId) { mutableStateOf(base.masa?.toString() ?: "") }
    var nbvOpen by remember(asetId) { mutableStateOf(false) }
    var delAsk by remember { mutableStateOf(false) }
    var temuanDlg by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val fotos = fotosAll[asetId].orEmpty()

    fun close() { c.editing = null; c.pendingNew = null }

    var camPath by rememberSaveable { mutableStateOf<String?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val p = camPath; camPath = null
        if (p != null) { val f = File(p); if (ok) c.tambahFoto(asetId, listOf(android.net.Uri.fromFile(f)), f) else f.delete() }
    }
    fun launchCamera() {
        val dir = File(ctx.cacheDir, "kamera").apply { mkdirs() }
        val f = File(dir, "aset_${UUID.randomUUID()}.jpg")
        camPath = f.absolutePath
        camera.launch(FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f))
    }
    val camPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) launchCamera() else vm.toast("Izin kamera ditolak. Pakai Galeri untuk memilih foto.")
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris -> if (uris.isNotEmpty()) c.tambahFoto(asetId, uris) }

    Dialog(onDismissRequest = { close() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(C.Solid)) {
            Header(if (isSaved) "Aset" else "Aset baru", onBack = { close() }, actions = {
                HeaderAction("Simpan") {
                    c.simpan(m.copy(nbv = parseNum(nbvTxt), harga = parseNum(hargaTxt), masa = masaTxt.trim().toIntOrNull())) { close() }
                }
            })
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp).navigationBarsPadding().imePadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // foto
                Panel {
                    Text("FOTO (${fotos.size})", style = MaterialTheme.typography.labelSmall, color = C.Muted)
                    if (!isSaved) Text("Simpan aset dulu untuk menambah foto.", color = C.Muted, style = MaterialTheme.typography.bodyMedium)
                    else {
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconTextButton(id.tub.dinas.R.drawable.ic_kamera, "Kamera") {
                                if (androidx.core.content.ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) launchCamera()
                                else camPerm.launch(Manifest.permission.CAMERA)
                            }
                            IconTextButton(id.tub.dinas.R.drawable.ic_galeri, "Galeri", primary = false) { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                        }
                        fotos.forEachIndexed { i, f ->
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                EncryptedImage(vm.repo.vault, f.path, Modifier.size(84.dp).itemFrame(10.dp), maxSide = 300)
                                Column(Modifier.weight(1f)) {
                                    Text(if (i == 0) "★ Foto utama" else "Foto ${i + 1}", style = MaterialTheme.typography.bodyMedium)
                                    Row {
                                        if (i > 0) TextButton(onClick = { c.jadikanUtama(asetId, f.id) }) { Text("Jadikan utama") }
                                        TextButton(onClick = { c.hapusFoto(f.id) }) { Text("Hapus", color = C.Danger) }
                                    }
                                }
                            }
                        }
                    }
                }

                // identitas
                Panel {
                    Field(m.code, { m = m.copy(code = it.uppercase().filter { ch -> !ch.isWhitespace() }, barcodeSrc = m.barcodeSrc) }, "Kode aset (mis. TUBOE250018)", singleLine = true)
                    if (m.pcodes.isNotEmpty()) Text("Terbaca di foto: ${m.pcodes.joinToString(", ")}", color = C.Muted, style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.height(6.dp))
                    Field(m.name, { m = m.copy(name = it) }, "Nama / deskripsi", minLines = 2)
                    Label("Kategori")
                    FlowChips(KodeAset.KATEGORI.map { it to KodeAset.labelKategori(it) }, m.category) { m = m.copy(category = it) }
                    Spacer(Modifier.height(6.dp))
                    Field(m.classif, { m = m.copy(classif = it) }, "Klasifikasi (otomatis dari kode)", singleLine = true)
                    Spacer(Modifier.height(6.dp))
                    Field(m.owner, { m = m.copy(owner = it) }, "PT pemilik", singleLine = true)
                    Label("Kepemilikan")
                    FlowChips(listOf("own" to "Milik Sendiri", "lease" to "Sewa"), m.tenure) { m = m.copy(tenure = it) }
                }

                Panel {
                    Field(m.dept, { m = m.copy(dept = it) }, "Departemen", singleLine = true)
                    Spacer(Modifier.height(6.dp))
                    Field(m.location, { m = m.copy(location = it) }, "Lokasi", singleLine = true)
                    Spacer(Modifier.height(6.dp))
                    Field(m.user, { m = m.copy(user = it) }, "User / pengguna", singleLine = true)
                    Label("Kondisi")
                    FlowChips(listOf("Baik", "Cukup", "Rusak").map { it to it }, m.cond) { m = m.copy(cond = if (m.cond == it) "" else it) }
                    Spacer(Modifier.height(6.dp))
                    Field(m.acq, { m = m.copy(acq = it) }, "Tgl pembelian (yyyy-mm-dd)", singleLine = true)
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { Field(m.checkDate, { m = m.copy(checkDate = it) }, "Tgl cek fisik (yyyy-mm-dd)", singleLine = true) }
                        TextButton(onClick = { m = m.copy(checkDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())) }) { Text("Hari ini") }
                    }
                    Spacer(Modifier.height(6.dp))
                    Field(m.remarks, { m = m.copy(remarks = it) }, "Keterangan / remarks", minLines = 2)
                }

                Panel {
                    Label("Status barcode")
                    FlowChips(StatusBarcode.entries.map { it.name to it.label }, m.barcode?.name.orEmpty()) {
                        m = m.copy(barcode = StatusBarcode.from(it), barcodeSrc = SumberBarcode.MANUAL)
                    }
                    Text(when (m.barcodeSrc) {
                        SumberBarcode.OCR -> "sumber: hasil scan"; SumberBarcode.EXCEL -> "sumber: Excel"; SumberBarcode.MANUAL -> "diatur manual"; else -> "otomatis"
                    }, color = C.Muted, style = MaterialTheme.typography.labelSmall)
                    if (m.barcodeSrc == SumberBarcode.MANUAL) TextButton(onClick = {
                        val (st, src) = KodeAset.hitungStatusBarcode(m.code, m.pcodes, m.pcodes.isNotEmpty(), m.barcode, m.barcodeSrc, paksa = true)
                        m = m.copy(barcode = st, barcodeSrc = src)
                    }) { Text("↻ Hitung otomatis") }
                    Label("Status audit")
                    FlowChips(AuditState.entries.map { it.name to it.label }, m.state.name) {
                        val s = AuditState.from(it)
                        m = m.copy(state = s, auditedAt = if (s == AuditState.PENDING) null else System.currentTimeMillis())
                    }
                }

                Panel(onClick = { nbvOpen = !nbvOpen }) {
                    Text((if (nbvOpen) "▾ " else "▸ ") + "Data NBV (nilai buku, harga perolehan)", style = MaterialTheme.typography.titleMedium)
                }
                if (nbvOpen) Panel {
                    Field(nbvTxt, { nbvTxt = it }, "Nilai buku (IDR)", singleLine = true)
                    Spacer(Modifier.height(6.dp))
                    Field(hargaTxt, { hargaTxt = it }, "Harga perolehan (IDR)", singleLine = true)
                    Spacer(Modifier.height(6.dp))
                    Field(masaTxt, { masaTxt = it }, "Masa manfaat (bulan)", singleLine = true)
                    Spacer(Modifier.height(6.dp))
                    Field(m.tglTercatat, { m = m.copy(tglTercatat = it) }, "Tanggal tercatat", singleLine = true)
                    Spacer(Modifier.height(6.dp))
                    Field(m.nbvKet, { m = m.copy(nbvKet = it) }, "NBV - keterangan", singleLine = true)
                    Spacer(Modifier.height(6.dp))
                    Field(m.catatan, { m = m.copy(catatan = it) }, "Catatan (NBV)", singleLine = true)
                    Spacer(Modifier.height(6.dp))
                    Field(m.sumber, { m = m.copy(sumber = it) }, "Sumber NBV", singleLine = true)
                }

                if (m.log.isNotEmpty()) Panel {
                    Text("RIWAYAT", style = MaterialTheme.typography.labelSmall, color = C.Muted)
                    val fmt = SimpleDateFormat("dd/MM HH:mm", Locale("id", "ID"))
                    m.log.take(6).forEach { line ->
                        val at = line.substringBefore('|').toLongOrNull()
                        Text((at?.let { fmt.format(Date(it)) } ?: "") + "  " + line.substringAfter('|'), style = MaterialTheme.typography.bodyMedium, color = C.Muted)
                    }
                }

                if (isSaved) {
                    AmberButton(if (existing?.temuanId != null) "Buat temuan lagi di dinas…" else "Buat temuan lensa Aset di dinas…", Modifier.fillMaxWidth()) { temuanDlg = true }
                    SoftButton("Hapus aset", Modifier.fillMaxWidth(), danger = true) { delAsk = true }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (delAsk) Confirm("Hapus aset?", "Aset dan semua fotonya dihapus permanen dari register.", onConfirm = { c.hapus(asetId); close() }, onDismiss = { delAsk = false })
    if (temuanDlg) {
        val dinas by vm.dinasList.collectAsState()
        AlertDialog(
            onDismissRequest = { temuanDlg = false },
            title = { Text("Pilih dinas") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (dinas.isEmpty()) Text("Belum ada dinas. Buat dinas dulu di halaman depan.")
                    dinas.forEach { d -> TextButton(onClick = { temuanDlg = false; c.buatTemuan(asetId, d.id) }) { Text(d.name) } }
                    Text("Temuan dibuat dengan kode, nama, kondisi, dan foto utama aset ini. Lensa Aset terisi otomatis.", color = C.Muted, style = MaterialTheme.typography.labelSmall)
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { temuanDlg = false }) { Text("Batal") } },
        )
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun FlowChips(options: List<Pair<String, String>>, selected: String, onPick: (String) -> Unit) {
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (k, l) -> Chip(l, k == selected, onClick = { onPick(k) }) }
    }
}

private fun fmtNum(d: Double): String = if (d == Math.floor(d) && Math.abs(d) < 1e15) d.toLong().toString() else d.toString()
private fun parseNum(s: String): Double? = id.tub.dinas.aset.AsetImport.parseAngka(s)
