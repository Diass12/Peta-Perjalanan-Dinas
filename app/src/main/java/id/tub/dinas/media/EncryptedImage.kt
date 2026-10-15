package id.tub.dinas.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import id.tub.dinas.security.MediaVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Cache bitmap kecil di memori (bukan di disk) supaya foto tidak pernah tersimpan tanpa enkripsi. */
object ImageCache {
    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 8).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    fun get(k: String): Bitmap? = cache.get(k)
    fun put(k: String, b: Bitmap) { cache.put(k, b) }
    fun clear() = cache.evictAll()
}

suspend fun loadEncryptedBitmap(vault: MediaVault, path: String, maxSide: Int): Bitmap? {
    val key = "$path@$maxSide"
    ImageCache.get(key)?.let { return it }
    return withContext(Dispatchers.IO) {
        runCatching {
            val bytes = vault.read(path)
            val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, b)
            var s = 1
            while (maxOf(b.outWidth, b.outHeight) / (s * 2) >= maxSide) s *= 2
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = s })
        }.getOrNull()?.also { ImageCache.put(key, it) }
    }
}

@Composable
fun EncryptedImage(vault: MediaVault, path: String, modifier: Modifier = Modifier, maxSide: Int = 800, contentScale: ContentScale = ContentScale.Crop) {
    var bmp by remember(path, maxSide) { mutableStateOf(ImageCache.get("$path@$maxSide")) }
    LaunchedEffect(path, maxSide) { if (bmp == null) bmp = loadEncryptedBitmap(vault, path, maxSide) }
    Box(modifier.background(Color(0xFFE4E0D5))) {
        bmp?.let { Image(it.asImageBitmap(), null, Modifier.matchParentSize(), contentScale = contentScale) }
    }
}
