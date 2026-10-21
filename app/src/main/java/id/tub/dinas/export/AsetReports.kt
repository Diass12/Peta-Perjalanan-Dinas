package id.tub.dinas.export

import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Base64
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import id.tub.dinas.domain.AsetFotoModel
import id.tub.dinas.domain.AsetModel
import id.tub.dinas.domain.KodeAset
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Imaging {
    /** Bitmap sisi terpanjang ≤ maxPx dari byte JPEG; null bila bukan gambar. */
    fun decode(bytes: ByteArray, maxPx: Int): Bitmap? {
        val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, b)
        if (b.outWidth <= 0 || b.outHeight <= 0) return null
        var sample = 1
        while (maxOf(b.outWidth, b.outHeight) / (sample * 2) >= maxPx) sample *= 2
        var bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val sc = maxPx.toFloat() / maxOf(bmp.width, bmp.height)
        if (sc < 1f) { val t = Bitmap.createScaledBitmap(bmp, (bmp.width * sc).toInt().coerceAtLeast(1), (bmp.height * sc).toInt().coerceAtLeast(1), true); if (t !== bmp) bmp.recycle(); bmp = t }
        return bmp
    }

    fun jpeg(bytes: ByteArray, maxPx: Int, quality: Int): ByteArray? {
        val bmp = decode(bytes, maxPx) ?: return null
        val o = ByteArrayOutputStream(48 * 1024)
        bmp.compress(Bitmap.CompressFormat.JPEG, quality, o); bmp.recycle()
        return o.toByteArray()
    }

    /** dHash 8x8 (64 bit): foto yang sama walau ukuran/kompresi beda punya hash berdekatan. */
    fun dHash(bytes: ByteArray): Long? {
        val bmp = decode(bytes, 256) ?: return null
        val s = Bitmap.createScaledBitmap(bmp, 9, 8, true)
        if (s !== bmp) bmp.recycle()
        var h = 0L
        for (y in 0 until 8) for (x in 0 until 8) {
            val a = lum(s.getPixel(x, y)); val b = lum(s.getPixel(x + 1, y))
            h = (h shl 1) or (if (a > b) 1L else 0L)
        }
        s.recycle()
        return h
    }
    private fun lum(c: Int) = ((c shr 16 and 255) * 299 + (c shr 8 and 255) * 587 + (c and 255) * 114) / 1000
    fun hamming(a: Long, b: Long) = java.lang.Long.bitCount(a xor b)
}

/** Laporan PDF, HTML, kartu aset, dan label QR dari register aset. */
object AsetReports {
    private const val PW = 595; private const val PH = 842; private const val M = 28

    private fun hari() = SimpleDateFormat("dd MMM yyyy", Locale("id", "ID")).format(Date())

