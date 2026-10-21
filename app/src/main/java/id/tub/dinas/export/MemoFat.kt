package id.tub.dinas.export

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import id.tub.dinas.domain.AsetFotoModel
import id.tub.dinas.domain.AsetModel
import id.tub.dinas.domain.StatusBarcode
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Laporan "Physical Check - Fixed Asset Good Condition" (Memo FAT): 3 sheet Tagged / Untagged / Belum Ada Kode Aset. */
object MemoFat {
    private const val EMU = 9525L
    private const val ROW_PHOTO_PT = 199.5
    private const val ROW_PLAIN_PT = 60.0
    private const val PHOTO_COL_W = 59.6
    private const val HEAD_FILL = "FABF8F"

    private class Col(val title: String, val width: Double, val nbv: Boolean = false)

    private val COLS = listOf(
        Col("No.", 4.6), Col("Assets Classification", 21.0), Col("Asset Number", 14.8), Col("Purchase Date", 13.9),
        Col("Description", 20.9), Col("Location", 12.1), Col("Dept.", 12.4), Col("User", 16.4), Col("Date of Physical Check", 15.4),
        Col("Condition", 13.0), Col("Photo", PHOTO_COL_W), Col("Barcode", 13.0), Col("Remarks", 17.0),
        Col("NBV - Keterangan", 16.0, true), Col("Tanggal Tercatat", 16.0, true), Col("Masa Manfaat (Bln)", 14.0, true),
        Col("Harga Perolehan (IDR)", 18.0, true), Col("Nilai Buku (IDR)", 18.0, true), Col("Catatan (NBV)", 20.0, true),
        Col("Sumber NBV (Sheet!Baris)", 20.0, true), Col("Status Match", 14.0, true),
        Col("PT Pemilik", 22.0), Col("Kepemilikan", 14.0),
    )
    private const val PHOTO_IDX = 10

    /** Pilihan ekspor: sheet mana, kolom opsional mana, dan mutu foto. */
    class Opsi(
        val tagged: Boolean = true, val untagged: Boolean = true, val tanpaKode: Boolean = true,
        val foto: Boolean = true, val barcode: Boolean = true, val remarks: Boolean = true, val nbv: Boolean = true, val pemilik: Boolean = true,
        val asli: Boolean = false,
    )

    private fun barcodeFill(b: StatusBarcode?) = when (b) { StatusBarcode.TAGGED -> "C6E0B4"; StatusBarcode.UNTAGGED -> "FFE699"; else -> "D9D9D9" }

