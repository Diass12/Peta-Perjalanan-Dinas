package id.tub.dinas.news

import id.tub.dinas.data.backup.MiniJson
import java.net.URLEncoder

/** Buku bahasa Inggris domain publik dari Project Gutenberg (lewat API Gutendex). Hanya judul, penulis, dan tautan. */
object Gutenberg {
    const val BERANDA = "https://www.gutenberg.org/"

    suspend fun cari(kata: String): List<Karya> {
        val k = kata.trim()
        val q = if (k.isEmpty()) "" else "&search=" + URLEncoder.encode(k, "UTF-8")
        return parse(String(NewsFeed.unduh("https://gutendex.com/books/?languages=en$q"), Charsets.UTF_8))
    }

    fun parse(json: String): List<Karya> {
        @Suppress("UNCHECKED_CAST")
        val root = MiniJson.parse(json) as? Map<String, Any?> ?: return emptyList()
        @Suppress("UNCHECKED_CAST")
        val list = root["results"] as? List<Any?> ?: return emptyList()
        return list.take(30).mapNotNull { x ->
            @Suppress("UNCHECKED_CAST")
            val o = x as? Map<String, Any?> ?: return@mapNotNull null
            val id = (o["id"] as? Number)?.toLong() ?: return@mapNotNull null
            val t = (o["title"] as? String)?.trim().orEmpty()
            if (t.isEmpty()) return@mapNotNull null
            @Suppress("UNCHECKED_CAST")
            val pen = (o["authors"] as? List<Any?>)?.mapNotNull { (it as? Map<String, Any?>)?.get("name") as? String }?.joinToString("; ").orEmpty()
            Karya(t, pen, "https://www.gutenberg.org/ebooks/$id")
        }
    }
}
