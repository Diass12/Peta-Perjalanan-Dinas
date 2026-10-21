package id.tub.dinas.domain

/**
 * Logika kode aset: pola `TU` + 3 huruf + 5..7 angka (mis. TUBOE250018).
 * Diterjemahkan dari AsetScan (web) dan diperketat di dua tempat:
 *  - barcode/QR yang bukan pola kode tidak dijadikan kode aset;
 *  - status Tagged hanya bila kode yang terbaca sama dengan kode aset itu sendiri.
 */
object KodeAset {
    private val DIGIT_KE_HURUF = mapOf('0' to 'O', '1' to 'I', '5' to 'S', '8' to 'B', '2' to 'Z', '6' to 'G')
    private val HURUF_KE_DIGIT = mapOf('O' to '0', 'Q' to '0', 'D' to '0', 'I' to '1', 'L' to '1', 'S' to '5', 'B' to '8', 'Z' to '2', 'G' to '6')
    /** Awalan 5 karakter: TUBxx (TUB) atau BAUxx (PT Bara Alam Utama). */
    private val AWALAN = Regex("^(TU[A-Z]{3}|BAU[A-Z]{2})$")
    private val AKHIRAN = Regex("^\\d{5,7}$")
    private val NON_ALNUM = Regex("[^A-Z0-9]")

    fun norm(s: String?): String = (s ?: "").uppercase().replace(NON_ALNUM, "")

    /** Satu kandidat; null bila bukan kode yang sah. Koreksi salah-baca OCR dilakukan di sini. */
    fun validasi(kandidat: String): String? {
        if (kandidat.length < 10) return null
        val awal = kandidat.take(5).map { c -> if (c.isDigit()) (DIGIT_KE_HURUF[c] ?: c) else c }.joinToString("")
        val akhir = kandidat.drop(5).map { c -> if (c in 'A'..'Z') (HURUF_KE_DIGIT[c] ?: c) else c }.joinToString("")
        return if (AWALAN.matches(awal) && AKHIRAN.matches(akhir)) awal + akhir else null
    }

    /** Semua kode dalam teks (per baris; panjang angka dicoba 6, 5, lalu 7). */
    fun ekstrak(teks: String?): List<String> {
        val hasil = LinkedHashSet<String>()
        (teks ?: "").uppercase().split(Regex("\\r?\\n")).forEach { baris ->
            val a = baris.replace(NON_ALNUM, "")
            for ((awal, urut) in listOf("TU" to intArrayOf(6, 5, 7), "BAU" to intArrayOf(7, 6, 5))) {
                var i = a.indexOf(awal)
                while (i >= 0) {
                    for (n in urut) {
                        val k = validasi(a.substring(i, minOf(a.length, i + 5 + n)))
                        if (k != null) { hasil.add(k); break }
                    }
                    i = a.indexOf(awal, i + 1)
                }
            }
        }
        return hasil.toList()
    }

