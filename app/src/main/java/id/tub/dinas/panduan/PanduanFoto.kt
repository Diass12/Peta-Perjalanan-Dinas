package id.tub.dinas.panduan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
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
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Foto asli dari Wikipedia/Wikimedia Commons: dimuat saat dibutuhkan, di-cache di HP, dengan batas waktu agar tidak menggantung. */
object PanduanFoto {
    class Isi(val bmp: Bitmap, val kredit: String, val berkas: String)

    private const val UA = "DokumentasiDinas/1.5 (https://github.com/Diass12/Peta-Perjalanan-Dinas)"
    private const val TIDAK_ADA_MS = 3L * 24 * 3600 * 1000
    private val sem = Semaphore(3)
    private val lru = LruCache<String, Isi>(48)

    private fun dir(ctx: Context) = File(ctx.cacheDir, "panduan").also { it.mkdirs() }
    private fun aman(k: String) = k.replace(Regex("[^A-Za-z0-9_-]"), "_")

    private fun http(url: String): ByteArray {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 8000; c.readTimeout = 8000
            c.setRequestProperty("User-Agent", UA)
            if (c.responseCode !in 200..299) throw IOException("HTTP ${c.responseCode}")
            return c.inputStream.use { it.readBytes() }
        } finally { c.disconnect() }
    }

    private fun cariWiki(bahasa: String, q: String, nama: String): FotoHasil? {
        val u = "https://$bahasa.wikipedia.org/w/api.php?action=query&format=json&generator=search&gsrsearch=${Uri.encode(q)}" +
            "&gsrlimit=5&gsrnamespace=0&prop=pageimages&piprop=thumbnail%7Cname&pithumbsize=640&pilimit=5"
        return FotoParse.pilih(String(http(u)), nama)
    }

    private fun ambilJaringan(ctx: Context, kunci: String, query: String, nama: String): Isi? {
        val f = cariWiki("id", query, nama) ?: cariWiki("en", query, nama) ?: return null
        val kr = FotoParse.kredit(String(http("https://commons.wikimedia.org/w/api.php?action=query&format=json&prop=imageinfo&iiprop=extmetadata" +
            "&iiextmetadatafilter=Artist%7CLicenseShortName%7CNonFree&titles=" + Uri.encode("File:" + f.berkas.replace('_', ' '))))) ?: return null
        val bytes = http(f.url)
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        val d = dir(ctx); val a = aman(kunci)
        File(d, "$a.jpg").writeBytes(bytes)
        File(d, "$a.txt").writeText(f.berkas + "\n" + kr)
        return Isi(bmp, kr, f.berkas)
    }

    suspend fun ambil(ctx: Context, kunci: String, query: String, nama: String): Isi? {
        lru.get(kunci)?.let { return it }
        return withContext(Dispatchers.IO) {
            val d = dir(ctx); val a = aman(kunci)
            val jpg = File(d, "$a.jpg"); val txt = File(d, "$a.txt"); val none = File(d, "$a.none")
            if (jpg.exists() && txt.exists()) {
                val l = txt.readLines()
                val bmp = BitmapFactory.decodeFile(jpg.path)
                if (bmp != null && l.size >= 2) return@withContext Isi(bmp, l[1], l[0]).also { lru.put(kunci, it) }
            }
            if (none.exists() && System.currentTimeMillis() - none.lastModified() < TIDAK_ADA_MS) return@withContext null
            try {
                val r = sem.withPermit { ambilJaringan(ctx, kunci, query, nama) }
                if (r == null) none.writeText("x") else lru.put(kunci, r)
                r
            } catch (e: IOException) { null } // sinyal buruk/time out: jangan dicatat "tidak ada", coba lagi nanti
            catch (e: RuntimeException) { null }
        }
    }
}

/** Kotak dengan foto asli sebagai latar; sebelum foto datang (atau bila gagal) tampil gradasi. [isi] digambar di atas foto. */
@Composable
fun FotoLatar(kunci: String, query: String, nama: String, tint: Color, modifier: Modifier = Modifier, isi: @Composable BoxScope.(PanduanFoto.Isi?) -> Unit) {
    val ctx = LocalContext.current.applicationContext
    val foto by produceState<PanduanFoto.Isi?>(null, kunci) { value = PanduanFoto.ambil(ctx, kunci, query, nama) }
    Box(modifier.background(Brush.linearGradient(listOf(tint, Color(0xFF12202B))))) {
        foto?.let {
            Image(it.bmp.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x22000000), Color(0xCC000000)))))
        }
        isi(foto)
    }
}

/** Ringkasan singkat dari Wikipedia untuk tempat yang tidak ada di data bawaan. */
object PanduanWeb {
    class Ringkas(val judul: String, val isi: String)

    @Suppress("UNCHECKED_CAST")
    suspend fun ringkas(q: String): Ringkas? = withContext(Dispatchers.IO) {
        for (bhs in listOf("id", "en")) {
            val c = java.net.URL("https://$bhs.wikipedia.org/w/api.php?action=query&format=json&generator=search&gsrsearch=${Uri.encode(q)}" +
                "&gsrlimit=1&gsrnamespace=0&prop=extracts&exintro=1&explaintext=1&exsentences=4").openConnection() as HttpURLConnection
            try {
                c.connectTimeout = 8000; c.readTimeout = 8000; c.setRequestProperty("User-Agent", "DokumentasiDinas/1.5 (https://github.com/Diass12/Peta-Perjalanan-Dinas)")
                if (c.responseCode !in 200..299) continue
                val root = id.tub.dinas.data.backup.MiniJson.parse(String(c.inputStream.use { it.readBytes() })) as? Map<String, Any?> ?: continue
                val p = ((root["query"] as? Map<String, Any?>)?.get("pages") as? Map<String, Any?>)?.values?.firstOrNull() as? Map<String, Any?> ?: continue
                val t = p["title"] as? String ?: continue
                val e = (p["extract"] as? String)?.trim().orEmpty()
                if (e.isNotEmpty() && FotoParse.kunciToken(q).any { k -> FotoParse.token(t).any { it.startsWith(k) || k.startsWith(it) } }) return@withContext Ringkas(t, e)
            } catch (e: IOException) { throw e } finally { c.disconnect() }
        }
        null
    }
}
