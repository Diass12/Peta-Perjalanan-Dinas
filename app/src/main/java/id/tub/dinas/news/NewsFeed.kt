package id.tub.dinas.news

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.xml.parsers.DocumentBuilderFactory

enum class Tipe { RSS, BMKG_GEMPA }

data class Sumber(val nama: String, val url: String, val tipe: Tipe = Tipe.RSS, val kategori: String = "", val penerbit: String = "", val utama: Boolean = false)

/** Satu kabar: hanya judul, ringkasan pendek, dan tautan ke situs asli (tidak menyalin isi berita). */
data class Berita(val judul: String, val ringkas: String, val link: String, val waktu: String, val sumber: String, val gambar: String = "", val kategori: String = "", val penerbit: String = "", val ms: Long = 0L)

object NewsFeed {
    private fun r(kat: String, pen: String, url: String, utama: Boolean = false) = Sumber("$kat · $pen", url, Tipe.RSS, kat, pen, utama)
    /** Sumber bawaan. Tidak semua situs menjamin alamat RSS-nya tetap; sumber yang gagal dimuat dilewati. */
    val DEFAULT = listOf(
        r("Terkini", "Antara", "https://www.antaranews.com/rss/terkini.xml", true),
        r("Ekonomi", "Antara", "https://www.antaranews.com/rss/ekonomi.xml"),
        r("Teknologi", "Antara", "https://www.antaranews.com/rss/tekno.xml"),
        r("Olahraga", "Antara", "https://www.antaranews.com/rss/olahraga.xml"),
        r("Hiburan", "Antara", "https://www.antaranews.com/rss/hiburan.xml"),
        r("Internasional", "Antara", "https://www.antaranews.com/rss/dunia.xml"),
        r("Nasional", "CNN Indonesia", "https://www.cnnindonesia.com/nasional/rss", true),
        r("Ekonomi", "CNN Indonesia", "https://www.cnnindonesia.com/ekonomi/rss"),
        r("Teknologi", "CNN Indonesia", "https://www.cnnindonesia.com/teknologi/rss"),
        r("Internasional", "CNN Indonesia", "https://www.cnnindonesia.com/internasional/rss"),
        r("Olahraga", "CNN Indonesia", "https://www.cnnindonesia.com/olahraga/rss"),
        r("Hiburan", "CNN Indonesia", "https://www.cnnindonesia.com/hiburan/rss"),
        r("Ekonomi", "CNBC Indonesia", "https://www.cnbcindonesia.com/news/rss", true),
        r("Teknologi", "CNBC Indonesia", "https://www.cnbcindonesia.com/tech/rss"),
        r("Nasional", "Tempo", "https://rss.tempo.co/nasional", true),
        r("Ekonomi", "Tempo", "https://rss.tempo.co/bisnis"),
        r("Internasional", "Tempo", "https://rss.tempo.co/dunia"),
        r("Teknologi", "Tempo", "https://rss.tempo.co/tekno"),
        r("Terkini", "Detik", "https://news.detik.com/rss", true),
        r("Terkini", "BBC Indonesia", "https://feeds.bbci.co.uk/indonesia/rss.xml", true),
    )
    val GEMPA = Sumber("Gempa · BMKG", "https://data.bmkg.go.id/DataMKG/TEWS/autogempa.xml", Tipe.BMKG_GEMPA, "Gempa", "BMKG")
    const val BMKG_CUACA = "https://www.bmkg.go.id/"
    const val BMKG_GEMPA_HALAMAN = "https://www.bmkg.go.id/gempabumi/gempabumi-terkini.html"
    private const val MAX_BYTES = 2_000_000
    private const val MAX_ITEM = 25

    /** Sumber tambahan disimpan sebagai baris "nama|url". Hanya https yang diterima. */
    fun parseExtra(s: String): List<Sumber> = s.lines().mapNotNull { l ->
        val p = l.split('|', limit = 2)
        if (p.size == 2 && p[1].trim().startsWith("https://")) Sumber(p[0].trim().ifEmpty { host(p[1]) }, p[1].trim(), Tipe.RSS, "Lainnya", p[0].trim().ifEmpty { host(p[1]) }) else null
    }
    fun host(u: String): String = runCatching { URL(u).host.removePrefix("www.") }.getOrDefault(u)

    suspend fun ambil(s: Sumber): List<Berita> = parse(unduh(s.url), s)