    private fun tp(size: Float, bold: Boolean = false, color: Int = Color.BLACK, mono: Boolean = false) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size; this.color = color
        typeface = Typeface.create(if (mono) Typeface.MONOSPACE else Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    private fun Canvas.wrap(text: String, p: TextPaint, x: Float, y: Float, width: Int, maxLines: Int): Float {
        if (text.isEmpty()) return 0f
        val l = StaticLayout.Builder.obtain(text, 0, text.length, p, width).setMaxLines(maxLines).setEllipsize(android.text.TextUtils.TruncateAt.END).setAlignment(Layout.Alignment.ALIGN_NORMAL).build()
        save(); translate(x, y); l.draw(this); restore()
        return l.height.toFloat()
    }

    private fun line(vararg parts: Pair<String, String>) = parts.filter { it.second.isNotBlank() }.joinToString("  ·  ") { "${it.first}: ${it.second}" }

    // ---------------- PDF daftar ----------------

    fun laporanPdf(aset: List<AsetModel>, fotos: Map<String, List<AsetFotoModel>>, company: String, maxFoto: Int, readFoto: (String) -> ByteArray?, out: OutputStream, op: MemoFat.Opsi = MemoFat.Opsi()) {
        val doc = PdfDocument()
        var pageNo = 0
        var page: PdfDocument.Page? = null
        var c: Canvas? = null
        var y = 0f
        fun baru() {
            page?.let { doc.finishPage(it) }
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(PW, PH, pageNo).create())
            c = page!!.canvas
            c!!.drawText("Laporan Aset — $company", M.toFloat(), 40f, tp(13f, true))
            c!!.drawText("${hari()}  ·  ${aset.size} aset", M.toFloat(), 55f, tp(9f, color = Color.DKGRAY))
            c!!.drawText("Hal. $pageNo", (PW - M - 40).toFloat(), 55f, tp(9f, color = Color.DKGRAY))
            y = 68f
        }
        baru()
        val pCode = tp(11f, true, 0xFF4F3FE8.toInt(), true); val pName = tp(10.5f, true); val pInfo = tp(8.5f, color = 0xFF444444.toInt())
        val border = Paint().apply { style = Paint.Style.STROKE; color = 0xFFBBBBBB.toInt(); strokeWidth = 0.8f }
        val n = if (op.foto) maxFoto.coerceIn(0, 4) else 0
        val sisi = if (op.asli) 1400 else 360
        for ((i, a) in aset.withIndex()) {
            val ph = if (n > 0) (fotos[a.id].orEmpty()).take(n).mapNotNull { f -> readFoto(f.path)?.let { Imaging.decode(it, sisi) } } else emptyList()
            val ekstra = (if (op.nbv && id.tub.dinas.security.Lisensi.ho && (a.nbv != null || a.harga != null)) 11f else 0f) + (if (op.remarks && a.remarks.isNotBlank()) 20f else 0f)
            val h = 74f + ekstra + (if (ph.isNotEmpty()) 84f else 0f)
            if (y + h > PH - M) baru()
            val cv = c!!
            cv.drawRoundRect(RectF(M.toFloat(), y, (PW - M).toFloat(), y + h), 6f, 6f, border)
            cv.drawText("${i + 1}.", (M + 6).toFloat(), y + 16f, tp(9f, color = Color.GRAY))
            cv.drawText(a.code.ifEmpty { "(tanpa kode)" }, (M + 26).toFloat(), y + 16f, pCode)
            val w = PW - 2 * M - 34
            var ty = y + 22f
            ty += cv.wrap(a.name.ifEmpty { "(tanpa nama)" }, pName, (M + 26).toFloat(), ty, w, 2) + 2f
            ty += cv.wrap(line("Kategori" to KodeAset.labelKategori(a.category), "Dept" to a.dept, "Lokasi" to a.location, "User" to a.user), pInfo, (M + 26).toFloat(), ty, w, 2) + 1f
            ty += cv.wrap(line("Kondisi" to a.cond, "Barcode" to (if (op.barcode) (a.barcode?.label ?: "Non Aset") else ""), "Audit" to a.state.label, "PT" to (if (op.pemilik) a.owner else ""), "Milik/Sewa" to (if (op.pemilik) a.tenureLabel else ""), "Cek" to a.checkDate), pInfo, (M + 26).toFloat(), ty, w, 2) + 1f
            val nbvTxt = if (op.nbv && id.tub.dinas.security.Lisensi.ho) line("Nilai buku" to (a.nbv?.let { "%,.0f".format(it) } ?: ""), "Harga" to (a.harga?.let { "%,.0f".format(it) } ?: "")) else ""
            if (nbvTxt.isNotBlank()) ty += cv.wrap(nbvTxt, pInfo, (M + 26).toFloat(), ty, w, 1) + 1f
            if (op.remarks && a.remarks.isNotBlank()) cv.wrap("Ket: " + a.remarks, pInfo, (M + 26).toFloat(), ty, w, 2)
            var px = (M + 26).toFloat()
            for (b in ph) {
                val sc = minOf(76f / b.width, 76f / b.height)
                val dw = b.width * sc; val dh = b.height * sc
                cv.drawBitmap(b, null, RectF(px, y + 74f + ekstra, px + dw, y + 74f + ekstra + dh), null)
                px += dw + 8f; b.recycle()
            }
            y += h + 8f
        }
        page?.let { doc.finishPage(it) }
        doc.writeTo(out); doc.close()
    }

    // ---------------- PDF kartu satu aset ----------------