    fun levenshtein(a: String, b: String): Int {
        val d = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) d[i][0] = i
        for (j in 0..b.length) d[0][j] = j
        for (i in 1..a.length) for (j in 1..b.length)
            d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
        return d[a.length][b.length]
    }

    class Cocok<T>(val kode: String, val aset: T?, val fuzzy: Boolean)

    /** Mencocokkan ke register: persis, lalu beda 1 karakter (satu kandidat), lalu mirip (satu kandidat). */
    fun <T> cocokkan(raw: String, daftar: List<T>, kodeDari: (T) -> String?): Cocok<T> {
        val t = norm(raw)
        if (t.isEmpty()) return Cocok(raw, null, false)
        daftar.firstOrNull { val k = kodeDari(it); !k.isNullOrEmpty() && norm(k) == t }?.let { return Cocok(kodeDari(it)!!, it, false) }
        val satuBeda = daftar.filter { a ->
            val k = kodeDari(a)
            if (k.isNullOrEmpty()) return@filter false
            val n = norm(k)
            kotlin.math.abs(n.length - t.length) <= 1 && levenshtein(n, t) == 1
        }
        if (satuBeda.size == 1) return Cocok(kodeDari(satuBeda[0])!!, satuBeda[0], true)
        val mirip = daftar.filter { a ->
            val k = kodeDari(a)
            if (k.isNullOrEmpty()) return@filter false
            val n = norm(k)
            t.length >= 9 && n.takeLast(6) == t.takeLast(6) && n.take(2) == t.take(2) && levenshtein(n, t) <= 2
        }
        if (mirip.size == 1) return Cocok(kodeDari(mirip[0])!!, mirip[0], true)
        return Cocok(raw, null, false)
    }

    /** Dua huruf kelas: karakter indeks 3..4 (TUBOE250018 -> "OE"). */
    fun kodeKelas(kode: String): String = norm(kode).let { if (it.length >= 5) it.substring(3, 5) else "" }

    class Kelas(val kode: String, val nama: String, val kategori: String)

    val KELAS = listOf(
        Kelas("ME", "Machinery and equipment", "machine"),
        Kelas("OE", "Office Equipment", "office"),
        Kelas("VC", "Vehicles", "vehicle"),
        Kelas("CB", "Camp Building", "building"),
    )
    private val KODE_TAMBAHAN = mapOf("VH" to "vehicle", "VE" to "vehicle", "BD" to "building")

    fun kelasDariKode(kode: String): Kelas? = kodeKelas(kode).let { k -> KELAS.firstOrNull { it.kode == k } }
    fun kelasDariNama(nama: String?): Kelas? = nama?.trim()?.takeIf { it.isNotEmpty() }?.let { n -> KELAS.firstOrNull { it.nama.equals(n, ignoreCase = true) } }

    val KATEGORI = listOf("vehicle", "machine", "office", "building", "other")
    fun labelKategori(k: String) = when (k) {
        "vehicle" -> "Kendaraan"; "machine" -> "Mesin & Peralatan"; "office" -> "Office Equipment"; "building" -> "Bangunan"; else -> "Lainnya"
    }

    /** Kategori dari kode kelas; bila tak dikenal ditebak dari nama. */
    fun tebakKategori(kode: String, nama: String = ""): String {
        val k = kodeKelas(kode)
        KELAS.firstOrNull { it.kode == k }?.let { return it.kategori }
        KODE_TAMBAHAN[k]?.let { return it }
        val n = "$nama $kode".lowercase()
        return when {
            Regex("mobil|motor|truck|truk|dump|\\blv-?\\d|pickup|bus\\b|kendaraan|vehicle|car\\b").containsMatchIn(n) -> "vehicle"
            Regex("genset|pompa|mesin|compressor|excavator|crusher|generator|forklift|las\\b|bor\\b|panel").containsMatchIn(n) -> "machine"
            Regex("meja|kursi|lemari|printer|laptop|\\bac\\b|cctv|ranjang|freezer|komputer|sofa|rak\\b|absen|kulkas|dispenser|tv\\b").containsMatchIn(n) -> "office"
            Regex("gedung|bangunan|mess|gudang|building|rumah").containsMatchIn(n) -> "building"
            else -> "other"
        }
    }

    /**
     * Status barcode:
     *  - ada kode terbaca di foto yang sama dengan kode aset  -> TAGGED
     *  - aset punya kode tetapi tidak terbaca di foto          -> UNTAGGED (kecuali asal Excel yang sudah TAGGED)
     *  - aset tanpa kode                                       -> NONASSET
     * Bila sumber MANUAL, tidak ditimpa kecuali `paksa`.
     */
    fun hitungStatusBarcode(
        kode: String, kodeTerbaca: List<String>, sudahOcr: Boolean,
        status: StatusBarcode?, sumber: SumberBarcode, paksa: Boolean = false,
    ): Pair<StatusBarcode?, SumberBarcode> {
        if (sumber == SumberBarcode.MANUAL && !paksa) return status to sumber
        val n = norm(kode)
        return when {
            n.isNotEmpty() && kodeTerbaca.any { norm(it) == n } -> StatusBarcode.TAGGED to SumberBarcode.OCR
            n.isNotEmpty() ->
                if (sumber == SumberBarcode.EXCEL && status == StatusBarcode.TAGGED) status to sumber
                else StatusBarcode.UNTAGGED to (if (sudahOcr) SumberBarcode.OCR else SumberBarcode.AUTO)
            else -> StatusBarcode.NONASSET to (if (sudahOcr) SumberBarcode.OCR else SumberBarcode.AUTO)
        }
    }
}

enum class StatusBarcode(val label: String) {
    TAGGED("Tagged"), UNTAGGED("Untagged"), NONASSET("Non Aset");
    companion object { fun from(s: String?): StatusBarcode? = entries.firstOrNull { it.name.equals(s, true) } }
}

enum class SumberBarcode {
    NONE, OCR, EXCEL, AUTO, MANUAL;
    companion object { fun from(s: String?): SumberBarcode = entries.firstOrNull { it.name.equals(s, true) } ?: NONE }
}

enum class AuditState(val label: String) {
    PENDING("Belum"), FOUND("Ditemukan"), MISSING("Tidak Ada"), DAMAGED("Rusak");
    companion object { fun from(s: String?): AuditState = entries.firstOrNull { it.name.equals(s, true) } ?: PENDING }
}
