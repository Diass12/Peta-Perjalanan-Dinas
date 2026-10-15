package id.tub.dinas.export

import id.tub.dinas.domain.*
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** Sumber byte gambar (didekripsi dari vault). Mengembalikan null bila berkas hilang. */
fun interface ExportMedia { fun bytes(path: String): ByteArray? }

/**
 * Menyusun .xlsx dari satu dinas: Temuan (foto + penjelasan + tiga lensa), Ringkasan,
 * Tindak Lanjut, Dokumentasi. Foto tidak pernah dipotong; rasio dijaga dari width/height tersimpan.
 */
object ExcelExporter {
    private const val EMU = 9525L
    private const val COL_PX = 7.0
    private const val MAX_PHOTO_H = 300.0
    private val LENS_COLOR = mapOf(Lens.ASET to ("B45309" to "FBEBD9"), Lens.OPS to ("0F766E" to "D8F0EC"), Lens.ADM to ("4F46E5" to "E6E5FB"))
    private const val INK = "12202B"; private const val MUTED = "5B6B78"; private const val AMBER = "E9A23B"

    fun export(s: DinasSnapshot, media: ExportMedia, out: OutputStream, zone: ZoneId = ZoneId.systemDefault()) {
        val wb = XlsxWriter()
        temuanSheet(wb, s, media)
        ringkasanSheet(wb, s, zone)
        tindakLanjutSheet(wb, s)
        dokumentasiSheet(wb, s, zone)
        wb.write(out)
    }

    private fun fmt(ms: Long?, zone: ZoneId, p: String = "dd MMM yyyy HH:mm"): String =
        if (ms == null) "" else DateTimeFormatter.ofPattern(p, Locale("id", "ID")).withZone(zone).format(Instant.ofEpochMilli(ms))

    /** Perkiraan jumlah baris teks bila lebar kolom dalam karakter. */
    private fun lines(text: String, widthChars: Double): Int =
        text.split("\n").sumOf { max(1, ceil(it.length / max(1.0, widthChars)).toInt()) }

