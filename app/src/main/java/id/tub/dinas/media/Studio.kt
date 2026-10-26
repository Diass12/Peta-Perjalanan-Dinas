package id.tub.dinas.media

import android.graphics.*

/** Filter, bingkai, dan kolase untuk Studio Foto. Semua hasil berupa Bitmap baru; foto asli tidak diubah. */
object Studio {
    class Filter(val nama: String, val matrix: ColorMatrix?)
    class Layout(val nama: String, val sel: List<RectF>)

    private fun cm(sat: Float = 1f, kontras: Float = 1f, geser: Float = 0f, r: Float = 1f, b: Float = 1f): ColorMatrix {
        val m = ColorMatrix().apply { setSaturation(sat) }
        m.postConcat(ColorMatrix(floatArrayOf(
            kontras * r, 0f, 0f, 0f, geser,
            0f, kontras, 0f, 0f, geser,
            0f, 0f, kontras * b, 0f, geser,
            0f, 0f, 0f, 1f, 0f)))
        return m
    }

    val FILTER = listOf(
        Filter("Asli", null),
        Filter("Cerah", cm(1.25f, 1.08f, 10f)),
        Filter("Hangat", cm(1.1f, 1.03f, 0f, 1.08f, 0.92f)),
        Filter("Sejuk", cm(1.05f, 1.03f, 0f, 0.92f, 1.08f)),
        Filter("Hitam putih", cm(0f, 1.1f, -4f)),
        Filter("Sepia", ColorMatrix(floatArrayOf(.393f, .769f, .189f, 0f, 0f, .349f, .686f, .168f, 0f, 0f, .272f, .534f, .131f, 0f, 0f, 0f, 0f, 0f, 1f, 0f))),
        Filter("Vintage", cm(0.7f, 0.95f, 15f, 1.05f, 0.9f)),
        Filter("Dramatis", cm(1.2f, 1.25f, -30f)),
    )

    val BINGKAI = listOf("Tanpa", "Putih", "Hitam", "Polaroid", "Emas")

    val LATAR = listOf("Putih" to Color.WHITE, "Krem" to Color.rgb(0xF6, 0xF4, 0xEE), "Hitam" to Color.rgb(0x12, 0x20, 0x2B))

    fun layouts(n: Int): List<Layout> = when (n) {
        2 -> listOf(Layout("Berdampingan", listOf(RectF(0f, 0f, .5f, 1f), RectF(.5f, 0f, 1f, 1f))),
            Layout("Bertumpuk", listOf(RectF(0f, 0f, 1f, .5f), RectF(0f, .5f, 1f, 1f))))
        3 -> listOf(Layout("Utama kiri", listOf(RectF(0f, 0f, .6f, 1f), RectF(.6f, 0f, 1f, .5f), RectF(.6f, .5f, 1f, 1f))),
            Layout("Utama atas", listOf(RectF(0f, 0f, 1f, .6f), RectF(0f, .6f, .5f, 1f), RectF(.5f, .6f, 1f, 1f))))
        else -> listOf(Layout("Kotak 2×2", listOf(RectF(0f, 0f, .5f, .5f), RectF(.5f, 0f, 1f, .5f), RectF(0f, .5f, .5f, 1f), RectF(.5f, .5f, 1f, 1f))))
    }

    fun filter(src: Bitmap, f: Filter): Bitmap {
        val m = f.matrix ?: return src
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(src, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(m) })
        return out
    }

    fun bingkai(src: Bitmap, jenis: Int, teks: String): Bitmap {
        if (jenis == 0) return src
        val w = src.width; val h = src.height; val s = minOf(w, h)
        val p = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        fun kanvas(kiri: Int, atas: Int, kanan: Int, bawah: Int, warna: Int): Pair<Bitmap, Canvas> {
            val o = Bitmap.createBitmap(w + kiri + kanan, h + atas + bawah, Bitmap.Config.ARGB_8888)
            val c = Canvas(o); c.drawColor(warna); c.drawBitmap(src, kiri.toFloat(), atas.toFloat(), p); return o to c
        }
        return when (jenis) {
            1 -> { val b = s / 25; kanvas(b, b, b, b, Color.WHITE).first }
            2 -> { val b = s / 25; kanvas(b, b, b, b, Color.rgb(0x12, 0x20, 0x2B)).first }
            3 -> {
                val b = s / 18; val bawah = (s * 0.26f).toInt()
                val (o, c) = kanvas(b, b, b, bawah, Color.rgb(0xF8, 0xF5, 0xEE))
                if (teks.isNotBlank()) {
                    val t = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0x33, 0x33, 0x33); textAlign = Paint.Align.CENTER; typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC) }
                    var ukuran = bawah * 0.28f; t.textSize = ukuran
                    while (t.measureText(teks) > o.width - 2f * b && ukuran > 10f) { ukuran -= 2f; t.textSize = ukuran }
                    c.drawText(teks, o.width / 2f, h + b + bawah * 0.55f, t)
                }
                o
            }
            else -> {
                val emas = Color.rgb(0xC9, 0x93, 0x2E); val b = s / 14; val tipis = s / 60
                val o = Bitmap.createBitmap(w + 2 * b, h + 2 * b, Bitmap.Config.ARGB_8888)
                val c = Canvas(o); c.drawColor(emas)
                val d = Paint().apply { color = Color.rgb(0xF8, 0xF5, 0xEE) }
                c.drawRect(tipis.toFloat(), tipis.toFloat(), (o.width - tipis).toFloat(), (o.height - tipis).toFloat(), d)
                val e = Paint().apply { color = emas }
                c.drawRect(b - tipis * 1.5f, b - tipis * 1.5f, o.width - b + tipis * 1.5f, o.height - b + tipis * 1.5f, e)
                c.drawBitmap(src, b.toFloat(), b.toFloat(), p); o
            }
        }
    }

    /** Kolase persegi [sisi] px; setiap foto dipotong tengah agar memenuhi selnya. */
    fun kolase(foto: List<Bitmap>, layout: Layout, latar: Int, sisi: Int): Bitmap {
        val o = Bitmap.createBitmap(sisi, sisi, Bitmap.Config.ARGB_8888)
        val c = Canvas(o); c.drawColor(latar)
        val gap = sisi * 0.012f; val p = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        layout.sel.forEachIndexed { i, r ->
            val b = foto.getOrNull(i) ?: return@forEachIndexed
            val tujuan = RectF(r.left * sisi + gap * (if (r.left == 0f) 1f else .5f), r.top * sisi + gap * (if (r.top == 0f) 1f else .5f),
                r.right * sisi - gap * (if (r.right == 1f) 1f else .5f), r.bottom * sisi - gap * (if (r.bottom == 1f) 1f else .5f))
            val rasioT = tujuan.width() / tujuan.height(); val rasioB = b.width.toFloat() / b.height
            val sumber = if (rasioB > rasioT) { val lb = (b.height * rasioT).toInt(); Rect((b.width - lb) / 2, 0, (b.width + lb) / 2, b.height) }
            else { val tg = (b.width / rasioT).toInt(); Rect(0, (b.height - tg) / 2, b.width, (b.height + tg) / 2) }
            c.drawBitmap(b, sumber, tujuan, p)
        }
        return o
    }
}