    fun kartuPdf(a: AsetModel, fotos: List<AsetFotoModel>, readFoto: (String) -> ByteArray?, out: OutputStream) {
        val doc = PdfDocument()
        val pg = doc.startPage(PdfDocument.PageInfo.Builder(PW, PH, 1).create())
        val c = pg.canvas
        c.drawText(a.code.ifEmpty { "(tanpa kode)" }, M.toFloat(), 50f, tp(20f, true, 0xFF4F3FE8.toInt(), true))
        var y = 62f
        y += c.wrap(a.name.ifEmpty { "(tanpa nama)" }, tp(14f, true), M.toFloat(), y, PW - 2 * M, 3) + 8f
        val lab = tp(9f, color = Color.GRAY); val v = tp(11f)
        val rows = listOf(
            "Kategori" to KodeAset.labelKategori(a.category), "Klasifikasi" to a.classif, "PT pemilik" to a.owner, "Kepemilikan" to a.tenureLabel,
            "Departemen" to a.dept, "Lokasi" to a.location, "User" to a.user, "Kondisi" to a.cond, "Tgl pembelian" to a.acq, "Tgl cek fisik" to a.checkDate,
            "Status barcode" to (a.barcode?.label ?: "Non Aset"), "Status audit" to a.state.label, "Nilai buku (IDR)" to (a.nbv?.let { String.format(Locale("id", "ID"), "%,.0f", it) } ?: ""),
            "Harga perolehan (IDR)" to (a.harga?.let { String.format(Locale("id", "ID"), "%,.0f", it) } ?: ""), "Keterangan" to a.remarks,
        ).filter { it.second.isNotBlank() && (id.tub.dinas.security.Lisensi.ho || !it.first.contains("IDR")) }
        for ((k, x) in rows) {
            c.drawText(k, M.toFloat(), y + 10f, lab)
            y += c.wrap(x, v, 150f, y, PW - M - 150, 3).coerceAtLeast(14f) + 6f
        }
        y += 6f
        val ph = fotos.take(4).mapNotNull { f -> readFoto(f.path)?.let { Imaging.decode(it, 800) } }
        val cell = (PW - 2 * M - 12) / 2f
        for ((i, b) in ph.withIndex()) {
            val cx = M + (i % 2) * (cell + 12f); val cy = y + (i / 2) * (cell * 0.75f + 12f)
            if (cy + cell * 0.75f > PH - M) { b.recycle(); continue }
            val sc = minOf(cell / b.width, cell * 0.75f / b.height)
            c.drawBitmap(b, null, RectF(cx, cy, cx + b.width * sc, cy + b.height * sc), null); b.recycle()
        }
        c.drawText("Dibuat ${hari()}", M.toFloat(), (PH - 16).toFloat(), tp(8f, color = Color.GRAY))
        doc.finishPage(pg); doc.writeTo(out); doc.close()
    }

    // ---------------- HTML ----------------

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    fun laporanHtml(aset: List<AsetModel>, fotos: Map<String, List<AsetFotoModel>>, company: String, maxFoto: Int, readFoto: (String) -> ByteArray?, out: OutputStream, op: MemoFat.Opsi = MemoFat.Opsi()) {
        val w = out.bufferedWriter(Charsets.UTF_8)
        w.write("""<!doctype html><html lang="id"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Laporan Aset</title>
<style>body{font-family:system-ui,Segoe UI,Roboto,sans-serif;margin:16px;background:#f5f3ff;color:#1e1b3a}h1{margin:0 0 4px}small{color:#666}
input{padding:10px 14px;border:1px solid #c4b5fd;border-radius:12px;width:min(520px,100%);margin:12px 0;font-size:15px}
table{border-collapse:collapse;width:100%;background:#fff;border-radius:12px;overflow:hidden}th{background:#5b45f0;color:#fff;text-align:left;padding:8px;position:sticky;top:0}
td{padding:8px;border-bottom:1px solid #eee;vertical-align:top;font-size:13px}td img{max-width:120px;max-height:90px;border-radius:6px;margin:0 4px 4px 0;cursor:zoom-in}
.code{font-family:ui-monospace,Consolas,monospace;font-weight:700;color:#4f3fe8}.t{display:inline-block;padding:2px 8px;border-radius:8px;font-size:11px;font-weight:600}
.Tagged{background:#c6e0b4}.Untagged{background:#ffe699}.NonAset{background:#d9d9d9}
#zoom{position:fixed;inset:0;background:#000d;display:none;align-items:center;justify-content:center}#zoom img{max-width:94vw;max-height:94vh}
@media print{input{display:none}body{background:#fff}th{position:static}td img{max-width:90px}}</style></head><body>
<h1>Laporan Aset — """.trimIndent() + esc(company) + """</h1><small>""" + hari() + " · " + aset.size + """ aset</small><br>
<input id="q" placeholder="Cari kode, nama, dept, lokasi…" oninput="f()">
<table id="t"><thead><tr><th>No</th>""" + (if (op.foto) "<th>Foto</th>" else "") + """<th>Kode</th><th>Nama</th><th>Kategori</th><th>Dept</th><th>Lokasi</th><th>User</th><th>Kondisi</th>""" + (if (op.barcode) "<th>Barcode</th>" else "") + "<th>Audit</th>" + (if (op.remarks) "<th>Keterangan</th>" else "") + (if (op.nbv && id.tub.dinas.security.Lisensi.ho) "<th>Harga</th><th>Nilai Buku</th>" else "") + (if (op.pemilik) "<th>PT Pemilik</th><th>Kepemilikan</th>" else "") + """</tr></thead><tbody>
""")
        val n = if (op.foto) maxFoto.coerceIn(0, 4) else 0
        for ((i, a) in aset.withIndex()) {
            val imgs = if (n > 0) (fotos[a.id].orEmpty()).take(n).mapNotNull { f -> readFoto(f.path)?.let { if (op.asli) Imaging.jpeg(it, 1400, 85) else Imaging.jpeg(it, 360, 72) } } else emptyList()
            val bc = a.barcode?.label ?: "Non Aset"
            w.write("<tr><td>${i + 1}</td>")
            if (op.foto) { w.write("<td>"); for (b in imgs) w.write("<img onclick=\"z(this)\" src=\"data:image/jpeg;base64,${Base64.encodeToString(b, Base64.NO_WRAP)}\">"); w.write("</td>") }
            w.write("<td class=\"code\">${esc(a.code.ifEmpty { "—" })}</td><td>${esc(a.name)}</td><td>${esc(KodeAset.labelKategori(a.category))}</td><td>${esc(a.dept)}</td><td>${esc(a.location)}</td><td>${esc(a.user)}</td><td>${esc(a.cond)}</td>")
            if (op.barcode) w.write("<td><span class=\"t ${bc.replace(" ", "")}\">${esc(bc)}</span></td>")
            w.write("<td>${esc(a.state.label)}</td>")
            if (op.remarks) w.write("<td>${esc(a.remarks)}</td>")
            if (op.nbv && id.tub.dinas.security.Lisensi.ho) w.write("<td>${a.harga?.let { "%,.0f".format(it) } ?: ""}</td><td>${a.nbv?.let { "%,.0f".format(it) } ?: ""}</td>")
            if (op.pemilik) w.write("<td>${esc(a.owner)}</td><td>${esc(a.tenureLabel)}</td>")
            w.write("</tr>\n")
        }
        w.write("""</tbody></table><div id="zoom" onclick="this.style.display='none'"><img id="zi"></div>
<script>function f(){var q=document.getElementById('q').value.toLowerCase();document.querySelectorAll('#t tbody tr').forEach(function(r){r.style.display=r.textContent.toLowerCase().indexOf(q)>=0?'':'none'})}
function z(i){document.getElementById('zi').src=i.src;document.getElementById('zoom').style.display='flex'}</script></body></html>""")
        w.flush()
    }