    private fun temuanSheet(wb: XlsxWriter, s: DinasSnapshot, media: ExportMedia) {
        val sh = wb.sheet("Temuan")
        val widths = listOf(2.0, 28.0, 28.0, 22.0, 22.0, 22.0, 22.0)
        sh.colWidths(widths)
        val colPx = 28 * COL_PX
        var r = 0
        sh.text(r, 1, s.dinas.name, XStyle(bold = true, size = 16.0, wrap = false)); sh.rowHeight(r, 26.0); r += 2
        val head = XStyle(bold = true, size = 12.0, fill = "E4E0D5", v = "center", border = true)
        val lbl = XStyle(bold = true, size = 9.0, color = MUTED)
        s.temuan.sortedBy { it.order }.forEachIndexed { idx, t ->
            sh.merge(r, 1, r, 6, head); sh.text(r, 1, "${idx + 1}.   ${t.title}", head); sh.rowHeight(r, 30.0); r++

            val photos = s.fotoOf(OwnerType.TEMUAN, t.id)
            val start = r
            data class Slot(val f: FotoModel, val col: Int, val w: Double)
            val rowsOfPhotos = ArrayList<List<Slot>>()
            var i = 0
            if (photos.size % 2 == 1) { rowsOfPhotos.add(listOf(Slot(photos[0], 1, colPx * 2))); i = 1 }
            while (i < photos.size) { rowsOfPhotos.add(listOf(Slot(photos[i], 1, colPx), Slot(photos[i + 1], 2, colPx))); i += 2 }

            for (slots in rowsOfPhotos) {
                val dims = slots.map { sl ->
                    val w = max(1, sl.f.width).toDouble(); val h = max(1, sl.f.height).toDouble()
                    val sc = min(min((sl.w - 6) / w, MAX_PHOTO_H / h), 1.0)
                    w * sc to h * sc
                }
                val rowPx = dims.maxOf { it.second } + 8
                sh.rowHeight(r, rowPx * 0.75)
                slots.forEachIndexed { k, sl ->
                    val bytes = media.bytes(sl.f.path) ?: return@forEachIndexed
                    val (w, h) = dims[k]
                    var offX = (sl.w - w) / 2
                    var col = sl.col
                    if (col == 1 && offX >= colPx) { col = 2; offX -= colPx }
                    sh.image(bytes, "jpeg", r, col, (offX * EMU).toLong(), (((rowPx - h) / 2) * EMU).toLong(), (w * EMU).toLong(), (h * EMU).toLong())
                }
                r++
                if (slots.any { it.f.caption.isNotBlank() }) {
                    val cs = XStyle(italic = true, size = 9.0, color = MUTED, h = "center")
                    slots.forEach { sl -> sh.text(r, sl.col, sl.f.caption, cs) }
                    if (slots.size == 1) sh.merge(r, 1, r, 2, cs)
                    val maxLines = slots.maxOf { lines(it.f.caption, if (slots.size == 1) 56.0 else 28.0) }
                    sh.rowHeight(r, max(15.0, maxLines * 12.0 + 4)); r++
                }
            }

            // blok penjelasan di D:G
            val runs = ArrayList<XRun>()
            val locParts = listOfNotNull(t.code.takeIf { it.isNotBlank() }, t.geo?.let { CoordParser.format(it) })
            if (locParts.isNotEmpty()) { runs += XRun("LOKASI / KODE ASET\n", bold = true, size = 8.0, color = MUTED); runs += XRun(locParts.joinToString("  ·  ") + "\n\n", size = 10.0) }
            runs += XRun("PENJELASAN\n", bold = true, size = 8.0, color = MUTED)
            runs += XRun(t.description.ifBlank { "(belum diisi)" }, size = 10.0, italic = t.description.isBlank())
            val lamp = s.lampiranOf(t.id)
            if (lamp.isNotEmpty()) { runs += XRun("\n\nLAMPIRAN PDF\n", bold = true, size = 8.0, color = MUTED); runs += XRun(lamp.joinToString("\n") { "• ${it.name}" }, size = 10.0) }
            val plain = runs.joinToString("") { it.text }
            val needPt = lines(plain, 80.0) * 13.5 + 10
            if (r == start) { sh.rowHeight(r, max(60.0, needPt)); r++ }
            else {
                val have = (start until r).sumOf { rowHeightOf(sh, it) }
                if (have < needPt) { val last = r - 1; sh.rowHeight(last, rowHeightOf(sh, last) + (needPt - have)) }
            }
            val box = XStyle(wrap = true, v = "top", border = true, fill = "FFFFFF")
            sh.merge(start, 3, r - 1, 6, box)
            sh.rich(start, 3, runs, box)

            // tiga lensa
            for (l in Lens.values()) {
                if (!t.isActive(l)) continue
                val (c, bg) = LENS_COLOR.getValue(l)
                val txt = SummaryCalc.lensText(t, l) ?: continue
                val empty = t.lens[l].isNullOrBlank()
                sh.text(r, 1, l.label.uppercase(), XStyle(bold = true, size = 9.0, color = "FFFFFF", fill = c, v = "center", border = true))
                val st = XStyle(italic = empty, color = if (empty) MUTED else INK, fill = bg, v = "center", border = true)
                sh.merge(r, 2, r, 6, st); sh.text(r, 2, txt, st)
                sh.rowHeight(r, max(22.0, lines(txt, 104.0) * 13.5 + 8)); r++
            }
            r++ // baris kosong
        }
        if (s.temuan.isEmpty()) sh.text(r, 1, "Belum ada temuan laporan.", XStyle(italic = true, color = MUTED))
    }

    private fun rowHeightOf(sh: XlsxWriter.Sheet, row: Int): Double = sh.heightOf(row)

