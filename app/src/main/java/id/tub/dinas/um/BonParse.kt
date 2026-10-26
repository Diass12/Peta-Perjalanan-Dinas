package id.tub.dinas.um

/** Mengambil jumlah, tanggal, dan nama toko dari teks hasil baca (OCR) foto bon. Murni teks, tanpa Android. */
object BonParse {
    class Hasil(val total: Long?, val kandidat: List<Long>, val tanggal: String?, val toko: String?, val uraian: String? = null, val kategori: id.tub.dinas.domain.BonKategori? = null)

    private val ANGKA = Regex("""(?i)(?:rp\.?\s*)?(\d{1,3}(?:[.,]\d{3})+|\d{4,9})(?:[.,]\d{1,2})?(?!\d)""")
    private val BULAN = mapOf("jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "mei" to 5, "may" to 5, "jun" to 6, "jul" to 7,
        "agu" to 8, "ags" to 8, "aug" to 8, "sep" to 9, "okt" to 10, "oct" to 10, "nov" to 11, "des" to 12, "dec" to 12)
    /** Kata kunci jumlah akhir, dari yang paling meyakinkan. */
    private val TINGKAT = listOf(
        listOf("grand total", "total bayar", "total tagihan", "total pembayaran", "total harga", "jumlah bayar", "total amount", "amount due", "total dibayar"),
        listOf("total", "jumlah", "tagihan", "amount", "bayar", "tunai", "cash"),
    )
    private val ABAIKAN = listOf("subtotal", "sub total", "kembali", "kembalian", "change", "pajak", "tax", "ppn", "diskon", "service")

    private fun angka(s: String): List<Long> = ANGKA.findAll(s).mapNotNull { m ->
        val n = m.groupValues[1].replace(".", "").replace(",", "").toLongOrNull() ?: return@mapNotNull null
        val tahun = n in 1990L..2100L && m.groupValues[1].none { it == '.' || it == ',' } && !m.value.lowercase().startsWith("rp")
        n.takeIf { it in 1000..9_999_999_999L && !tahun }
    }.toList()

    fun total(baris: List<String>): Pair<Long?, List<Long>> {
        val semua = LinkedHashSet<Long>()
        var pilih: Long? = null
        for (kunci in TINGKAT) {
            val nilai = ArrayList<Long>()
            baris.forEachIndexed { i, b ->
                val l = b.lowercase()
                if (kunci.any { l.contains(it) } && ABAIKAN.none { l.contains(it) }) {
                    val a = angka(b).ifEmpty { baris.getOrNull(i + 1)?.let { angka(it) } ?: emptyList() }
                    nilai.addAll(a)
                }
            }
            if (nilai.isNotEmpty()) { semua.addAll(nilai.sortedDescending()); if (pilih == null) pilih = nilai.max() }
        }
        val lain = baris.flatMap { angka(it) }.sortedDescending().take(4)
        semua.addAll(lain)
        if (pilih == null) pilih = lain.firstOrNull()
        return pilih to semua.take(5)
    }

    fun tanggal(teks: String): String? {
        fun ok(d: Int, m: Int, y: Int) = d in 1..31 && m in 1..12 && y in 2000..2100
        fun iso(d: Int, m: Int, y: Int) = "%04d-%02d-%02d".format(y, m, d)
        val hasil = ArrayList<Pair<Int, String>>()
        Regex("""(\d{1,2})[/\-.](\d{1,2})[/\-.](\d{4})""").findAll(teks).forEach { m ->
            val d = m.groupValues[1].toInt(); val b = m.groupValues[2].toInt(); val y = m.groupValues[3].toInt()
            if (ok(d, b, y)) hasil.add(m.range.first to iso(d, b, y))
        }
        Regex("""(\d{4})[/\-.](\d{1,2})[/\-.](\d{1,2})""").findAll(teks).forEach { m ->
            val y = m.groupValues[1].toInt(); val b = m.groupValues[2].toInt(); val d = m.groupValues[3].toInt()
            if (ok(d, b, y)) hasil.add(m.range.first to iso(d, b, y))
        }
        Regex("""(?i)(\d{1,2})\s*(jan|feb|mar|apr|mei|may|jun|jul|agu|ags|aug|sep|okt|oct|nov|des|dec)[a-z]*\.?\s*,?\s*(\d{4})""").findAll(teks).forEach { m ->
            val d = m.groupValues[1].toInt(); val b = BULAN[m.groupValues[2].lowercase()] ?: return@forEach; val y = m.groupValues[3].toInt()
            if (ok(d, b, y)) hasil.add(m.range.first to iso(d, b, y))
        }
        return hasil.minByOrNull { it.first }?.second
    }

    fun toko(baris: List<String>): String? = baris.take(6).map { it.trim() }.firstOrNull { b ->
        val huruf = b.count { it.isLetter() }
        b.length in 3..40 && huruf >= 3 && huruf.toDouble() / b.length > 0.6 &&
            listOf("struk", "invoice", "receipt", "faktur", "nota", "terima kasih", "www.", "http").none { b.lowercase().contains(it) }
    }

    fun urai(teks: String): Hasil {
        val baris = teks.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val (t, k) = total(baris)
        return Hasil(t, k, tanggal(teks), toko(baris))
    }
}
