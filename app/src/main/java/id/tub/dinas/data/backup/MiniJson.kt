package id.tub.dinas.data.backup

/** JSON kecil tanpa dependensi: Map<String,Any?>, List<Any?>, String, Double/Long, Boolean, null. */
object MiniJson {
    fun parse(text: String): Any? {
        val p = Parser(text)
        p.ws()
        val v = p.value(0)
        p.ws()
        if (p.i != text.length) throw IllegalArgumentException("Sisa teks setelah JSON")
        return v
    }

    fun write(v: Any?): String = StringBuilder().also { w(it, v) }.toString()

    private fun w(sb: StringBuilder, v: Any?) {
        when (v) {
            null -> sb.append("null")
            is Boolean -> sb.append(v)
            is Int, is Long -> sb.append(v.toString())
            is Double -> { require(v.isFinite()); sb.append(if (v == Math.floor(v) && Math.abs(v) < 1e15) v.toLong().toString() else v.toString()) }
            is Float -> w(sb, v.toDouble())
            is String -> str(sb, v)
            is Map<*, *> -> { sb.append('{'); var f = true; for ((k, x) in v) { if (!f) sb.append(','); f = false; str(sb, k.toString()); sb.append(':'); w(sb, x) }; sb.append('}') }
            is List<*> -> { sb.append('['); var f = true; for (x in v) { if (!f) sb.append(','); f = false; w(sb, x) }; sb.append(']') }
            else -> throw IllegalArgumentException("Tipe tidak didukung: ${v::class}")
        }
    }

    private fun str(sb: StringBuilder, s: String) {
        sb.append('"')
        for (c in s) when {
            c == '"' -> sb.append("\\\"")
            c == '\\' -> sb.append("\\\\")
            c == '\n' -> sb.append("\\n")
            c == '\r' -> sb.append("\\r")
            c == '\t' -> sb.append("\\t")
            c < ' ' -> sb.append(String.format("\\u%04x", c.code))
            else -> sb.append(c)
        }
        sb.append('"')
    }

    private class Parser(val s: String) {
        var i = 0
        fun ws() { while (i < s.length && s[i].isWhitespace()) i++ }
        fun value(depth: Int): Any? {
            if (depth > 64) throw IllegalArgumentException("JSON terlalu dalam")
            if (i >= s.length) throw IllegalArgumentException("JSON berakhir tiba-tiba")
            return when (val c = s[i]) {
                '{' -> obj(depth)
                '[' -> arr(depth)
                '"' -> string()
                't' -> lit("true", true)
                'f' -> lit("false", false)
                'n' -> lit("null", null)
                else -> if (c == '-' || c.isDigit()) num() else throw IllegalArgumentException("Karakter tak terduga '$c' di $i")
            }
        }
        fun lit(w: String, v: Any?): Any? { if (!s.startsWith(w, i)) throw IllegalArgumentException("Literal salah di $i"); i += w.length; return v }
        fun num(): Any {
            val st = i
            if (s[i] == '-') i++
            while (i < s.length && (s[i].isDigit() || s[i] in ".eE+-")) i++
            val t = s.substring(st, i)
            return if (t.any { it in ".eE" }) t.toDouble() else (t.toLongOrNull() ?: t.toDouble())
        }
        fun string(): String {
            i++
            val sb = StringBuilder()
            while (true) {
                if (i >= s.length) throw IllegalArgumentException("String tidak ditutup")
                val c = s[i++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (i >= s.length) throw IllegalArgumentException("Escape tidak lengkap")
                        when (val e = s[i++]) {
                            'n' -> sb.append('\n'); 'r' -> sb.append('\r'); 't' -> sb.append('\t')
                            'b' -> sb.append('\b'); 'f' -> sb.append('\u000c')
                            '/', '\\', '"' -> sb.append(e)
                            'u' -> { if (i + 4 > s.length) throw IllegalArgumentException("\\u tidak lengkap"); sb.append(s.substring(i, i + 4).toInt(16).toChar()); i += 4 }
                            else -> throw IllegalArgumentException("Escape tidak dikenal")
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }
        fun arr(depth: Int): List<Any?> {
            i++; val l = ArrayList<Any?>(); ws()
            if (s[i] == ']') { i++; return l }
            while (true) {
                ws(); l.add(value(depth + 1)); ws()
                if (i >= s.length) throw IllegalArgumentException("Array tidak ditutup")
                if (s[i] == ',') { i++; continue }
                if (s[i] == ']') { i++; return l }
                throw IllegalArgumentException("Array salah di $i")
            }
        }
        fun obj(depth: Int): Map<String, Any?> {
            i++; val m = LinkedHashMap<String, Any?>(); ws()
            if (s[i] == '}') { i++; return m }
            while (true) {
                ws(); if (i >= s.length || s[i] != '"') throw IllegalArgumentException("Kunci objek salah di $i")
                val k = string(); ws()
                if (i >= s.length || s[i] != ':') throw IllegalArgumentException("Titik dua hilang di $i")
                i++; ws(); m[k] = value(depth + 1); ws()
                if (i >= s.length) throw IllegalArgumentException("Objek tidak ditutup")
                if (s[i] == ',') { i++; continue }
                if (s[i] == '}') { i++; return m }
                throw IllegalArgumentException("Objek salah di $i")
            }
        }
    }
}