    private fun ringkasanSheet(wb: XlsxWriter, s: DinasSnapshot, zone: ZoneId) {
        val sh = wb.sheet("Ringkasan")
        sh.colWidths(listOf(2.0, 5.0, 34.0, 38.0, 38.0, 38.0, 32.0))
        val stats = SummaryCalc.calc(s.temuan)
        var r = 0
        val title = XStyle(bold = true, size = 18.0, color = "FFFFFF", fill = INK, v = "center")
        sh.merge(r, 1, r, 6, title); sh.text(r, 1, "  " + s.dinas.name, title); sh.rowHeight(r, 38.0); r++
        val period = listOfNotNull(s.dinas.startMs?.let { fmt(it, zone, "dd MMM yyyy") }, s.dinas.endMs?.let { fmt(it, zone, "dd MMM yyyy") }).joinToString(" – ")
        if (period.isNotEmpty() || s.dinas.note.isNotBlank()) {
            val st = XStyle(color = MUTED, size = 10.0)
            sh.merge(r, 1, r, 6, st); sh.text(r, 1, listOf(period, s.dinas.note).filter { it.isNotBlank() }.joinToString("  ·  "), st); sh.rowHeight(r, 20.0); r++
        }
        r++
        val cnt = XStyle(bold = true, size = 11.0, fill = "FBEBD9", v = "center", h = "center", border = true)
        val items = listOf("${stats.total} temuan", "${s.titik.size} titik dokumentasi", "${s.foto.size} foto", "${stats.done} selesai")
        items.forEachIndexed { i, t -> sh.text(r, 2 + i, t, cnt) }
        sh.rowHeight(r, 26.0); r += 2
        val sent = XStyle(size = 11.0, italic = true)
        sh.merge(r, 1, r, 6, sent); sh.text(r, 1, SummaryCalc.sentence(stats, s.titik.size), sent); sh.rowHeight(r, 34.0); r += 2
        val th = XStyle(bold = true, size = 9.0, color = "FFFFFF", fill = INK, v = "center", h = "center", border = true)
        listOf("#", "TEMUAN", "ASET", "TEKNIS OPERASIONAL", "ADMINISTRASI", "TINDAK LANJUT").forEachIndexed { i, h -> sh.text(r, 1 + i, h, th) }
        sh.rowHeight(r, 24.0); r++
        val cell = XStyle(border = true)
        s.temuan.sortedBy { it.order }.forEachIndexed { i, t ->
            sh.text(r, 1, "${i + 1}", XStyle(border = true, h = "center", bold = true))
            sh.text(r, 2, t.title, XStyle(border = true, bold = true))
            var maxL = lines(t.title, 34.0)
            Lens.values().forEachIndexed { k, l ->
                val txt = SummaryCalc.lensText(t, l)
                val (c, bg) = LENS_COLOR.getValue(l)
                if (txt == null) sh.text(r, 3 + k, "—", XStyle(border = true, h = "center", color = MUTED))
                else { sh.text(r, 3 + k, txt, XStyle(border = true, fill = bg, color = if (t.lens[l].isNullOrBlank()) MUTED else INK, italic = t.lens[l].isNullOrBlank())); maxL = max(maxL, lines(txt, 38.0)) }
            }
            val tl = listOf(t.action, t.pic.takeIf { it.isNotBlank() }?.let { "PIC: $it" }, t.target.takeIf { it.isNotBlank() }?.let { "Target: $it" }, "Status: ${t.status.label}").filterNotNull().filter { it.isNotBlank() }.joinToString("\n")
            sh.text(r, 6, tl, cell); maxL = max(maxL, lines(tl, 32.0))
            sh.rowHeight(r, max(24.0, maxL * 13.0 + 8)); r++
        }
    }

