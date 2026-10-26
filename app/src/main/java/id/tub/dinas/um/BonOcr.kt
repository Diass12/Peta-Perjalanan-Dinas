package id.tub.dinas.um

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/** Membaca foto bon di perangkat (ML Kit, tanpa internet). Hasil hanya usulan; pengguna tetap memeriksa. */
object BonOcr {
    suspend fun baca(jpeg: ByteArray): BonParse.Hasil? {
        val bmp = withContext(Dispatchers.Default) { decode(jpeg) } ?: return null
        val teks = suspendCancellableCoroutine<String?> { k ->
            val r = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            r.process(InputImage.fromBitmap(bmp, 0))
                .addOnSuccessListener { k.resume(it.text) }
                .addOnFailureListener { k.resume(null) }
            k.invokeOnCancellation { r.close() }
        } ?: return null
        return BonParse.urai(teks)
    }

    private fun decode(b: ByteArray): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(b, 0, b.size, o)
        var s = 1
        while (o.outWidth / (s * 2) >= 2000 || o.outHeight / (s * 2) >= 2000) s *= 2
        return BitmapFactory.decodeByteArray(b, 0, b.size, BitmapFactory.Options().apply { inSampleSize = s })
    }
}
