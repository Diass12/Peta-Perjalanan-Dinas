package id.tub.pemberi

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.util.concurrent.Executors

/** Bantuan QR untuk lisensi dua arah: membuat gambar QR dan memindai QR dengan kamera. */
object QrKit {
    /** Awalan isi QR: permintaan dari HP staff, dan lisensi dari Pemberi Lisensi. */
    const val PREFIX_MINTA = "DNSREQ1|"
    const val PREFIX_LISENSI = "DNSLIC1|"

    fun bitmap(teks: String, px: Int = 640): Bitmap {
        val m = QRCodeWriter().encode(teks, BarcodeFormat.QR_CODE, 0, 0,
            mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 1))
        val cell = maxOf(1, px / m.width)
        val w = m.width * cell
        val pix = IntArray(w * w)
        for (y in 0 until w) for (x in 0 until w) pix[y * w + x] = if (m.get(x / cell, y / cell)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        return Bitmap.createBitmap(pix, w, w, Bitmap.Config.ARGB_8888)
    }
}

/** Dialog layar penuh: kamera memindai satu QR, memanggil [onHasil] sekali dengan isinya. */
@Composable
fun QrScanDialog(judul: String, onHasil: (String) -> Unit, onTutup: () -> Unit) {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var izin by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val minta = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { izin = it }
    LaunchedEffect(Unit) { if (!izin) minta.launch(Manifest.permission.CAMERA) }
    Dialog(onDismissRequest = onTutup, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(judul, style = MaterialTheme.typography.titleMedium)
                if (!izin) Text("Izin kamera diperlukan untuk memindai QR.")
                else {
                    val selesai = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
                    val exec = remember { Executors.newSingleThreadExecutor() }
                    val scanner = remember { BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()) }
                    DisposableEffect(Unit) { onDispose { exec.shutdown(); scanner.close() } }
                    AndroidView(modifier = Modifier.fillMaxWidth().weight(1f), factory = { c ->
                        val pv = PreviewView(c)
                        val fut = ProcessCameraProvider.getInstance(c)
                        fut.addListener({
                            val prov = fut.get()
                            val prev = Preview.Builder().build().also { it.setSurfaceProvider(pv.surfaceProvider) }
                            val an = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                            an.setAnalyzer(exec, @androidx.annotation.OptIn(ExperimentalGetImage::class) { p: ImageProxy ->
                                val img = p.image
                                if (img == null || selesai.get()) { p.close() } else {
                                    scanner.process(InputImage.fromMediaImage(img, p.imageInfo.rotationDegrees))
                                        .addOnSuccessListener { l ->
                                            val raw = l.firstNotNullOfOrNull { it.rawValue }
                                            if (raw != null && selesai.compareAndSet(false, true)) pv.post { onHasil(raw) }
                                        }
                                        .addOnCompleteListener { p.close() }
                                }
                            })
                            prov.unbindAll()
                            prov.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, prev, an)
                        }, ContextCompat.getMainExecutor(c))
                        pv
                    })
                }
                OutlinedButton(onClick = onTutup, modifier = Modifier.fillMaxWidth()) { Text("Batal") }
            }
        }
    }
}
