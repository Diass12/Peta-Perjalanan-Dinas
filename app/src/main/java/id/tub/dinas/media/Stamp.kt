package id.tub.dinas.media

import android.graphics.*
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import id.tub.dinas.domain.GeoPoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.abs

/** Data yang diketahui editor saat foto diambil. Waktu dan kode foto ditambahkan saat dicap. */
class StampSource(val title: String, val note: String, val geo: GeoPoint?, val address: String?)

/** Cap waktu dan lokasi di pojok kiri bawah foto, ditanam permanen ke gambar. */
object Stamp {
    fun newCode(): String = UUID.randomUUID().toString().replace("-", "").take(16).uppercase(Locale.ROOT)

    fun coord(g: GeoPoint): String =
        String.format(Locale.US, "%.6f°%s, %.6f°%s", abs(g.lat), if (g.lat < 0) "S" else "N", abs(g.lng), if (g.lng < 0) "W" else "E")

    fun draw(src: Bitmap, s: StampSource, geo: GeoPoint?, timeMs: Long, code: String): Bitmap {
        val bmp = if (src.isMutable) src else src.copy(Bitmap.Config.ARGB_8888, true).also { src.recycle() }
        val w = bmp.width; val h = bmp.height
        val u = minOf(w, h) / 1080f
        val m = 32f * u
        val c = Canvas(bmp)
        val width = (w - 2 * m).toInt()
        val loc = Locale("id", "ID")
        val date = SimpleDateFormat("EEE, dd MMM yyyy", loc).format(Date(timeMs))
        val time = SimpleDateFormat("HH:mm", loc).format(Date(timeMs))

        fun tp(size: Float, bold: Boolean = false, color: Int = Color.WHITE) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size * u; this.color = color; typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            setShadowLayer(4f * u, 0f, 1.5f * u, 0xCC000000.toInt())
        }
        fun layout(text: String, p: TextPaint, wd: Int = width, maxLines: Int = 3) =
            StaticLayout.Builder.obtain(text, 0, text.length, p, wd).setMaxLines(maxLines).setEllipsize(android.text.TextUtils.TruncateAt.END)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).build()

        val blocks = ArrayList<StaticLayout>()
        blocks += layout(date, tp(40f, true), maxLines = 1)
        s.address?.takeIf { it.isNotBlank() }?.let { blocks += layout(it, tp(34f), (width * 0.85f).toInt(), 3) }
        geo?.let { blocks += layout(coord(it), tp(34f), maxLines = 1) }
        val noteText = s.note.trim().ifBlank { "" }
        if (noteText.isNotEmpty()) blocks += layout("Catatan: $noteText", tp(34f), (width * 0.85f).toInt(), 2)
        val codeLayout = layout("Kode Foto: $code", tp(28f, true, 0xFFE8E8E8.toInt()), maxLines = 1)

        val gap = 8f * u
        val timeH = 84f * u
        var total = timeH + gap
        blocks.forEach { total += it.height + gap }
        total += codeLayout.height + gap
        val top = h - m - total

        // gradasi gelap tipis di bawah agar teks terbaca di foto terang
        val gp = Paint().apply { shader = LinearGradient(0f, top - 2 * m, 0f, h.toFloat(), 0x00000000, 0xAA000000.toInt(), Shader.TileMode.CLAMP) }
        c.drawRect(0f, top - 2 * m, w.toFloat(), h.toFloat(), gp)

        // baris waktu: label emas + jam besar
        var y = top
        val tag = tp(36f, true, 0xFF1A2B4A.toInt()).apply { clearShadowLayer() }
        val tagText = s.title.trim().ifBlank { "Dinas" }.let { if (it.length > 14) it.take(13) + "…" else it }
        val tagW = tag.measureText(tagText) + 28f * u
        val tagRect = RectF(m, y + 14f * u, m + tagW, y + 14f * u + 56f * u)
        c.drawRoundRect(tagRect, 10f * u, 10f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFC94A.toInt() })
        c.drawText(tagText, m + 14f * u, tagRect.centerY() + tag.textSize * 0.35f, tag)
        val big = tp(76f, true)
        c.drawText(time, tagRect.right + 18f * u, y + timeH * 0.78f, big)
        y += timeH + gap

        // garis emas di kiri blok keterangan
        val blockTop = y
        blocks.forEach { l -> c.save(); c.translate(m + 18f * u, y); l.draw(c); c.restore(); y += l.height + gap }
        c.drawRect(m, blockTop, m + 6f * u, y - gap, Paint().apply { color = 0xFFFFC94A.toInt() })
        c.save(); c.translate(m, y); codeLayout.draw(c); c.restore()
        return bmp
    }
}
