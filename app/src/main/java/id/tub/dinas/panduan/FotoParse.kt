package id.tub.dinas.panduan

import id.tub.dinas.data.backup.MiniJson

/** Foto terpilih dari Wikipedia + data atribusinya (Wikimedia Commons). */
data class FotoHasil(val url: String, val berkas: String, val judul: String, val kredit: String = "")

/** Logika murni (tanpa Android) untuk memilih foto dari hasil API Wikipedia. Diuji lewat kotlinc. */
object FotoParse {
    private val UMUM = setOf("pantai", "pura", "gunung", "pulau", "candi", "taman", "kota", "jalan", "danau", "masjid", "benteng", "museum",
        "sate", "nasi", "mie", "mi", "kue", "ikan", "ayam", "air", "terjun", "desa", "kampung", "pasar", "bukit", "teluk", "kepulauan",
        "kebun", "binatang", "kawasan", "tugu", "istana", "keraton", "vihara", "klenteng", "dan", "di", "the", "of", "wisata", "sop", "soto", "es", "bubur", "kopi", "kuah")
    private val TOLAK = Regex("(?i)(map|peta|logo|flag|lambang|locator|location|diagram|coat_of_arms|icon|\\.svg$)")

    fun token(s: String): List<String> = s.lowercase().split(Regex("[^a-z0-9]+")).filter { it.length >= 3 }

    fun kunciToken(nama: String): List<String> {
        val t = token(nama)
        val khas = t.filter { it !in UMUM }
        return khas.ifEmpty { t }
    }

    @Suppress("UNCHECKED_CAST")
    private fun m(v: Any?) = v as? Map<String, Any?>

    /** Pilih halaman terbaik yang punya gambar & judulnya cocok dengan nama. */
    fun pilih(json: String, nama: String): FotoHasil? {
        val root = runCatching { m(MiniJson.parse(json)) }.getOrNull() ?: return null
        val pages = m(m(root["query"])?.get("pages")) ?: return null
        val kunci = kunciToken(nama)
        val urut = pages.values.mapNotNull { m(it) }.sortedBy { (it["index"] as? Number)?.toInt() ?: 99 }
        for (p in urut) {
            val judul = p["title"] as? String ?: continue
            val th = m(p["thumbnail"]) ?: continue
            val url = th["source"] as? String ?: continue
            val berkas = p["pageimage"] as? String ?: continue
            if (TOLAK.containsMatchIn(berkas)) continue
            val tj = token(judul)
            if (kunci.isNotEmpty() && kunci.none { k -> tj.any { it == k || it.startsWith(k) || k.startsWith(it) && it.length >= 4 } }) continue
            return FotoHasil(url, berkas, judul)
        }
        return null
    }

    private fun bersih(html: String) = html.replace(Regex("<[^>]*>"), "").replace("&amp;", "&").replace("&quot;", "\"").replace("&#039;", "'").replace(Regex("\\s+"), " ").trim()

    /** Ambil penulis + lisensi dari Commons; null bila data tidak ada atau berlisensi non-bebas. */
    fun kredit(json: String): String? {
        val root = runCatching { m(MiniJson.parse(json)) }.getOrNull() ?: return null
        val pages = m(m(root["query"])?.get("pages")) ?: return null
        val info = (pages.values.firstOrNull()?.let { m(it) }?.get("imageinfo") as? List<*>)?.firstOrNull()?.let { m(it) } ?: return null
        val meta = m(info["extmetadata"]) ?: return null
        fun nilai(k: String) = (m(meta[k])?.get("value") as? String)?.let { bersih(it) }.orEmpty()
        if (nilai("NonFree").equals("true", true)) return null
        val lisensi = nilai("LicenseShortName")
        if (lisensi.isEmpty()) return null
        val artis = nilai("Artist").ifEmpty { "Kontributor Wikimedia" }.take(60)
        return "$artis · $lisensi"
    }
}
