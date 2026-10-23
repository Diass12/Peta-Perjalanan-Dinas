package id.tub.dinas.export

import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Penulis .xlsx minimal tanpa dependensi (OOXML): gaya, gabung sel, tautan, teks kaya, gambar. */
data class XStyle(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val size: Double = 10.0,
    val color: String = "12202B",
    val fill: String? = null,
    val wrap: Boolean = true,
    val h: String? = null,      // left|center|right
    val v: String = "top",      // top|center|bottom
    val border: Boolean = false,
    val underline: Boolean = false,
    val font: String = "Calibri",
    val numFmt: String? = null,
    /** Border per sisi "kiri,kanan,atas,bawah" (thin/hair/double/kosong); menggantikan [border] bila diisi. */
    val bd: String? = null,
)

data class XRun(val text: String, val bold: Boolean = false, val italic: Boolean = false, val color: String? = null, val size: Double? = null)

class XlsxWriter {
    private val styles = LinkedHashMap<XStyle, Int>().also { it[XStyle()] = 0 }
    private val sheets = ArrayList<Sheet>()

    fun sheet(name: String): Sheet = Sheet(name.replace(Regex("[\\\\/?*\\[\\]:]"), " ").take(31).ifBlank { "Sheet" }, this).also { sheets.add(it) }

    internal fun styleId(s: XStyle): Int = styles.getOrPut(s) { styles.size }

    internal class Cell(val style: Int, val text: String?, val num: Double?, val runs: List<XRun>?, val formula: String? = null)
    internal class Img(val bytes: ByteArray, val ext: String, val row: Int, val col: Int, val offX: Long, val offY: Long, val cx: Long, val cy: Long)

    class Sheet internal constructor(val name: String, private val owner: XlsxWriter) {
        private val cells = java.util.TreeMap<Int, java.util.TreeMap<Int, Cell>>()
        private val merges = ArrayList<String>()
        private val heights = HashMap<Int, Double>()
        private var widths: List<Double> = emptyList()
        private val links = ArrayList<Triple<Int, Int, String>>()
        private val images = ArrayList<Img>()
        var showGrid = false
        var landscape = false

        fun colWidths(w: List<Double>) { widths = w }
        fun rowHeight(row: Int, pt: Double) { heights[row] = pt }
        fun heightOf(row: Int): Double = heights[row] ?: 15.0
        fun text(row: Int, col: Int, t: String, st: XStyle = XStyle()) { put(row, col, Cell(owner.styleId(st), t, null, null)) }
        fun number(row: Int, col: Int, n: Double, st: XStyle = XStyle()) { put(row, col, Cell(owner.styleId(st), null, n, null)) }
        /** Sel rumus; [cached] = nilai hasil agar tampil juga di penampil tanpa kalkulasi ulang. */
        fun formula(row: Int, col: Int, f: String, cached: Double, st: XStyle = XStyle()) { put(row, col, Cell(owner.styleId(st), null, cached, null, f)) }
        fun rich(row: Int, col: Int, runs: List<XRun>, st: XStyle = XStyle()) { put(row, col, Cell(owner.styleId(st), null, null, runs)) }
        fun style(row: Int, col: Int, st: XStyle) { if (cells[row]?.get(col) == null) put(row, col, Cell(owner.styleId(st), null, null, null)) }
        fun merge(r1: Int, c1: Int, r2: Int, c2: Int, st: XStyle? = null) {
            if (st != null) for (r in r1..r2) for (c in c1..c2) style(r, c, st)
            merges.add("${ref(r1, c1)}:${ref(r2, c2)}")
        }
        fun link(row: Int, col: Int, url: String) { links.add(Triple(row, col, url)) }
        /** Gambar dengan ukuran persis (EMU) mulai dari sel (row,col) dengan offset di dalam sel. */
        fun image(bytes: ByteArray, ext: String, row: Int, col: Int, offXEmu: Long, offYEmu: Long, cxEmu: Long, cyEmu: Long) {
            images.add(Img(bytes, ext, row, col, offXEmu, offYEmu, cxEmu, cyEmu))
        }

