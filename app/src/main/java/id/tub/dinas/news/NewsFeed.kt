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

data class Sumber(val nama: String, val url: String, val tipe: Tipe = Tipe.RSS)

/** Satu kabar: hanya judul, ringkasan pendek, dan tautan ke situs asli (tidak menyalin isi berita). */
data class Berita(val judul: String, val ringkas: String, val link: String, val waktu: String, val sumber: String)

object NewsFeed {
    val DEFAULT = listOf(
        Sumber("Ekonomi · Antara", "https://www.antaranews.com/rss/ekonomi.xml"),
        Sumber("Terkini · Antara", "https://www.antaranews.com/rss/terkini.xml"),
        Sumber("Gempa · BMKG", "https://data.bmkg.go.id/DataMKG/TEWS/autogempa.xml", Tipe.BMKG_GEMPA),
    )
    const val BMKG_CUACA = "https://www.bmkg.go.id/cuaca/prakiraan-cuaca"
    const val BMKG_GEMPA_HALAMAN = "https://www.bmkg.go.id/gempabumi/gempabumi-terkini"
    private const val MAX_BYTES = 2_000_000
    private const val MAX_ITEM = 25

    /** Sumber tambahan disimpan sebagai baris "nama|url". Hanya https yang diterima. */
    fun parseExtra(s: String): List<Sumber> = s.lines().mapNotNull { l ->
        val p = l.split('|', limit = 2)
        if (p.size == 2 && p[1].trim().startsWith("https://")) Sumber(p[0].trim().ifEmpty { host(p[1]) }, p[1].trim()) else null
    }
    fun host(u: String): String = runCatching { URL(u).host.removePrefix("www.") }.getOrDefault(u)

    suspend fun ambil(s: Sumber): List<Berita> = withContext(Dispatchers.IO) {
        require(s.url.startsWith("https://")) { "Hanya tautan https yang diizinkan" }
        val c = URL(s.url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 10_000; c.readTimeout = 15_000
            c.setRequestProperty("User-Agent", "DokumentasiDinas/1.0 (pembaca RSS)")
            c.setRequestProperty("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml, */*")
            if (c.responseCode !in 200..299) throw java.io.IOException("Server membalas ${c.responseCode}")
            val bytes = c.inputStream.use { it.readNBytesLimited(MAX_BYTES) }
            parse(bytes, s)
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
            val waktu = e.text("pubDate").ifEmpty { e.text("updated") }.ifEmpty { e.text("published") }
            Berita(judul, ring, link, waktu, s.nama)
        }
    }

    private fun bmkg(root: Element, s: Sumber): List<Berita> {
        val g = root.kids("gempa").firstOrNull() ?: return emptyList()
        val mag = g.text("Magnitude"); val wil = g.text("Wilayah")
        if (wil.isEmpty()) return emptyList()
        val ring = listOf(g.text("Tanggal") + " " + g.text("Jam"), "Kedalaman ${g.text("Kedalaman")}", g.text("Potensi"), g.text("Dirasakan"))
            .map { it.trim() }.filter { it.isNotEmpty() && it != "Kedalaman" }.joinToString(" · ")
        return listOf(Berita("Gempa M$mag — $wil", ring, BMKG_GEMPA_HALAMAN, g.text("DateTime"), s.nama))
    }

    /** Membuang tag HTML dan entitas umum dari ringkasan. */
    fun bersih(h: String): String = h.replace(Regex("<[^>]*>"), " ")
        .replace("&nbsp;", " ").replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">")
        .replace(Regex("\\s+"), " ").trim()
}
