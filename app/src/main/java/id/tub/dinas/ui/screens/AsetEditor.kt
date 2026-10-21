package id.tub.dinas.ui.screens

import id.tub.dinas.ui.components.Text
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var aiBusy by remember { mutableStateOf<String?>(null) }
    var aiHasil by remember { mutableStateOf<id.tub.dinas.ai.AiHasil?>(null) }
    val ctx = LocalContext.current
    val fotos = fotosAll[asetId].orEmpty()
    var besar by remember { mutableStateOf<Int?>(null) }
    besar?.let { b -> id.tub.dinas.ui.components.ZoomViewer(vm, fotos.map { it.path }, b) { besar = null } }

    fun close() { c.editing = null; c.pendingNew = null }
    val kartu = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri -> if (uri != null && existing != null) c.exportKartu(uri, existing) }

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
    val mediaAll by c.media.collectAsState()
    val medias = mediaAll[asetId].orEmpty()
    var vidPath by rememberSaveable { mutableStateOf<String?>(null) }
    var playing by remember { mutableStateOf<AsetMediaModel?>(null) }
    var delMedia by remember { mutableStateOf<String?>(null) }
    val vidPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(5)) { uris -> if (uris.isNotEmpty()) c.tambahMedia(asetId, "VIDEO", uris) }
    val docPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> if (uris.isNotEmpty()) c.tambahMedia(asetId, "DOC", uris) }
    val videoCam = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()) { ok ->
        val p = vidPath; vidPath = null
        if (p != null) { val f = File(p); if (ok && f.length() > 0) c.tambahMediaFile(asetId, "VIDEO", f, "Video ${SimpleDateFormat("d MMM HH.mm", Locale("id", "ID")).format(Date())}.mp4", "mp4") else f.delete() }
    }
    fun launchVideo() {
        val dir = File(ctx.cacheDir, "media").apply { mkdirs() }
        val f = File(dir, "vid_${UUID.randomUUID()}.mp4")
        vidPath = f.absolutePath
        videoCam.launch(FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f))
    }
    val vidPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) launchVideo() else vm.toast("Izin kamera ditolak. Pakai \"Pilih video\".")
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
                                EncryptedImage(vm.repo.vault, f.path, Modifier.size(84.dp).itemFrame(10.dp).clickable { besar = i }, maxSide = 300)
                                Column(Modifier.weight(1f)) {
                                    Text(if (i == 0) "★ Foto utama" else "Foto ${i + 1}", style = MaterialTheme.typography.bodyMedium)
                                    Row {
                                        TextButton(onClick = { besar = i }) { Text("Perbesar") }
                                        if (i > 0) TextButton(onClick = { c.jadikanUtama(asetId, f.id) }) { Text("Jadikan utama") }
                                        TextButton(onClick = { c.hapusFoto(f.id) }) { Text("Hapus", color = C.Danger) }
                                    }
                                }
                            }
                        }
                    }
                }

                // baca kode & analisis AI
                if (isSaved && fotos.isNotEmpty()) Panel {
                    Text("BACA KODE & ANALISIS", style = MaterialTheme.typography.labelSmall, color = C.Muted)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SoftButton(if (aiBusy == "ocr") "Membaca…" else "🔍 Baca kode dari foto", Modifier.weight(1f)) {
                            if (aiBusy == null) { aiBusy = "ocr"; scope.launch {
                                try {
                                    val kode = LinkedHashSet<String>()
                                    for (f in fotos.take(4)) { val b = vm.repo.asetFotoBytes(f.path) ?: continue; kode.addAll(runCatching { id.tub.dinas.aset.CodeScanner.bacaJpeg(b).kode }.getOrDefault(emptyList())); if (kode.isNotEmpty()) break }
                                    if (kode.isEmpty()) vm.toast("Tidak ada kode terbaca di foto")
                                    else {
                                        var n = m.copy(pcodes = (m.pcodes + kode).distinct())
                                        if (n.code.isBlank() && kode.size == 1) n = n.copy(code = kode.first())
                                        val (st, src) = KodeAset.hitungStatusBarcode(n.code, n.pcodes, true, n.barcode, n.barcodeSrc)
                                        m = n.copy(barcode = st, barcodeSrc = src); vm.toast("Terbaca: ${kode.joinToString(", ")}")
                                    }
                                } finally { aiBusy = null }
                            } }
                        }
                        SoftButton(if (aiBusy == "ai") "Menganalisis…" else "✨ Analisis AI (Gemini)", Modifier.weight(1f)) {
                            val key = vm.settings.geminiKey
                            if (key.isBlank()) vm.toast("Isi kunci Gemini dulu di Pengaturan")
                            else if (aiBusy == null) { aiBusy = "ai"; scope.launch {
                                try {
                                    val b = vm.repo.asetFotoBytes(fotos.first().path) ?: throw java.io.IOException("Foto tidak terbaca")
                                    val kecil = withContext(Dispatchers.IO) { id.tub.dinas.aset.AsetImport.toJpeg(b, 1280, 80) ?: b }
                                    aiHasil = withContext(Dispatchers.IO) { id.tub.dinas.ai.Gemini.analisis(key, vm.settings.geminiModel, kecil) }
                                } catch (e: Exception) { vm.toast(e.message ?: "Analisis gagal") } finally { aiBusy = null }
                            } }
                        }
                    }
                    Text("Baca kode berjalan di perangkat tanpa internet. Analisis AI mengirim foto utama ke Google Gemini.", color = C.Muted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
                }
                aiHasil?.let { h ->
                    AlertDialog(onDismissRequest = { aiHasil = null }, title = { Text("Hasil analisis AI") },
                        text = { Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Nama: ${h.nama.ifBlank { "-" }}"); Text("Jenis: ${h.jenis.ifBlank { "-" }}")
                            Text("Kode terbaca: ${h.kode.joinToString(", ").ifBlank { "-" }}"); Text("Stiker: ${h.barcode.ifBlank { "-" }}")
                            if (h.catatan.isNotBlank()) Text("Catatan: ${h.catatan}")
                            Text("Hasil AI bisa keliru; periksa sebelum menyimpan.", color = C.Muted, style = MaterialTheme.typography.labelSmall)
                        } },
                        confirmButton = { TextButton(onClick = {
                            var n = m
                            if (h.nama.isNotBlank() && (n.name.isBlank() || n.name != h.nama)) n = n.copy(name = h.nama)
                            if (h.kode.isNotEmpty()) { n = n.copy(pcodes = (n.pcodes + h.kode).distinct()); if (n.code.isBlank() && h.kode.size == 1) n = n.copy(code = h.kode.first()) }
                            if (h.catatan.isNotBlank() && n.remarks.isBlank()) n = n.copy(remarks = h.catatan)
                            val kelas = KodeAset.kelasDariKode(n.code)
                            if (kelas != null && n.classif.isBlank()) n = n.copy(classif = kelas.nama, category = kelas.kategori)
                            else if (n.classif.isBlank() && h.jenis.isNotBlank()) n = n.copy(category = KodeAset.tebakKategori(n.code, h.nama + " " + h.jenis))
                            val (st, src) = KodeAset.hitungStatusBarcode(n.code, n.pcodes, n.pcodes.isNotEmpty(), n.barcode, n.barcodeSrc)
                            m = n.copy(barcode = st, barcodeSrc = src); aiHasil = null
                        }) { Text("Terapkan") } },
                        dismissButton = { TextButton(onClick = { aiHasil = null }) { Text("Tutup") } })
                }

                // video & dokumen
                if (isSaved) Panel {
                    Text("VIDEO & DOKUMEN (${medias.size})", style = MaterialTheme.typography.labelSmall, color = C.Muted)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PrimaryButton("🎬 Rekam video") {
                            if (androidx.core.content.ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) launchVideo()
                            else vidPerm.launch(Manifest.permission.CAMERA)
                        }
                        SoftButton("Pilih video") { vidPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) }
                    }
                    Spacer(Modifier.height(6.dp))
                    SoftButton("📎 Lampirkan dokumen (PDF, Word, Excel, dll.)") { docPicker.launch(arrayOf("*/*")) }
                    medias.forEach { m ->
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (m.kind == "VIDEO") "🎬" else "📄", style = MaterialTheme.typography.titleLarge)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(m.name, maxLines = 1, style = MaterialTheme.typography.bodyMedium)
                                Text((if (m.kind == "VIDEO") "Video" else "Dokumen") + " · " + (if (m.sizeBytes >= 1048576) String.format(Locale.US, "%.1f MB", m.sizeBytes / 1048576.0) else "${m.sizeBytes / 1024} KB"), color = C.Muted, style = MaterialTheme.typography.labelSmall)
                            }
                            TextButton({ playing = m }) { Text(if (m.kind == "VIDEO") "Putar" else "Buka") }
                            TextButton({ delMedia = m.id }) { Text("Hapus", color = C.Danger) }
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
                    MasterPick("Klasifikasi (otomatis dari kode)", m.classif, c.opsi(id.tub.dinas.aset.MasterField.KLASIF, list)) { m = m.copy(classif = it) }
                    Spacer(Modifier.height(6.dp))
                    MasterPick("PT pemilik", m.owner, c.opsi(id.tub.dinas.aset.MasterField.PT, list)) { m = m.copy(owner = it) }
                    Label("Kepemilikan")
                    FlowChips(listOf("own" to "Milik Sendiri", "lease" to "Sewa"), m.tenure) { m = m.copy(tenure = it) }
                }

                Panel {
                    MasterPick("Departemen", m.dept, c.opsi(id.tub.dinas.aset.MasterField.DEPT, list)) { m = m.copy(dept = it) }
                    Spacer(Modifier.height(6.dp))
                    MasterPick("Lokasi", m.location, c.opsi(id.tub.dinas.aset.MasterField.LOKASI, list)) { m = m.copy(location = it) }
                    Spacer(Modifier.height(6.dp))
                    Field(m.user, { m = m.copy(user = it) }, "User / pengguna", singleLine = true)
                    Label("Kondisi")
                    FlowChips(c.opsi(id.tub.dinas.aset.MasterField.KONDISI, list).map { it to it }, m.cond) { m = m.copy(cond = if (m.cond == it) "" else it) }
                    Spacer(Modifier.height(6.dp))
                    DateField("Tgl pembelian", m.acq, { m = m.copy(acq = it) })
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        DateField("Tgl cek fisik", m.checkDate, { m = m.copy(checkDate = it) }, Modifier.weight(1f))
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

                if (id.tub.dinas.security.Lisensi.ho) {
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
                    SoftButton("Unduh kartu aset (PDF)", Modifier.fillMaxWidth()) { kartu.launch("Aset ${(existing?.code ?: "").ifEmpty { "tanpa-kode" }}.pdf") }
                    SoftButton("Hapus aset", Modifier.fillMaxWidth(), danger = true) { delAsk = true }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    playing?.let { AsetMediaViewer(vm, it) { playing = null } }
    delMedia?.let { id -> Confirm("Hapus berkas?", "Video atau dokumen ini dihapus permanen dari aset.", onConfirm = { c.hapusMedia(id) }, onDismiss = { delMedia = null }) }
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

/** Memutar video di dialog; dokumen didekripsi ke berkas sementara lalu dibuka dengan aplikasi lain. */
@Composable
private fun AsetMediaViewer(vm: MainViewModel, m: AsetMediaModel, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var file by remember { mutableStateOf<File?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    val ext = m.path.removeSuffix(".enc").substringAfterLast('.', "bin")
    LaunchedEffect(m.id) {
        try {
            val f = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { vm.repo.mediaTempFile(m.path, ".$ext") }
            if (m.kind == "VIDEO") file = f
            else {
                val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
                val mime = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.lowercase()) ?: "*/*"
                val it = android.content.Intent(android.content.Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                try { ctx.startActivity(android.content.Intent.createChooser(it, m.name)) } catch (e: Exception) { vm.toast("Tidak ada aplikasi untuk membuka berkas ini") }
                onClose()
            }
        } catch (e: Exception) { err = "Tidak dapat dibuka: ${e.message ?: "berkas rusak"}" }
    }
    DisposableEffect(m.id) { onDispose { if (m.kind == "VIDEO") file?.delete() } }
    if (m.kind != "VIDEO" && err == null) return
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth().padding(12.dp), shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp), color = androidx.compose.ui.graphics.Color(0xFF0B2545)) {
            Column(Modifier.padding(14.dp)) {
                Text(m.name, color = androidx.compose.ui.graphics.Color.White, maxLines = 2)
                Spacer(Modifier.height(8.dp))
                val f = file
                when {
                    err != null -> Text(err!!, color = androidx.compose.ui.graphics.Color(0xFFFFB4AB))
                    f == null -> Text("Menyiapkan (mendekripsi)…", color = androidx.compose.ui.graphics.Color.White)
                    else -> androidx.compose.ui.viewinterop.AndroidView(
                        factory = { c ->
                            android.widget.VideoView(c).apply {
                                setVideoPath(f.path)
                                setMediaController(android.widget.MediaController(c).also { it.setAnchorView(this) })
                                setOnPreparedListener { start() }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(320.dp),
                        onRelease = { it.stopPlayback() },
                    )
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClose, Modifier.align(Alignment.End)) { Text("Tutup") }
            }
        }
    }
}
