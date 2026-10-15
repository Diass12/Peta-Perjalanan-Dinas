package id.tub.dinas.data

import id.tub.dinas.domain.CoordParser
import id.tub.dinas.domain.GeoPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder

/** Membaca lokasi dari tautan Google Maps, termasuk tautan pendek hasil tombol Bagikan (butuh internet). */
object MapsLink {
    private val URL_RE = Regex("""https?://[^\s]+""")
    private val INIT_RE = Regex("""\[\[\[\d+(?:\.\d+)?,(-?\d+\.\d+),(-?\d+\.\d+)\]""")

    fun firstUrl(text: String): String? = URL_RE.find(text)?.value?.trimEnd(')', '.', ',')

    private fun allowed(u: String): Boolean = try {
        val url = URL(u)
        val h = url.host.lowercase()
        url.protocol == "https" && (h == "goo.gl" || h == "g.co" || h.endsWith(".goo.gl") || h == "google.com" || h.endsWith(".google.com") || h.endsWith(".google.co.id"))
    } catch (_: Exception) { false }

    fun isMapsLink(text: String): Boolean = firstUrl(text)?.let { allowed(it) } == true

    /** Nama tempat bila teks berbentuk "Nama tempat\nhttps://…". */
    fun placeName(text: String): String {
        val u = firstUrl(text)
        val head = (if (u != null) text.substringBefore(u) else text).trim().lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        return head.take(40).ifEmpty { "Titik dari Maps" }
    }

    private fun dec(s: String) = try { URLDecoder.decode(s, "UTF-8") } catch (_: Exception) { s }

    /** Koordinat dari teks langsung; bila berupa tautan Google, ikuti pengalihannya. Gagal = null. */
    suspend fun resolve(text: String): GeoPoint? = withContext(Dispatchers.IO) {
        CoordParser.parse(text)?.let { return@withContext it }
        var url = firstUrl(text)?.takeIf { allowed(it) } ?: return@withContext null
        CoordParser.parse(dec(url))?.let { return@withContext it.copy(source = "gmaps") }
        try {
            repeat(6) {
                val c = URL(url).openConnection() as HttpURLConnection
                c.instanceFollowRedirects = false
                c.connectTimeout = 8000; c.readTimeout = 8000
                c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
                val code = c.responseCode
                if (code in 300..399) {
                    val loc = c.getHeaderField("Location") ?: return@withContext null
                    var next = URL(URL(url), loc).toString()
                    if (next.contains("consent.google") && next.contains("continue=")) next = dec(next.substringAfter("continue=").substringBefore("&"))
                    if (!allowed(next)) return@withContext null
                    CoordParser.parse(dec(next))?.let { return@withContext it.copy(source = "gmaps") }
                    url = next
                } else {
                    val body = c.inputStream.use { s ->
                        val buf = ByteArray(300_000); var n = 0
                        while (n < buf.size) { val r = s.read(buf, n, buf.size - n); if (r < 0) break; n += r }
                        String(buf, 0, n, Charsets.UTF_8)
                    }
                    CoordParser.parse(dec(body.take(20000)))?.let { return@withContext it.copy(source = "gmaps") }
                    INIT_RE.find(body)?.let { m ->
                        val lng = m.groupValues[1].toDouble(); val lat = m.groupValues[2].toDouble()
                        if (lat in -90.0..90.0 && lng in -180.0..180.0) return@withContext GeoPoint(lat, lng, null, "gmaps")
                    }
                    return@withContext null
                }
            }
            null
        } catch (_: Exception) { null }
    }
}