    private fun tindakLanjutSheet(wb: XlsxWriter, s: DinasSnapshot) {
        val sh = wb.sheet("Tindak Lanjut")
        sh.colWidths(listOf(2.0, 5.0, 34.0, 26.0, 56.0, 22.0, 16.0, 12.0))
        var r = 0
        val th = XStyle(bold = true, size = 9.0, color = "FFFFFF", fill = INK, v = "center", h = "center", border = true)
        listOf("#", "TEMUAN", "PENANGGUNG JAWAB", "TINDAK LANJUT", "TARGET", "STATUS", "KODE").forEachIndexed { i, h -> sh.text(r, 1 + i, h, th) }
        sh.rowHeight(r, 24.0); r++
        val statusFill = mapOf(Status.SELESAI to "D8F0EC", Status.PROSES to "FBEBD9", Status.BELUM to "ECE9E0", Status.SELISIH to "F8D7D4")
        s.temuan.sortedBy { it.order }.forEachIndexed { i, t ->
            val c = XStyle(border = true)
            sh.text(r, 1, "${i + 1}", XStyle(border = true, h = "center", bold = true))
            sh.text(r, 2, t.title, XStyle(border = true, bold = true))
            sh.text(r, 3, t.pic, c)
            sh.text(r, 4, t.action.ifBlank { "(belum diisi)" }, XStyle(border = true, italic = t.action.isBlank(), color = if (t.action.isBlank()) MUTED else INK))
            sh.text(r, 5, t.target, c)
            sh.text(r, 6, t.status.label, XStyle(border = true, bold = true, h = "center", fill = statusFill.getValue(t.status)))
            sh.text(r, 7, t.code, c)
            sh.rowHeight(r, max(22.0, max(lines(t.title, 34.0), lines(t.action, 56.0)) * 13.0 + 8)); r++
        }
        r++
        val h2 = XStyle(bold = true, size = 12.0)
        sh.text(r, 1, "Perbaikan sistem", h2); sh.rowHeight(r, 22.0); r++
        if (s.poin.isEmpty()) sh.text(r, 1, "(belum ada)", XStyle(italic = true, color = MUTED))
        s.poin.sortedBy { it.order }.forEachIndexed { i, p ->
            sh.text(r, 1, "${i + 1}", XStyle(border = true, h = "center", bold = true))
            val st = XStyle(border = true)
            sh.merge(r, 2, r, 7, st); sh.text(r, 2, p.text, st)
            sh.rowHeight(r, max(20.0, lines(p.text, 130.0) * 13.0 + 6)); r++
        }
    }

    private fun dokumentasiSheet(wb: XlsxWriter, s: DinasSnapshot, zone: ZoneId) {
        val sh = wb.sheet("Dokumentasi")
        sh.colWidths(listOf(2.0, 5.0, 30.0, 52.0, 13.0, 13.0, 11.0, 18.0, 8.0, 16.0))
        var r = 0
        val th = XStyle(bold = true, size = 9.0, color = "FFFFFF", fill = INK, v = "center", h = "center", border = true)
        listOf("#", "JUDUL", "CATATAN", "LINTANG", "BUJUR", "AKURASI", "WAKTU", "FOTO", "PETA").forEachIndexed { i, h -> sh.text(r, 1 + i, h, th) }
        sh.rowHeight(r, 24.0); r++
        val c = XStyle(border = true)
        s.titik.sortedBy { it.order }.forEachIndexed { i, t ->
            sh.text(r, 1, "${i + 1}", XStyle(border = true, h = "center", bold = true))
            sh.text(r, 2, t.title, XStyle(border = true, bold = true))
            sh.text(r, 3, t.note, c)
            if (t.geo != null) {
                sh.text(r, 4, String.format(Locale.US, "%.6f", t.geo.lat), c); sh.text(r, 5, String.format(Locale.US, "%.6f", t.geo.lng), c)
                sh.text(r, 6, t.geo.accuracyM?.let { "±$it m" } ?: "", c)
                sh.text(r, 9, "Buka peta", XStyle(border = true, color = "0563C1", underline = true, h = "center")); sh.link(r, 9, CoordParser.mapsUrl(t.geo))
            } else { for (k in 4..6) sh.text(r, k, "", c); sh.text(r, 9, "", c) }
            sh.text(r, 7, fmt(t.recordedAt, zone), c)
            sh.number(r, 8, s.fotoOf(OwnerType.TITIK, t.id).size.toDouble(), XStyle(border = true, h = "center"))
            sh.rowHeight(r, max(22.0, max(lines(t.title, 30.0), lines(t.note, 52.0)) * 13.0 + 8)); r++
        }
        if (s.titik.isEmpty()) sh.text(r, 1, "Belum ada titik dokumentasi.", XStyle(italic = true, color = MUTED))
    }
}