    // ---------------- label QR ----------------

    fun labelPdf(aset: List<AsetModel>, company: String, out: OutputStream) {
        val list = aset.filter { it.code.isNotBlank() }
        val doc = PdfDocument()
        val cols = 3; val rows = 8
        val cw = 181; val ch = 96; val left = (PW - cols * cw) / 2; val top = 30
        val per = cols * rows
        val pages = maxOf(1, (list.size + per - 1) / per)
        val dash = Paint().apply { style = Paint.Style.STROKE; color = 0xFFCCCCCC.toInt(); strokeWidth = 0.6f; pathEffect = DashPathEffect(floatArrayOf(3f, 3f), 0f) }
        val black = Paint().apply { color = Color.BLACK }
        val writer = QRCodeWriter()
        val hints = mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 0)
        for (p in 0 until pages) {
            val pg = doc.startPage(PdfDocument.PageInfo.Builder(PW, PH, p + 1).create())
            val c = pg.canvas
            for (k in 0 until per) {
                val idx = p * per + k
                if (idx >= list.size) break
                val a = list[idx]
                val x = left + (k % cols) * cw; val y = top + (k / cols) * ch
                c.drawRect(x.toFloat(), y.toFloat(), (x + cw).toFloat(), (y + ch).toFloat(), dash)
                val m = writer.encode(a.code, BarcodeFormat.QR_CODE, 0, 0, hints)
                val qs = 70f; val cell = qs / m.width
                for (yy in 0 until m.height) for (xx in 0 until m.width) if (m.get(xx, yy))
                    c.drawRect(x + 10 + xx * cell, y + 13 + yy * cell, x + 10 + (xx + 1) * cell, y + 13 + (yy + 1) * cell, black)
                c.drawText(a.code, (x + 86).toFloat(), (y + 26).toFloat(), tp(11f, true, mono = true))
                c.wrap(a.name, tp(8f, color = 0xFF333333.toInt()), (x + 86).toFloat(), (y + 32).toFloat(), cw - 92, 3)
                c.drawText(company.take(26), (x + 86).toFloat(), (y + ch - 10).toFloat(), tp(6.5f, color = Color.GRAY))
            }
            doc.finishPage(pg)
        }
        doc.writeTo(out); doc.close()
    }
}
