package id.tub.dinas.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import id.tub.dinas.MainViewModel
import id.tub.dinas.aset.Beep
import id.tub.dinas.domain.KodeAset
import java.io.File
import java.util.UUID

/** Penganalisis frame untuk mode Live: barcode tiap 700 ms, OCR cepat tiap tick ke-3. */
private class LiveAnalyzer(val onKode: (List<String>) -> Unit) : ImageAnalysis.Analyzer {
    private val barcode = BarcodeScanning.getClient()
    private val ocr = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private var last = 0L
    private var tick = 0

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    override fun analyze(p: ImageProxy) {
        val now = SystemClock.elapsedRealtime()
        val img = p.image
        if (now - last < 700 || img == null) { p.close(); return }
        last = now; tick++
        val input = InputImage.fromMediaImage(img, p.imageInfo.rotationDegrees)
        val kode = LinkedHashSet<String>()
        barcode.process(input)
            .addOnSuccessListener { l -> l.forEach { b -> b.rawValue?.let { kode.addAll(KodeAset.ekstrak(it)) } } }
            .addOnCompleteListener {
                if (kode.isEmpty() && tick % 3 == 0) {
                    ocr.process(input).addOnSuccessListener { t -> kode.addAll(KodeAset.ekstrak(t.text)) }.addOnCompleteListener { selesai(p, kode) }
                } else selesai(p, kode)
            }
    }

    private fun selesai(p: ImageProxy, kode: Set<String>) {
        try { if (kode.isNotEmpty()) onKode(kode.toList()) } finally { p.close() }
    }

    fun tutup() { runCatching { barcode.close() }; runCatching { ocr.close() } }
}

private class Baris(val teks: String, val ok: Boolean)

