package id.tub.dinas.news

import id.tub.dinas.data.backup.MiniJson
import java.net.URLEncoder

data class Karya(val judul: String, val ringkas: String, val link: String)

/** Karya domain publik di Wikisource bahasa Indonesia (API resmi MediaWiki). Hanya judul, cuplikan, dan tautan. */
object Wikisource {
    const val BERANDA = "https://id.wikisource.org/wiki/Halaman_Utama"

    suspend fun cari(kata: String): List<Karya> {
        val q = URLEncoder.encode(kata.trim().ifEmpty { "novel" }, "UTF-8")
        val url = "https://id.wikisource.org/w/api.php?action=query&list=search&srsearch=$q&srnamespace=0&srlimit=30&format=json"
        return parse(String(NewsFeed.unduh(url), Charsets.UTF_8))
    }

    fun parse(json: String): List<Karya> {
        @Suppress("UNCHECKED_CAST")
        val root = MiniJson.parse(json) as? Map<String, Any?> ?: return emptyList()
        @Suppress("UNCHECKED_CAST")
        val list = ((root["query"] as? Map<String, Any?>)?.get("search") as? List<Any?>) ?: return emptyList()
        return list.mapNotNull { x ->
            @Suppress("UNCHECKED_CAST")
            val o = x as? Map<String, Any?> ?: return@mapNotNull null
            val t = (o["title"] as? String)?.trim().orEmpty()
            if (t.isEmpty()) return@mapNotNull null
            Karya(t, NewsFeed.bersih((o["snippet"] as? String).orEmpty()).take(200), "https://id.wikisource.org/wiki/" + URLEncoder.encode(t.replace(' ', '_'), "UTF-8").replace("+", "%20"))
        }
    }
}
