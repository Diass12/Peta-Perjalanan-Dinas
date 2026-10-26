package id.tub.dinas.um

/** Angka rupiah dalam kata, mis. 1450000 -> "Satu Juta Empat Ratus Lima Puluh Ribu Rupiah". */
object Terbilang {
    private val SATUAN = arrayOf("", "Satu", "Dua", "Tiga", "Empat", "Lima", "Enam", "Tujuh", "Delapan", "Sembilan", "Sepuluh", "Sebelas")

    private fun tiga(n: Long): String {
        require(n in 0..999)
        return when {
            n < 12 -> SATUAN[n.toInt()]
            n < 20 -> SATUAN[(n - 10).toInt()] + " Belas"
            n < 100 -> SATUAN[(n / 10).toInt()] + " Puluh" + (if (n % 10 > 0) " " + SATUAN[(n % 10).toInt()] else "")
            n < 200 -> "Seratus" + (if (n % 100 > 0) " " + tiga(n % 100) else "")
            else -> SATUAN[(n / 100).toInt()] + " Ratus" + (if (n % 100 > 0) " " + tiga(n % 100) else "")
        }
    }

    fun kata(n: Long): String {
        if (n == 0L) return "Nol"
        if (n < 0) return "Minus " + kata(-n)
        val satuan = listOf(1_000_000_000_000L to "Triliun", 1_000_000_000L to "Miliar", 1_000_000L to "Juta", 1_000L to "Ribu")
        var sisa = n
        val bagian = ArrayList<String>()
        for ((nilai, nama) in satuan) {
            val q = sisa / nilai
            if (q > 0) {
                bagian.add(if (nilai == 1_000L && q == 1L) "Seribu" else tiga(q) + " " + nama)
                sisa %= nilai
            }
        }
        if (sisa > 0) bagian.add(tiga(sisa))
        return bagian.joinToString(" ")
    }

    fun rupiah(n: Long): String = kata(n) + " Rupiah"
}