    /** Perkecil foto untuk Excel supaya memori dan ukuran berkas terkendali. */
    private fun small(bytes: ByteArray, side: Int = 720, asli: Boolean = false): Triple<ByteArray, Int, Int>? {
        val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, b)
        if (b.outWidth <= 0 || b.outHeight <= 0) return null
        if (asli) return Triple(bytes, b.outWidth, b.outHeight)
        var sample = 1
        while (maxOf(b.outWidth, b.outHeight) / (sample * 2) >= side) sample *= 2
        var bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val sc = side.toFloat() / maxOf(bmp.width, bmp.height)
        if (sc < 1f) { val t = Bitmap.createScaledBitmap(bmp, (bmp.width * sc).toInt().coerceAtLeast(1), (bmp.height * sc).toInt().coerceAtLeast(1), true); if (t !== bmp) bmp.recycle(); bmp = t }
        val o = ByteArrayOutputStream(96 * 1024)
        bmp.compress(Bitmap.CompressFormat.JPEG, 80, o)
        val w = bmp.width; val h = bmp.height
        bmp.recycle()
        return Triple(o.toByteArray(), w, h)
    }

    fun export(
        aset: List<AsetModel>, fotos: Map<String, List<AsetFotoModel>>, company: String, maxFoto: Int,
        readFoto: (String) -> ByteArray?, out: OutputStream, opsi: Opsi = Opsi(),
    ) {
        val wb = XlsxWriter()
        val groups = listOfNotNull(
            if (opsi.tagged) Triple("Tagged", "Listing Assets - Tagged", aset.filter { it.barcode == StatusBarcode.TAGGED }) else null,
            if (opsi.untagged) Triple("Untagged", "Listing Assets - Untagged", aset.filter { it.barcode == StatusBarcode.UNTAGGED }) else null,
            if (opsi.tanpaKode) Triple("Belum Ada Kode Aset", "Listing Assets - Belum Ada Kode Aset (Non Aset)", aset.filter { it.barcode == StatusBarcode.NONASSET || it.barcode == null }) else null,
        )
        val month = SimpleDateFormat("MMMM, yyyy", Locale.ENGLISH).format(Date()).uppercase(Locale.ENGLISH)
        val n = maxFoto.coerceIn(1, 4)
        val ikut = COLS.indices.filter { i -> when (i) { PHOTO_IDX -> opsi.foto; 11 -> opsi.barcode; 12 -> opsi.remarks; in 13..20 -> opsi.nbv; 21, 22 -> opsi.pemilik; else -> true } }
        val pos = HashMap<Int, Int>().also { m -> ikut.forEachIndexed { k, i -> m[i] = 3 + k } }
        fun cx(i: Int) = pos[i] ?: -1
        for ((sheetName, label, list) in groups) {
            val sh = wb.sheet(sheetName)
            sh.colWidths(listOf(2.0, 2.0, 2.0) + ikut.map { COLS[it].width })
            val title = XStyle(bold = true, size = 14.0, wrap = false)
            val lab = XStyle(bold = true, size = 11.0, wrap = false)
            sh.text(0, 1, "Physical Check - Fixed Asset Good Condition", title)
            sh.text(2, 1, "Company", lab); sh.text(2, 2, ":", lab); sh.text(2, 3, company, lab)
            sh.text(3, 1, "Date", lab); sh.text(3, 2, ":", lab); sh.text(3, 3, month, lab)
            sh.number(4, 3, list.size.toDouble(), lab)
            sh.text(5, 3, label, lab)

            val head = XStyle(bold = true, size = 10.0, fill = HEAD_FILL, h = "center", v = "center", border = true)
            val headNbv = XStyle(bold = true, italic = true, size = 10.0, color = "C00000", fill = HEAD_FILL, h = "center", v = "center", border = true)
            ikut.forEachIndexed { k, i ->
                val c = COLS[i]
                val col = 3 + k
                sh.text(6, col, c.title, if (c.nbv) headNbv else head)
                sh.merge(6, col, 8, col, if (c.nbv) headNbv else head)
            }
            val body = XStyle(size = 10.0, border = true, v = "center")
            val bodyC = body.copy(h = "center")
            list.forEachIndexed { idx, a ->
                val r = 9 + idx
                val ph = if (!opsi.foto) emptyList() else (fotos[a.id].orEmpty()).take(n).mapNotNull { f -> readFoto(f.path)?.let { small(it, 900, opsi.asli) } }
                sh.rowHeight(r, if (ph.isNotEmpty()) ROW_PHOTO_PT else ROW_PLAIN_PT)
                fun t(c: Int, v: String, st: XStyle = body) { val x = cx(c); if (x >= 0) sh.text(r, x, v, st) }
                sh.number(r, 3, (idx + 1).toDouble(), bodyC)
                t(1, a.classif); t(2, a.code); t(3, a.acq); t(4, a.name); t(5, a.location); t(6, a.dept); t(7, a.user)
                t(8, a.checkDate); t(9, a.cond)
                if (cx(PHOTO_IDX) >= 0) sh.style(r, cx(PHOTO_IDX), body)
                t(11, a.barcode?.label ?: "Non Aset", bodyC.copy(fill = barcodeFill(a.barcode)))
                t(12, a.remarks)
                t(13, a.nbvKet); t(14, a.tglTercatat)
                if (cx(15) >= 0) a.masa?.let { sh.number(r, cx(15), it.toDouble(), body) } ?: sh.style(r, cx(15), body)
                if (cx(16) >= 0) a.harga?.let { sh.number(r, cx(16), it, body) } ?: sh.style(r, cx(16), body)
                if (cx(17) >= 0) a.nbv?.let { sh.number(r, cx(17), it, body) } ?: sh.style(r, cx(17), body)
                t(18, a.catatan); t(19, a.sumber); t(20, a.srcStatus); t(21, a.owner); t(22, a.tenureLabel)

                if (ph.isNotEmpty()) {
                    val cellW = PHOTO_COL_W * 7 + 5      // piksel kira-kira
                    val cellH = ROW_PHOTO_PT / 0.75
                    val slotW = (cellW - 4) / ph.size
                    ph.forEachIndexed { k, (bytes, w, h) ->
                        val sc = minOf((slotW - 4) / w, (cellH - 6) / h, 1.0)
                        val dw = w * sc; val dh = h * sc
                        val offX = k * slotW + (slotW - dw) / 2 + 2
                        val offY = (cellH - dh) / 2
                        sh.image(bytes, "jpeg", r, cx(PHOTO_IDX), (offX * EMU).toLong(), (offY * EMU).toLong(), (dw * EMU).toLong(), (dh * EMU).toLong())
                    }
                }
            }
        }
        wb.write(out)
    }
}
