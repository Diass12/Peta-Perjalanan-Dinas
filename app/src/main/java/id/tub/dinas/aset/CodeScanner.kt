package id.tub.dinas.aset

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import id.tub.dinas.domain.KodeAset
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Hasil baca satu foto. `kode` sudah dikoreksi dan unik; `mentah` = isi barcode/QR yang bukan pola kode. */
class ScanRead(val kode: List<String>, val mentah: List<String>, val teks: String)

/**
 * Membaca kode aset dari foto: barcode/QR (ML Kit) lalu OCR teks (ML Kit, model Latin terpasang di aplikasi).
 * Bila belum ada kode, OCR diulang pada empat potongan sudut (65% x 65%) seperti mode "deep" di AsetScan.
 */
object CodeScanner {
    private const val MAX_SIDE = 2400

    suspend fun baca(ctx: Context, uri: Uri): ScanRead {
        val bmp = muat(ctx, uri)
        val barcode = BarcodeScanning.getClient()
        val ocr = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val kode = LinkedHashSet<String>()
            val mentah = LinkedHashSet<String>()
            val teks = StringBuilder()

            runCatching { barcode.process(InputImage.fromBitmap(bmp, 0)).await() }.getOrNull()?.forEach { b ->
                val raw = b.rawValue?.trim().orEmpty()
                if (raw.isEmpty()) return@forEach
                val k = KodeAset.ekstrak(raw)
                if (k.isNotEmpty()) kode.addAll(k) else mentah.add(raw)
            }

            suspend fun ocrDi(b: Bitmap) {
                val t = runCatching { ocr.process(InputImage.fromBitmap(b, 0)).await().text }.getOrNull().orEmpty()
                if (t.isNotEmpty()) { teks.append(t).append('\n'); kode.addAll(KodeAset.ekstrak(t)) }
            }
            ocrDi(bmp)
            if (kode.isEmpty()) {
                val w = (bmp.width * 0.65f).toInt(); val h = (bmp.height * 0.65f).toInt()
                for ((x, y) in listOf(0 to 0, bmp.width - w to 0, 0 to bmp.height - h, bmp.width - w to bmp.height - h)) {
                    val part = Bitmap.createBitmap(bmp, x, y, w, h)
                    try { ocrDi(part) } finally { if (part !== bmp) part.recycle() }
                    if (kode.isNotEmpty()) break
                }
            }
            return ScanRead(kode.toList(), mentah.toList(), teks.toString())
        } finally {
            bmp.recycle(); barcode.close(); ocr.close()
        }
    }

    /** Memuat bitmap ≤ MAX_SIDE dan memutarnya sesuai EXIF. */
    private fun muat(ctx: Context, uri: Uri): Bitmap {
        val cr = ctx.contentResolver
        var rot = 0f
        cr.openInputStream(uri)?.use { s ->
            runCatching {
                rot = when (ExifInterface(s).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw java.io.IOException("Berkas bukan gambar yang valid")
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        var bmp = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
            ?: throw java.io.IOException("Gagal membaca gambar")
        if (rot != 0f) {
            val r = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(rot) }, true)
            if (r !== bmp) bmp.recycle()
            bmp = r
        }
        return bmp
    }
}

suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { c ->
    addOnSuccessListener { c.resume(it) }
    addOnFailureListener { c.resumeWithException(it) }
    addOnCanceledListener { c.cancel() }
}
