package id.tub.dinas.aset

import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.time.LocalDate
import java.util.zip.ZipFile
import javax.xml.parsers.SAXParserFactory

class XCell(val text: String, val num: Double?, val date: Boolean)

/** `rows`: baris (0-based) -> kolom (0-based) -> sel. `images`: baris jangkar (0-based) -> nama entri gambar di dalam zip. */
class XSheet(val name: String, val rows: Map<Int, Map<Int, XCell>>, val images: Map<Int, List<String>>)

/** Pembaca .xlsx ringan (OOXML) tanpa dependensi: teks, angka, tanggal, dan gambar yang tertanam di sel. */
object XlsxReader {
    private const val NS_R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    private const val MAX_XML = 80L * 1024 * 1024
    private const val MAX_IMAGE = 30L * 1024 * 1024
    private const val MAX_ROWS = 20000
    private const val MAX_COLS = 200

    fun colIndex(ref: String): Int {
        var c = 0
        for (ch in ref) { if (ch in 'A'..'Z') c = c * 26 + (ch - 'A' + 1) else break }
        return c - 1
    }
    private fun rowIndex(ref: String): Int = ref.dropWhile { it in 'A'..'Z' }.toIntOrNull()?.minus(1) ?: -1

    private fun parser(input: InputStream, h: DefaultHandler) {
        val f = SAXParserFactory.newInstance()
        f.isNamespaceAware = true
        for ((k, v) in listOf(
            "http://xml.org/sax/features/external-general-entities" to false,
            "http://xml.org/sax/features/external-parameter-entities" to false,
        )) runCatching { f.setFeature(k, v) }
        f.newSAXParser().parse(InputSource(input), h)
    }

    private fun limited(i: InputStream, max: Long): InputStream = object : java.io.FilterInputStream(i) {
        var n = 0L
        override fun read(): Int { val b = super.read(); if (b >= 0 && ++n > max) throw IOException("Berkas di dalam xlsx terlalu besar"); return b }
        override fun read(b: ByteArray, off: Int, len: Int): Int { val r = super.read(b, off, len); if (r > 0) { n += r; if (n > max) throw IOException("Berkas di dalam xlsx terlalu besar") }; return r }
    }

