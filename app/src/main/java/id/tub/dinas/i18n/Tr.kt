package id.tub.dinas.i18n

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class AppLang(val code: String, val nama: String) {
    ID("id", "Indonesia"), EN("en", "English"), ZH("zh", "中文"), JA("ja", "日本語"), KO("ko", "한국어");
    companion object { fun from(s: String?) = entries.firstOrNull { it.code == s } ?: ID }
}

/**
 * Kamus terjemahan: baris "Indonesia|English|中文|日本語|한국어". Teks kode ditulis dalam bahasa Indonesia;
 * teks yang persis ada di kamus diganti utuh. Teks dinamis (mengandung angka, ":" atau "·") diterjemahkan per frasa.
 * Baris berawalan "~" hanya dipakai pada mode per frasa (bentuk tunggal, mis. "Temuan 3" -> "Finding 3").
 */
class Dict(tsv: String) {
    private val exact = HashMap<String, Array<String>>()
    private val frasa = HashMap<String, Array<String>>()
    private val urut: List<String>
    init {
        for (line in tsv.lineSequence()) {
            val p = line.split('|')
            if (p.size != 5) continue
            val k = p[0]; val v = arrayOf(p[1], p[2], p[3], p[4])
            if (k.startsWith("~")) frasa[k.substring(1)] = v
            else { exact[k] = v; if (k.length >= 3 && k.none { it == ' ' } || k.length >= 4) frasa.putIfAbsent(k, v) }
        }
        urut = frasa.keys.sortedByDescending { it.length }
    }

    fun terjemah(s: String, lang: AppLang): String {
        if (lang == AppLang.ID || s.isBlank()) return s
        val i = lang.ordinal - 1
        exact[s]?.let { return it[i] }
        val t = s.trim()
        if (t.length != s.length) exact[t]?.let { return s.substringBefore(t) + it[i] + s.substringAfterLast(t) }
        val dinamis = s.any { it.isDigit() } || s.contains(" · ") || s.contains(':') || s.contains('/')
        if (!dinamis) return s
        var out = s
        for (k in urut) {
            if (!out.contains(k)) continue
            val v = frasa[k]!![i]
            out = Regex("(?<![\\p{L}\\p{N}])" + Regex.escape(k) + "(?![\\p{L}\\p{N}])").replace(out) { v }
        }
        return out
    }
}

object Tr {
    /** Bahasa aktif; dibaca dari komposisi sehingga teks otomatis tergambar ulang saat diganti. */
    var lang by mutableStateOf(AppLang.ID)
    private var dict: Dict? = null
    private val cache = HashMap<String, String>()

    fun init(ctx: Context) {
        if (dict != null) return
        dict = runCatching { Dict(ctx.applicationContext.assets.open("i18n.tsv").bufferedReader(Charsets.UTF_8).use { it.readText() }) }.getOrNull()
    }

    fun t(s: String): String {
        val l = lang
        if (l == AppLang.ID) return s
        val d = dict ?: return s
        val key = l.code + "\u0000" + s
        cache[key]?.let { return it }
        val r = d.terjemah(s, l)
        if (cache.size > 4000) cache.clear()
        cache[key] = r
        return r
    }
}