/** Scanner dalam aplikasi: rana, Live, Multi-foto, mode Audit. */
@Composable
fun AsetScanner(vm: MainViewModel, onClose: () -> Unit) {
    val c = vm.aset
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var izin by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val minta = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { izin = it }
    var live by remember { mutableStateOf(false) }
    var multi by remember { mutableStateOf(false) }
    var senter by remember { mutableStateOf(false) }
    var proses by remember { mutableStateOf(false) }
    val baris = remember { mutableStateListOf<Baris>() }
    val tray = remember { mutableStateListOf<File>() }
    var capture by remember { mutableStateOf<ImageCapture?>(null) }
    var kamera by remember { mutableStateOf<Camera?>(null) }
    val liveState = rememberUpdatedState(live)
    val bunyi = vm.settings.sfx
    val pvHolder = remember { arrayOfNulls<PreviewView>(1) }

    fun tambah(t: String, ok: Boolean) { baris.add(0, Baris(t, ok)); while (baris.size > 5) baris.removeAt(baris.lastIndex) }

    val galeri = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(50)) { uris ->
        if (uris.isNotEmpty()) { c.galeriUris = uris; onClose() }
    }

    // pasang kamera; ulangi bila mode Live berubah
    DisposableEffect(izin, live) {
        var provider: ProcessCameraProvider? = null
        var analyzer: LiveAnalyzer? = null
        val exec = ContextCompat.getMainExecutor(ctx)
        if (izin) {
            val f = ProcessCameraProvider.getInstance(ctx)
            f.addListener({
                val p = f.get(); provider = p
                p.unbindAll()
                val prev = Preview.Builder().build().also { it.surfaceProvider = pvHolder[0]?.surfaceProvider }
                val cap = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build()
                val uses = ArrayList<UseCase>().apply { add(prev); add(cap) }
                if (live) {
                    val an = LiveAnalyzer { kodes ->
                        vm.work {
                            for (k in kodes) {
                                val r = c.prosesLive(k) ?: continue
                                tambah(r.first, r.second)
                                Beep.hasil(ctx, r.second, bunyi)
                            }
                        }
                    }
                    analyzer = an
                    uses.add(ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build().also { it.setAnalyzer(exec, an) })
                }
                kamera = p.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, *uses.toTypedArray())
                capture = cap
            }, exec)
        }
        onDispose { analyzer?.tutup(); provider?.unbindAll() }
    }
    LaunchedEffect(senter, kamera) { runCatching { kamera?.cameraControl?.enableTorch(senter) } }

    fun jepret() {
        val cap = capture ?: return
        if (proses) return
        val dir = File(ctx.cacheDir, "kamera").apply { mkdirs() }
        val f = File(dir, "scan_${UUID.randomUUID()}.jpg")
        proses = true
        cap.takePicture(ImageCapture.OutputFileOptions.Builder(f).build(), ContextCompat.getMainExecutor(ctx), object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(r: ImageCapture.OutputFileResults) {
                if (multi) { tray.add(f); proses = false; return }
                vm.work(onDone = { proses = false }) {
                    try {
                        val rep = c.scanFoto(Uri.fromFile(f))
                        if (rep.lines.isEmpty()) { tambah("Kode tidak terbaca. Dekatkan label atau tambah cahaya.", false); Beep.hasil(ctx, false, bunyi); rep.jpeg?.let { c.fotoTanpaKode = it } }
                        rep.lines.forEach { l -> tambah((if (l.baru) "＋ " else "✔ ") + l.kode + (if (l.nama.isNotEmpty()) " — ${l.nama}" else "") + " · ${l.catatan}", true) }
                        if (rep.lines.isNotEmpty()) Beep.hasil(ctx, true, bunyi)
                    } finally { f.delete() }
                }
            }
            override fun onError(e: ImageCaptureException) { proses = false; vm.toast("Gagal mengambil foto: ${e.message}") }
        })
    }

    fun selesaiMulti() {
        if (tray.isEmpty()) return
        c.galeriUris = tray.map { Uri.fromFile(it) }
        onClose()
    }

    Dialog(onDismissRequest = { tray.forEach { it.delete() }; onClose() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            if (!izin) {
                Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Izin kamera diperlukan untuk memindai label aset.", color = Color.White, fontSize = 16.sp)
                    AGradButton("Izinkan kamera") { minta.launch(Manifest.permission.CAMERA) }
                    ADarkButton("Pakai Galeri") { galeri.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                }
            } else {
                AndroidView(factory = { PreviewView(it).also { pv -> pv.scaleType = PreviewView.ScaleType.FILL_CENTER; pvHolder[0] = pv } }, modifier = Modifier.fillMaxSize())
                Box(Modifier.align(Alignment.Center).fillMaxWidth(0.78f).aspectRatio(1.4f).border(2.dp, Color(0xCCFFFFFF), RoundedCornerShape(24.dp)))
            }
            // atas
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(42.dp).clip(CircleShape).background(Color(0x99000000)).clickable { tray.forEach { it.delete() }; onClose() }, contentAlignment = Alignment.Center) { Text("✕", color = Color.White, fontSize = 18.sp) }
                Spacer(Modifier.weight(1f))
                AChip("Audit", c.auditMode, A.Green) { c.auditMode = !c.auditMode }
                AChip("Live", live, A.Amber) { live = !live; if (live) multi = false }
                AChip("Multi", multi, A.Blue) { multi = !multi; if (multi) live = false }
            }
            // bawah
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                baris.forEach { b ->
                    Text(b.teks, color = Color.White, fontSize = 14.sp, maxLines = 2,
                        modifier = Modifier.fillMaxWidth().background(if (b.ok) Color(0xCC065F46) else Color(0xCC7F1D1D), RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 8.dp))
                }
                if (c.fotoTanpaKode != null) ADarkButton("Buat aset dari foto terakhir", Modifier.fillMaxWidth()) { c.fotoTanpaKode?.let { j -> c.fotoTanpaKode = null; onClose(); c.buatDariFoto(j) } }
                Text(
                    when { !izin -> ""; live -> "Mode Live: arahkan ke label, kode dibaca otomatis (tanpa foto)"; multi -> "Mode Multi: ambil beberapa foto, lalu Selesai"; c.auditMode -> "Mode Audit: aset yang di-scan ditandai Ditemukan" else -> "Arahkan ke label lalu ketuk tombol" },
                    color = Color(0xCCFFFFFF), fontSize = 13.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Box(Modifier.size(52.dp).clip(CircleShape).background(Color(0x99000000)).clickable { galeri.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, contentAlignment = Alignment.Center) { Text("🖼", fontSize = 22.sp) }
                    Box(Modifier.size(78.dp).border(4.dp, Color.White, CircleShape).padding(6.dp).clip(CircleShape).background(if (live) Color(0x55FFFFFF) else Color.White).clickable(enabled = !live && izin) { jepret() }, contentAlignment = Alignment.Center) {
                        if (proses) CircularProgressIndicator(Modifier.size(32.dp), strokeWidth = 3.dp)
                        else if (multi && tray.isNotEmpty()) Text("${tray.size}", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = Color(0xFF2563EB))
                    }
                    if (multi) Box(Modifier.height(52.dp).clip(RoundedCornerShape(26.dp)).background(if (tray.isEmpty()) Color(0x99000000) else Color(0xFF2563EB)).clickable { selesaiMulti() }.padding(horizontal = 18.dp), contentAlignment = Alignment.Center) { Text("Selesai", color = Color.White, fontWeight = FontWeight.Bold) }
                    else Box(Modifier.size(52.dp).clip(CircleShape).background(if (senter) Color.White else Color(0x99000000)).clickable { senter = !senter }, contentAlignment = Alignment.Center) { Text("🔦", fontSize = 22.sp) }
                }
            }
        }
    }
}