    /** Mengunduh isi sebuah alamat https (maks 2 MB). */
    suspend fun unduh(url: String): ByteArray = withContext(Dispatchers.IO) {
        require(url.startsWith("https://")) { "Hanya tautan https yang diizinkan" }
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 10_000; c.readTimeout = 15_000
            c.setRequestProperty("User-Agent", "DokumentasiDinas/1.0 (pembaca RSS)")
            c.setRequestProperty("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml, application/json, */*")
            if (c.responseCode !in 200..299) throw java.io.IOException("Server membalas ${c.responseCode}")
            c.inputStream.use { it.readNBytesLimited(MAX_BYTES) }
        } finally { c.disconnect() }
    }

    private fun java.io.InputStream.readNBytesLimited(max: Int): ByteArray {
        val o = java.io.ByteArrayOutputStream(); val b = ByteArray(8192); var t = 0
        while (true) { val r = read(b); if (r < 0) break; t += r; if (t > max) throw java.io.IOException("Respons terlalu besar"); o.write(b, 0, r) }
        return o.toByteArray()
    }

    fun parse(bytes: ByteArray, s: Sumber): List<Berita> {
        val f = DocumentBuilderFactory.newInstance()
        runCatching { f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { f.setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { f.isExpandEntityReferences = false }
        val doc = f.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
        val root = doc.documentElement
        return if (s.tipe == Tipe.BMKG_GEMPA) bmkg(root, s) else rssAtom(root, s)
    }

    private fun Element.kids(name: String): List<Element> {
        val out = ArrayList<Element>(); var n: Node? = firstChild
        while (n != null) { if (n is Element && (n.localName ?: n.nodeName).equals(name, true)) out.add(n); n = n.nextSibling }
        return out
    }
    private fun Element.text(name: String): String = kids(name).firstOrNull()?.textContent?.trim().orEmpty()

    private fun rssAtom(root: Element, s: Sumber): List<Berita> {
        val items = ArrayList<Element>()
        fun walk(e: Element) {
            val nm = (e.localName ?: e.nodeName).lowercase()
            if (nm == "item" || nm == "entry") { items.add(e); return }
            var n: Node? = e.firstChild
            while (n != null) { if (n is Element) walk(n); n = n.nextSibling }
        }
        walk(root)
        return items.take(MAX_ITEM).mapNotNull { e ->
            val judul = bersih(e.text("title"))
            val link = e.text("link").ifEmpty { e.kids("link").firstOrNull()?.getAttribute("href").orEmpty() }.trim()
            if (judul.isEmpty() || !link.startsWith("http")) return@mapNotNull null
            val ring = bersih(e.text("description").ifEmpty { e.text("summary") }).take(220)
            val waktuMentah = e.text("pubDate").ifEmpty { e.text("updated") }.ifEmpty { e.text("published") }.ifEmpty { e.text("dc:date") }
            val ms = parseWaktu(waktuMentah)
            Berita(judul, ring, link, if (ms > 0) rapi(ms) else waktuMentah, s.nama, gambar(e), s.kategori, s.penerbit.ifEmpty { s.nama }, ms)
        }
    }

    /** Mencari alamat gambar sampul: media:content, media:thumbnail, enclosure, lalu tag img pertama di isi. */
    private fun gambar(e: Element): String {
        fun https(u: String) = u.trim().replace("&amp;", "&").let { if (it.startsWith("http://")) "https://" + it.removePrefix("http://") else it }
        for (nm in listOf("media:content", "media:thumbnail", "enclosure")) for (k in e.kids(nm)) {
            val u = k.getAttribute("url"); val t = k.getAttribute("type").lowercase(); val medium = k.getAttribute("medium").lowercase()
            val gambar = t.startsWith("image") || medium == "image" || nm == "media:thumbnail" || Regex("\\.(jpe?g|png|webp)(\\?|$)").containsMatchIn(u.lowercase())
            if (u.startsWith("http") && gambar) return https(u)
        }
        e.text("img").takeIf { it.startsWith("http") }?.let { return https(it) } // format Tempo: <img>alamat</img>
        val html = e.text("description") + " " + e.text("content:encoded") + " " + e.text("content")
        return Regex("<img[^>]+src=[\"']([^\"']+)").find(html)?.groupValues?.get(1)?.takeIf { it.startsWith("http") }?.let { https(it) }.orEmpty()
    }

    private val FORMAT = listOf("EEE, dd MMM yyyy HH:mm:ss Z", "EEE, d MMM yyyy HH:mm:ss Z", "EEE, dd MMM yyyy HH:mm:ss zzz", "yyyy-MM-dd'T'HH:mm:ssXXX", "yyyy-MM-dd'T'HH:mm:ss'Z'", "yyyy-MM-dd HH:mm:ss")
    fun parseWaktu(t: String): Long {
        if (t.isBlank()) return 0L
        for (f in FORMAT) runCatching { return java.text.SimpleDateFormat(f, java.util.Locale.US).parse(t.trim())?.time ?: 0L }
        return 0L
    }
    fun rapi(ms: Long): String = java.text.SimpleDateFormat("d MMM yyyy HH:mm", java.util.Locale("id", "ID")).apply { timeZone = java.util.TimeZone.getTimeZone("Asia/Jakarta") }.format(java.util.Date(ms)) + " WIB"

    private fun bmkg(root: Element, s: Sumber): List<Berita> {
        val g = root.kids("gempa").firstOrNull() ?: return emptyList()
        val mag = g.text("Magnitude"); val wil = g.text("Wilayah")
        if (wil.isEmpty()) return emptyList()
        val ring = listOf(g.text("Tanggal") + " " + g.text("Jam"), "Kedalaman ${g.text("Kedalaman")}", g.text("Potensi"), g.text("Dirasakan"))
            .map { it.trim() }.filter { it.isNotEmpty() && it != "Kedalaman" }.joinToString(" · ")
        return listOf(Berita("Gempa M$mag — $wil", ring, BMKG_GEMPA_HALAMAN, g.text("DateTime"), s.nama, "", "Gempa", "BMKG", parseWaktu(g.text("DateTime"))))
    }

    /** Membuang tag HTML dan entitas umum dari ringkasan. */
    fun bersih(h: String): String = h.replace(Regex("<[^>]*>"), " ")
        .replace("&nbsp;", " ").replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">")
        .replace(Regex("\\s+"), " ").trim()
}