        private fun put(r: Int, c: Int, cell: Cell) { cells.getOrPut(r) { java.util.TreeMap() }[c] = cell }

        internal fun imageList() = images
        internal fun hasLinks() = links.isNotEmpty()

        internal fun xml(drawingRid: String?, linkRids: Map<Int, String>): String {
            val sb = StringBuilder()
            sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""")
            sb.append("""<sheetPr><pageSetUpPr fitToPage="1"/></sheetPr><sheetViews><sheetView workbookViewId="0" showGridLines="${if (showGrid) 1 else 0}"/></sheetViews><sheetFormatPr defaultRowHeight="15"/>""")
            if (widths.isNotEmpty()) {
                sb.append("<cols>")
                widths.forEachIndexed { i, w -> sb.append("""<col min="${i + 1}" max="${i + 1}" width="${if (w <= 0) 1.0 else w}" customWidth="1"${if (w <= 0) " hidden=\"1\"" else ""}/>""") }
                sb.append("</cols>")
            }
            sb.append("<sheetData>")
            val rows = (cells.keys + heights.keys).toSortedSet()
            for (r in rows) {
                sb.append("""<row r="${r + 1}"""")
                heights[r]?.let { sb.append(""" ht="$it" customHeight="1"""") }
                sb.append(">")
                cells[r]?.forEach { (c, cell) ->
                    val a = ref(r, c)
                    when {
                        cell.runs != null -> {
                            sb.append("""<c r="$a" s="${cell.style}" t="inlineStr"><is>""")
                            for (run in cell.runs) {
                                sb.append("<r><rPr>")
                                if (run.bold) sb.append("<b/>")
                                if (run.italic) sb.append("<i/>")
                                sb.append("""<sz val="${run.size ?: 10.0}"/>""")
                                run.color?.let { sb.append("""<color rgb="FF$it"/>""") }
                                sb.append("""<rFont val="Calibri"/></rPr><t xml:space="preserve">${esc(run.text)}</t></r>""")
                            }
                            sb.append("</is></c>")
                        }
                        cell.text != null -> sb.append("""<c r="$a" s="${cell.style}" t="inlineStr"><is><t xml:space="preserve">${esc(cell.text)}</t></is></c>""")
                        cell.formula != null -> sb.append("""<c r="$a" s="${cell.style}"><f>${esc(cell.formula)}</f><v>${cell.num}</v></c>""")
                        cell.num != null -> sb.append("""<c r="$a" s="${cell.style}"><v>${cell.num}</v></c>""")
                        else -> sb.append("""<c r="$a" s="${cell.style}"/>""")
                    }
                }
                sb.append("</row>")
            }
            sb.append("</sheetData>")
            if (merges.isNotEmpty()) {
                sb.append("""<mergeCells count="${merges.size}">""")
                merges.forEach { sb.append("""<mergeCell ref="$it"/>""") }
                sb.append("</mergeCells>")
            }
            if (links.isNotEmpty()) {
                sb.append("<hyperlinks>")
                links.forEachIndexed { i, (r, c, _) -> sb.append("""<hyperlink ref="${ref(r, c)}" r:id="${linkRids[i]}"/>""") }
                sb.append("</hyperlinks>")
            }
            sb.append("""<pageMargins left="0.5" right="0.5" top="0.6" bottom="0.6" header="0.3" footer="0.3"/><pageSetup paperSize="9" orientation="${if (landscape) "landscape" else "portrait"}" fitToWidth="1" fitToHeight="0"/>""")
            if (drawingRid != null) sb.append("""<drawing r:id="$drawingRid"/>""")
            sb.append("</worksheet>")
            return sb.toString()
        }

        internal fun linkUrls() = links.map { it.third }
    }

    fun write(out: OutputStream) {
        val z = ZipOutputStream(out)
        fun put(name: String, s: String) { z.putNextEntry(ZipEntry(name)); z.write(s.toByteArray(Charsets.UTF_8)); z.closeEntry() }
        val ct = StringBuilder("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Default Extension="jpeg" ContentType="image/jpeg"/><Default Extension="png" ContentType="image/png"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""")
        sheets.forEachIndexed { i, s ->
            ct.append("""<Override PartName="/xl/worksheets/sheet${i + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
            if (s.imageList().isNotEmpty()) ct.append("""<Override PartName="/xl/drawings/drawing${i + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.drawing+xml"/>""")
        }
        ct.append("</Types>")
        put("[Content_Types].xml", ct.toString())
        put("_rels/.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""")
        val wb = StringBuilder("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>""")
        val wr = StringBuilder("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
        sheets.forEachIndexed { i, s ->
            wb.append("""<sheet name="${esc(s.name)}" sheetId="${i + 1}" r:id="rId${i + 1}"/>""")
            wr.append("""<Relationship Id="rId${i + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet${i + 1}.xml"/>""")
        }
        wb.append("</sheets><calcPr fullCalcOnLoad=\"1\"/></workbook>")
        wr.append("""<Relationship Id="rId${sheets.size + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>""")
        put("xl/workbook.xml", wb.toString()); put("xl/_rels/workbook.xml.rels", wr.toString())

        var mediaN = 0
        sheets.forEachIndexed { i, s ->
            val n = i + 1
            val rels = StringBuilder()
            val linkRids = HashMap<Int, String>()
            s.linkUrls().forEachIndexed { k, u ->
                val id = "rIdL$k"; linkRids[k] = id
                rels.append("""<Relationship Id="$id" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/hyperlink" Target="${esc(u)}" TargetMode="External"/>""")
            }
            var drawingRid: String? = null
            if (s.imageList().isNotEmpty()) {
                drawingRid = "rIdD"
                rels.append("""<Relationship Id="rIdD" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/drawing" Target="../drawings/drawing$n.xml"/>""")
                val d = StringBuilder("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><xdr:wsDr xmlns:xdr="http://schemas.openxmlformats.org/drawingml/2006/spreadsheetDrawing" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""")
                val dr = StringBuilder("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
                s.imageList().forEachIndexed { k, im ->
                    mediaN++
                    val ext = if (im.ext.lowercase() == "png") "png" else "jpeg"
                    z.putNextEntry(ZipEntry("xl/media/image$mediaN.$ext")); z.write(im.bytes); z.closeEntry()
                    dr.append("""<Relationship Id="rId${k + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="../media/image$mediaN.$ext"/>""")
                    d.append("""<xdr:oneCellAnchor><xdr:from><xdr:col>${im.col}</xdr:col><xdr:colOff>${im.offX}</xdr:colOff><xdr:row>${im.row}</xdr:row><xdr:rowOff>${im.offY}</xdr:rowOff></xdr:from><xdr:ext cx="${im.cx}" cy="${im.cy}"/><xdr:pic><xdr:nvPicPr><xdr:cNvPr id="${k + 2}" name="Foto ${k + 1}"/><xdr:cNvPicPr><a:picLocks noChangeAspect="1"/></xdr:cNvPicPr></xdr:nvPicPr><xdr:blipFill><a:blip r:embed="rId${k + 1}"/><a:stretch><a:fillRect/></a:stretch></xdr:blipFill><xdr:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="${im.cx}" cy="${im.cy}"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></xdr:spPr></xdr:pic><xdr:clientData/></xdr:oneCellAnchor>""")
                }
                d.append("</xdr:wsDr>"); dr.append("</Relationships>")
                put("xl/drawings/drawing$n.xml", d.toString()); put("xl/drawings/_rels/drawing$n.xml.rels", dr.toString())
            }
            if (rels.isNotEmpty()) put("xl/worksheets/_rels/sheet$n.xml.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">$rels</Relationships>""")
            put("xl/worksheets/sheet$n.xml", s.xml(drawingRid, linkRids))
        }
        put("xl/styles.xml", stylesXml())
        z.finish(); z.flush()
    }

    private fun stylesXml(): String {
        val fonts = LinkedHashMap<String, Int>(); val fills = LinkedHashMap<String, Int>()
        fills["<fill><patternFill patternType=\"none\"/></fill>"] = 0
        fills["<fill><patternFill patternType=\"gray125\"/></fill>"] = 1
        val xfs = StringBuilder()
        val numFmts = LinkedHashMap<String, Int>(); val extraBorders = LinkedHashMap<String, Int>()
        fun edge(tag: String, st: String) = if (st.isBlank()) "<$tag/>" else "<$tag style=\"${st.trim()}\"><color rgb=\"FF000000\"/></$tag>"
        for (s in styles.keys) {
            val f = "<font>${if (s.bold) "<b/>" else ""}${if (s.italic) "<i/>" else ""}${if (s.underline) "<u/>" else ""}<sz val=\"${s.size}\"/><color rgb=\"FF${s.color}\"/><name val=\"${s.font}\"/></font>"
            val fi = fonts.getOrPut(f) { fonts.size }
            val fl = if (s.fill == null) 0 else fills.getOrPut("<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FF${s.fill}\"/><bgColor indexed=\"64\"/></patternFill></fill>") { fills.size }
            val nf = s.numFmt?.let { numFmts.getOrPut(it) { 164 + numFmts.size } } ?: 0
            val bid = if (s.bd != null) { val e = s.bd.split(",").let { l -> List(4) { l.getOrElse(it) { "" } } }; 2 + extraBorders.getOrPut("<border>${edge("left", e[0])}${edge("right", e[1])}${edge("top", e[2])}${edge("bottom", e[3])}<diagonal/></border>") { extraBorders.size } } else if (s.border) 1 else 0
            xfs.append("""<xf numFmtId="$nf" fontId="$fi" fillId="$fl" borderId="$bid" xfId="0" applyNumberFormat="1" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment${s.h?.let { " horizontal=\"$it\"" } ?: ""} vertical="${s.v}"${if (s.wrap) " wrapText=\"1\"" else ""}/></xf>""")
        }
        val bd = "<border><left style=\"thin\"><color rgb=\"FFDDD8CC\"/></left><right style=\"thin\"><color rgb=\"FFDDD8CC\"/></right><top style=\"thin\"><color rgb=\"FFDDD8CC\"/></top><bottom style=\"thin\"><color rgb=\"FFDDD8CC\"/></bottom><diagonal/></border>"
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">${if (numFmts.isEmpty()) "" else "<numFmts count=\"${numFmts.size}\">" + numFmts.entries.joinToString("") { "<numFmt numFmtId=\"${it.value}\" formatCode=\"${esc(it.key)}\"/>" } + "</numFmts>"}<fonts count="${fonts.size}">${fonts.keys.joinToString("")}</fonts><fills count="${fills.size}">${fills.keys.joinToString("")}</fills><borders count="${2 + extraBorders.size}"><border><left/><right/><top/><bottom/><diagonal/></border>$bd${extraBorders.keys.joinToString("")}</borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="${styles.size}">$xfs</cellXfs><cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles></styleSheet>"""
    }

    companion object {
        fun ref(row: Int, col: Int): String {
            var c = col; val sb = StringBuilder()
            do { sb.insert(0, ('A' + c % 26)); c = c / 26 - 1 } while (c >= 0)
            return "$sb${row + 1}"
        }
        fun esc(s: String): String {
            val sb = StringBuilder(s.length + 8)
            for (ch in s) when {
                ch == '&' -> sb.append("&amp;"); ch == '<' -> sb.append("&lt;"); ch == '>' -> sb.append("&gt;")
                ch == '"' -> sb.append("&quot;")
                ch.code < 32 && ch != '\n' && ch != '\t' && ch != '\r' -> {}
                else -> sb.append(ch)
            }
            return sb.toString()
        }
    }
}