    private fun resolve(baseDir: String, target: String): String {
        if (target.startsWith("/")) return target.trimStart('/')
        val parts = ArrayList<String>()
        (if (baseDir.isEmpty()) emptyList() else baseDir.split('/')).forEach { parts.add(it) }
        for (p in target.split('/')) when (p) { "", "." -> {}; ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex); else -> parts.add(p) }
        return parts.joinToString("/")
    }

    private class Rel(val id: String, val type: String, val target: String)

    private fun rels(z: ZipFile, path: String): List<Rel> {
        val e = z.getEntry(path) ?: return emptyList()
        val out = ArrayList<Rel>()
        z.getInputStream(e).use { s ->
            parser(limited(s, MAX_XML), object : DefaultHandler() {
                override fun startElement(u: String?, l: String?, q: String?, a: Attributes) {
                    if (l == "Relationship") out.add(Rel(a.getValue("Id") ?: "", a.getValue("Type") ?: "", a.getValue("Target") ?: ""))
                }
            })
        }
        return out
    }

    private fun relsPathOf(part: String): String {
        val dir = part.substringBeforeLast('/', "")
        val name = part.substringAfterLast('/')
        return (if (dir.isEmpty()) "" else "$dir/") + "_rels/$name.rels"
    }

    fun read(file: File): List<XSheet> = ZipFile(file).use { z ->
        if (z.getEntry("xl/workbook.xml") == null) throw IOException("Bukan berkas .xlsx yang valid")

        // buku kerja
        data class SheetRef(val name: String, val rid: String)
        val refs = ArrayList<SheetRef>()
        var date1904 = false
        z.getInputStream(z.getEntry("xl/workbook.xml")).use { s ->
            parser(limited(s, MAX_XML), object : DefaultHandler() {
                override fun startElement(u: String?, l: String?, q: String?, a: Attributes) {
                    if (l == "sheet") refs.add(SheetRef(a.getValue("name") ?: "Sheet", a.getValue(NS_R, "id") ?: ""))
                    if (l == "workbookPr") date1904 = a.getValue("date1904") == "1" || a.getValue("date1904") == "true"
                }
            })
        }
        val wbRels = rels(z, "xl/_rels/workbook.xml.rels").associateBy { it.id }

        // string bersama
        val shared = ArrayList<String>()
        z.getEntry("xl/sharedStrings.xml")?.let { e ->
            z.getInputStream(e).use { s ->
                parser(limited(s, MAX_XML), object : DefaultHandler() {
                    var sb: StringBuilder? = null; var inT = false; var ph = 0
                    override fun startElement(u: String?, l: String?, q: String?, a: Attributes) {
                        when (l) { "si" -> sb = StringBuilder(); "rPh" -> ph++; "t" -> if (ph == 0 && sb != null) inT = true }
                    }
                    override fun characters(ch: CharArray, start: Int, length: Int) { if (inT) sb?.append(ch, start, length) }
                    override fun endElement(u: String?, l: String?, q: String?) {
                        when (l) { "t" -> inT = false; "rPh" -> ph--; "si" -> { shared.add(sb.toString()); sb = null } }
                    }
                })
            }
        }

        // gaya: sel mana yang berformat tanggal
        val dateXf = ArrayList<Boolean>()
        z.getEntry("xl/styles.xml")?.let { e ->
            val custom = HashMap<Int, String>()
            val xfFmt = ArrayList<Int>()
            z.getInputStream(e).use { s ->
                parser(limited(s, MAX_XML), object : DefaultHandler() {
                    var inXfs = false
                    override fun startElement(u: String?, l: String?, q: String?, a: Attributes) {
                        when (l) {
                            "numFmt" -> custom[a.getValue("numFmtId")?.toIntOrNull() ?: -1] = a.getValue("formatCode") ?: ""
                            "cellXfs" -> inXfs = true
                            "xf" -> if (inXfs) xfFmt.add(a.getValue("numFmtId")?.toIntOrNull() ?: 0)
                        }
                    }
                    override fun endElement(u: String?, l: String?, q: String?) { if (l == "cellXfs") inXfs = false }
                })
            }
            xfFmt.forEach { dateXf.add(isDateFmt(it, custom[it])) }
        }

        val result = ArrayList<XSheet>()
        for (ref in refs) {
            val rel = wbRels[ref.rid] ?: continue
            val part = resolve("xl", rel.target)
            val entry = z.getEntry(part) ?: continue
            val rows = java.util.TreeMap<Int, MutableMap<Int, XCell>>()
            z.getInputStream(entry).use { s ->
                parser(limited(s, MAX_XML), object : DefaultHandler() {
                    var r = -1; var c = -1; var t = ""; var st = -1
                    var v = StringBuilder(); var inV = false; var inIs = false; var inIsT = false; var isb = StringBuilder()
                    override fun startElement(u: String?, l: String?, q: String?, a: Attributes) {
                        when (l) {
                            "c" -> {
                                val rf = a.getValue("r") ?: ""
                                r = rowIndex(rf); c = colIndex(rf); t = a.getValue("t") ?: ""; st = a.getValue("s")?.toIntOrNull() ?: -1
                                v.setLength(0); isb.setLength(0)
                            }
                            "v" -> inV = true
                            "is" -> inIs = true
                            "t" -> if (inIs) inIsT = true
                        }
                    }
                    override fun characters(ch: CharArray, start: Int, length: Int) {
                        if (inV) v.append(ch, start, length) else if (inIsT) isb.append(ch, start, length)
                    }
                    override fun endElement(u: String?, l: String?, q: String?) {
                        when (l) {
                            "v" -> inV = false
                            "t" -> inIsT = false
                            "is" -> inIs = false
                            "c" -> {
                                if (r in 0 until MAX_ROWS && c in 0 until MAX_COLS) {
                                    val raw = v.toString().trim()
                                    val cell: XCell? = when (t) {
                                        "s" -> raw.toIntOrNull()?.let { shared.getOrNull(it) }?.let { XCell(it, null, false) }
                                        "str" -> XCell(raw, null, false)
                                        "inlineStr" -> XCell(isb.toString(), null, false)
                                        "b" -> XCell(if (raw == "1") "TRUE" else "FALSE", null, false)
                                        "e" -> null
                                        else -> raw.toDoubleOrNull()?.let { n ->
                                            if (st >= 0 && dateXf.getOrNull(st) == true) XCell(serialToIso(n, date1904), n, true)
                                            else XCell(if (n == Math.floor(n) && Math.abs(n) < 1e15) n.toLong().toString() else n.toString(), n, false)
                                        }
                                    }
                                    if (cell != null && cell.text.isNotBlank()) rows.getOrPut(r) { java.util.TreeMap() }[c] = cell
                                }
                            }
                        }
                    }
                })
            }

            // gambar yang tertanam
            val images = HashMap<Int, MutableList<String>>()
            val sheetRels = rels(z, relsPathOf(part))
            val drawing = sheetRels.firstOrNull { it.type.endsWith("/drawing") }
            if (drawing != null) {
                val dPart = resolve(part.substringBeforeLast('/', ""), drawing.target)
                val dRels = rels(z, relsPathOf(dPart)).associateBy { it.id }
                z.getEntry(dPart)?.let { de ->
                    z.getInputStream(de).use { s ->
                        parser(limited(s, MAX_XML), object : DefaultHandler() {
                            var inFrom = false; var inRow = false; var row = -1; var rid: String? = null; val buf = StringBuilder()
                            override fun startElement(u: String?, l: String?, q: String?, a: Attributes) {
                                when (l) {
                                    "twoCellAnchor", "oneCellAnchor", "absoluteAnchor" -> { row = -1; rid = null }
                                    "from" -> inFrom = true
                                    "row" -> if (inFrom) { inRow = true; buf.setLength(0) }
                                    "blip" -> rid = a.getValue(NS_R, "embed")
                                }
                            }
                            override fun characters(ch: CharArray, start: Int, length: Int) { if (inRow) buf.append(ch, start, length) }
                            override fun endElement(u: String?, l: String?, q: String?) {
                                when (l) {
                                    "row" -> if (inRow) { inRow = false; row = buf.toString().trim().toIntOrNull() ?: -1 }
                                    "from" -> inFrom = false
                                    "twoCellAnchor", "oneCellAnchor", "absoluteAnchor" -> {
                                        val rr = rid
                                        if (row >= 0 && rr != null) dRels[rr]?.let { images.getOrPut(row) { ArrayList() }.add(resolve(dPart.substringBeforeLast('/', ""), it.target)) }
                                    }
                                }
                            }
                        })
                    }
                }
            }
            result.add(XSheet(ref.name, rows, images))
        }
        result
    }

    /** Membaca byte gambar dari berkas xlsx (dibatasi ukurannya). */
    fun readImage(file: File, entry: String): ByteArray? = ZipFile(file).use { z ->
        val e = z.getEntry(entry) ?: return null
        if (e.size > MAX_IMAGE) return null
        z.getInputStream(e).use { limited(it, MAX_IMAGE).readBytes() }
    }

    fun serialToIso(n: Double, date1904: Boolean = false): String =
        runCatching { LocalDate.of(1899, 12, 30).plusDays(n.toLong() + if (date1904) 1462 else 0).toString() }.getOrDefault("")

    private fun isDateFmt(id: Int, code: String?): Boolean {
        if (id in 14..22 || id in 27..36 || id in 45..47 || id in 50..58) return true
        if (code == null) return false
        val c = code.replace(Regex("\"[^\"]*\""), "").replace(Regex("\\[[^]]*]"), "").lowercase()
        return c != "general" && Regex("[dmyhs]").containsMatchIn(c)
    }
}
