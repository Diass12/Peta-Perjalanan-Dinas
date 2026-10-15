package id.tub.dinas.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import id.tub.dinas.data.PhotoPreset
import id.tub.dinas.domain.GeoPoint
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale

class ProcessedPhoto(val jpeg: ByteArray, val width: Int, val height: Int, val geo: GeoPoint?, val takenAt: Long?)

object PhotoPipeline {
    private const val MAX_INPUT_BYTES = 80L * 1024 * 1024

    /** Membaca foto dari Uri, memutar sesuai EXIF, mengecilkan, dan mengompres. EXIF asli TIDAK ikut tersimpan. */
    fun process(ctx: Context, uri: Uri, preset: PhotoPreset, stamp: StampSource? = null): ProcessedPhoto {
        val cr = ctx.contentResolver
        var geo: GeoPoint? = null
        var takenAt: Long? = null
        var orientation = ExifInterface.ORIENTATION_NORMAL
        cr.openInputStream(uri)?.use { s ->
            runCatching {
                val ex = ExifInterface(s)
                ex.latLong?.let { ll -> if (ll[0] in -90.0..90.0 && ll[1] in -180.0..180.0 && !(ll[0] == 0.0 && ll[1] == 0.0)) geo = GeoPoint(ll[0], ll[1], null, "exif") }
                ex.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)?.let { d ->
                    takenAt = runCatching { SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).parse(d)?.time }.getOrNull()
                }
                orientation = ex.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }
        } ?: throw IOException("Foto tidak dapat dibuka")

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Berkas bukan gambar yang valid")
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (longest / (sample * 2) >= preset.maxSide) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
        var bmp = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: throw IOException("Gagal membaca gambar")

        val scale = preset.maxSide.toFloat() / maxOf(bmp.width, bmp.height)
        val m = Matrix()
        if (scale < 1f) m.postScale(scale, scale)
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(270f); m.postScale(-1f, 1f) }
        }
        if (!m.isIdentity) {
            val t = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            if (t !== bmp) bmp.recycle()
            bmp = t
        }
        if (stamp != null) bmp = Stamp.draw(bmp, stamp, stamp.geo ?: geo, takenAt ?: System.currentTimeMillis(), Stamp.newCode())
        val out = ByteArrayOutputStream(512 * 1024)
        bmp.compress(Bitmap.CompressFormat.JPEG, preset.quality, out)
        val w = bmp.width; val h = bmp.height
        bmp.recycle()
        return ProcessedPhoto(out.toByteArray(), w, h, geo, takenAt)
    }
}
