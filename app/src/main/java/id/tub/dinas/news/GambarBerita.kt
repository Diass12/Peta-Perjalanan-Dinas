package id.tub.dinas.news

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Gambar sampul berita dari alamat di RSS: dimuat saat dibutuhkan, disimpan di memori saja, dengan batas waktu. */
object GambarBerita {
    private val lru = object : LruCache<String, Bitmap>(24 * 1024 * 1024) { override fun sizeOf(k: String, v: Bitmap) = v.byteCount }
    private val sem = Semaphore(4)
    private val gagal = HashSet<String>()

    suspend fun ambil(url: String, sisi: Int = 800): Bitmap? {
        lru.get(url)?.let { return it }
        if (url in gagal) return null
        return try {
            sem.withPermit {
                val b = NewsFeed.unduh(url)
                val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(b, 0, b.size, o)
                var sample = 1
                while (o.outWidth / (sample * 2) >= sisi && o.outHeight / (sample * 2) >= sisi) sample *= 2
                BitmapFactory.decodeByteArray(b, 0, b.size, BitmapFactory.Options().apply { inSampleSize = sample })
            }?.also { lru.put(url, it) } ?: run { gagal.add(url); null }
        } catch (e: java.io.IOException) { null } catch (e: RuntimeException) { null }
    }
}

/** Kotak berlatar gambar berita; sebelum gambar datang (atau bila gagal) tampil warna cadangan. */
@Composable
fun GambarUrl(url: String, modifier: Modifier, cadangan: Color = Color(0xFFE9E3DD), scrim: Boolean = false, isi: @Composable BoxScope.(Boolean) -> Unit = {}) {
    val bmp by produceState<Bitmap?>(null, url) { value = if (url.isBlank()) null else GambarBerita.ambil(url) }
    Box(modifier.background(cadangan)) {
        bmp?.let {
            Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            if (scrim) Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x11000000), Color(0xCC000000)))))
        }
        isi(bmp != null)
    }
}
