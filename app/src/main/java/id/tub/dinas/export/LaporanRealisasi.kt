package id.tub.dinas.export

import id.tub.dinas.domain.BonKategori
import id.tub.dinas.domain.BonModel
import id.tub.dinas.domain.UmBundle
import java.io.OutputStream

/** Identitas pada kepala & tanda tangan laporan. */
class IdentitasLaporan(
    val perusahaan: String, val kota: String, val tanggalCetak: String,
    val penyiap: String, val jabatanPenyiap: String, val pemeriksa: String,
)

/**
 * Laporan Realisasi Biaya Perjalanan Dinas (format LPBPD): satu lembar per pengajuan uang muka, kolom
 * Akomodasi / Transportasi / Konsumsi / Uang Saku / Lain-lain, nomor bagian + sub-poin, rumus SUM, blok bon sementara
 * dan kelebihan/kekurangan dana, serta kotak tanda tangan.
 */
object LaporanRealisasi {
    private const val FONT = "Arial Narrow"
    private const val NUM = "_(* #,##0_);_(* \\(#,##0\\);_(* \"-\"??_);_(@_)"
    private val BLN = listOf("Jan", "Feb", "Mar", "Apr", "Mei", "Jun", "Jul", "Agu", "Sept", "Okt", "Nov", "Des")

    /** yyyy-MM-dd -> "29 Sept 2026". */
    fun tgl(iso: String): String {
        val p = iso.split("-"); if (p.size != 3) return iso
        val m = p[1].toIntOrNull() ?: return iso; val d = p[2].toIntOrNull() ?: return iso
        return "$d ${BLN.getOrElse(m - 1) { p[1] }} ${p[0]}"
    }

    private class Bagian(val nama: String, val kolom: Int, val kat: Set<BonKategori>, val tanpaTanggal: Boolean = false, val tag: String? = null)
    // kolom: 5 Akomodasi, 6 Transportasi, 7 Konsumsi, 8 Uang Saku, 9 Lain-lain
    private val BAGIAN = listOf(
        Bagian("Tiket Pesawat", 6, setOf(BonKategori.TIKET)),
        Bagian("Travel / Taxi", 6, setOf(BonKategori.TRANSPORTASI)),
        Bagian("Speedboat", 6, setOf(BonKategori.KAPAL)),
        Bagian("Penginapan", 5, setOf(BonKategori.HOTEL), tanpaTanggal = true),
        Bagian("Uang Makan", 7, setOf(BonKategori.MAKAN_PAGI, BonKategori.MAKAN_SIANG, BonKategori.MAKAN_MALAM)),
        Bagian("Uang Saku", 8, setOf(BonKategori.UANG_SAKU), tanpaTanggal = true),
        Bagian("Lain - lain", 9, setOf(BonKategori.LAUNDRY, BonKategori.LAINNYA)),
    )

    fun tulis(b: UmBundle, id: IdentitasLaporan, logo: ByteArray?, out: OutputStream) {
        val wb = XlsxWriter()
        val sh = wb.sheet("LPBPD"); sh.landscape = true
        sh.colWidths(listOf(3.4, 0.0, 23.9, 2.9, 25.4, 12.5, 13.5, 12.5, 12.0, 12.0, 13.0, 15.1))
        fun st(bold: Boolean = false, size: Double = 9.0, h: String? = null, bd: String? = null, nf: String? = null, fill: String? = null, wrap: Boolean = false, v: String = "center") =
            XStyle(bold = bold, size = size, color = "000000", fill = fill, wrap = wrap, h = h, v = v, font = FONT, numFmt = nf, bd = bd)
        val sisi = "thin,thin,,hair"
        val tAngka = st(nf = NUM, bd = sisi); val tTeks = st(h = "left", bd = ",,,hair"); val tTebal = st(bold = true, h = "left", bd = ",,,hair")
        val tNo = st(bold = true, h = "center", bd = sisi); val tKosong = st(bd = sisi)
        val abu = "D9D9D9"

        // Kepala
        sh.rowHeight(1, 34.0); sh.rowHeight(2, 44.0); sh.rowHeight(3, 15.0)
        sh.merge(1, 0, 2, 2, st(bd = "thin,,thin,"))
        logo?.let {
            // Muat dalam kotak 130x96 px, di tengah; ukuran asli dibaca dari header PNG.
            var w = 130; var h = 96
            if (it.size > 24 && it[1] == 'P'.code.toByte()) {
                fun i4(o: Int) = ((it[o].toInt() and 255) shl 24) or ((it[o + 1].toInt() and 255) shl 16) or ((it[o + 2].toInt() and 255) shl 8) or (it[o + 3].toInt() and 255)
                val pw = i4(16); val ph = i4(20)
                if (pw > 0 && ph > 0) { val sc = minOf(130.0 / pw, 96.0 / ph); w = (pw * sc).toInt().coerceAtLeast(1); h = (ph * sc).toInt().coerceAtLeast(1) }
            }
            sh.image(it, "png", 1, 2, ((172 - w).coerceAtLeast(0) / 2) * 9525L, ((104 - h).coerceAtLeast(0) / 2) * 9525L, w * 9525L, h * 9525L)
        }
        sh.merge(3, 0, 3, 2, st(bold = true, h = "center", bd = "thin,,,thin")); sh.text(3, 0, id.perusahaan, st(bold = true, h = "center", bd = "thin,,,thin"))
        sh.merge(1, 3, 2, 11, st(bold = true, size = 16.0, h = "center", bd = ",thin,thin,"))
        sh.text(1, 3, "LAPORAN REALISASI BIAYA PERJALANAN DINAS", st(bold = true, size = 16.0, h = "center", bd = ",thin,thin,"))
        sh.merge(3, 3, 3, 11, st(bold = true, size = 9.0, h = "center", bd = ",thin,,thin"))
        sh.text(3, 3, listOfNotNull(b.um.judul.takeIf { it.isNotBlank() }, b.um.nomor.takeIf { it.isNotBlank() }).joinToString(" · "), st(bold = true, size = 9.0, h = "center", bd = ",thin,,thin"))

        // Judul kolom
        val hd = st(bold = true, size = 10.0, h = "center", bd = "thin,thin,thin,thin", fill = abu)
        sh.rowHeight(5, 16.0); sh.rowHeight(6, 16.0); sh.rowHeight(7, 5.4)
        sh.merge(5, 0, 6, 0, hd); sh.text(5, 0, "NO", hd)
        sh.merge(5, 1, 6, 1, hd)
        sh.merge(5, 2, 6, 4, hd); sh.text(5, 2, "URAIAN", hd)
        sh.merge(5, 5, 5, 11, hd); sh.text(5, 5, "BIAYA YANG DIKELUARKAN", hd)
        val hd2 = st(bold = true, size = 10.0, h = "center", bd = "thin,thin,thin,double", fill = abu)
        listOf("AKOMODASI", "TRANSPORTASI", "KONSUMSI", "UANG SAKU", "LAIN-LAIN", "JUMLAH", "KETERANGAN").forEachIndexed { i, t -> sh.text(6, 5 + i, t, hd2) }

        // Isi
        var r = 8; var no = 0
        val awal = r + 1
        val tDalam = st(bd = ",,,hair")
        fun isiBaris(row: Int) { for (c in 0..11) sh.style(row, c, if (c in 2..4) tDalam else tKosong) }
        fun sum(row: Int, nilai: Double) = sh.formula(row, 10, "SUM(F${row + 1}:J${row + 1})", nilai, tAngka)
        val total = DoubleArray(5)
        val kustom = b.bons.map { it.bon }.filter { it.tag.isNotBlank() }.groupBy { it.tag.trim() }.map { (t, l) ->
            val k = l.first().kategori
            Bagian(t, when (k) { BonKategori.HOTEL -> 5; BonKategori.TIKET, BonKategori.TRANSPORTASI, BonKategori.KAPAL -> 6; BonKategori.MAKAN_PAGI, BonKategori.MAKAN_SIANG, BonKategori.MAKAN_MALAM -> 7; BonKategori.UANG_SAKU -> 8; else -> 9 }, setOf(k), tag = t)
        }
        val urut = BAGIAN.take(6) + kustom + BAGIAN.last()
        for (bg in urut) {
            val items = b.bons.map { it.bon }.filter { if (bg.tag != null) it.tag.trim() == bg.tag else it.kategori in bg.kat && it.tag.isBlank() }.sortedWith(compareBy<BonModel> { it.tanggal }.thenBy { it.order })
            if (items.isEmpty()) continue
            no++
            isiBaris(r); sh.number(r, 0, no.toDouble(), tNo); sh.merge(r, 2, r, 4, tTebal); sh.text(r, 2, bg.nama, tTebal); r++
            if (bg.kolom == 7 && bg.tag == null) {
                var abj = 'a'
                for ((tg, grup) in items.groupBy { it.tanggal }) {
                    isiBaris(r); sh.text(r, 2, "${abj++}. ${tgl(tg)} :", tTeks); r++
                    grup.forEachIndexed { i, x ->
                        isiBaris(r); sh.number(r, 3, (i + 1).toDouble(), st(h = "right", bd = ",,,hair")); sh.text(r, 4, x.uraian.ifBlank { x.kategori.label }, tTeks)
                        sh.number(r, bg.kolom, x.jumlah, tAngka); sum(r, x.jumlah); total[bg.kolom - 5] += x.jumlah; r++
                    }
                }
            } else {
                var abj = 'a'
                for (x in items) {
                    isiBaris(r); sh.merge(r, 2, r, 4, tTeks)
                    val u = x.uraian.ifBlank { x.kategori.label }
                    sh.text(r, 2, "${abj++}. $u" + if (!bg.tanpaTanggal && x.tanggal.isNotBlank()) ", ${tgl(x.tanggal)}" else if (bg.tanpaTanggal && x.uraian.isBlank() && x.tanggal.isNotBlank()) " ${tgl(x.tanggal)}" else "", tTeks)
                    sh.number(r, bg.kolom, x.jumlah, tAngka); sum(r, x.jumlah); total[bg.kolom - 5] += x.jumlah; r++
                }
            }
            isiBaris(r); sh.rowHeight(r, 7.0); r++
        }
        val akhir = r - 1
        val jum = total.sum()
        val tt = st(bold = true, size = 10.0, nf = NUM, bd = "thin,thin,thin,thin")
        val tl = st(bold = true, size = 10.0, h = "left", bd = "thin,thin,thin,thin")
        for (c in 0..11) sh.style(r, c, tl)
        sh.merge(r, 0, r, 4, tl); sh.text(r, 0, "TOTAL", tl)
        for (k in 0..4) sh.formula(r, 5 + k, "SUM(${XlsxWriter.ref(awal - 1, 5 + k).filter { it.isLetter() }}$awal:${XlsxWriter.ref(awal - 1, 5 + k).filter { it.isLetter() }}${akhir + 1})", total[k], tt)
        sh.formula(r, 10, "SUM(K$awal:K${akhir + 1})", jum, tt)
        val barisTotal = r
        r += 2

        // Keterangan
        val k1 = st(bold = true, size = 11.0, h = "left"); val k2 = st(h = "left"); val kn = st(nf = NUM, h = "left")
        sh.text(r, 0, "KETERANGAN", k1); r++
        val pencairan = mutableListOf<Pair<String, Double>>()
        val dasar = if (b.um.jumlahCair > 0) b.um.jumlahCair else b.um.jumlahDiajukan
        if (dasar > 0) pencairan += (b.um.tglCair.ifBlank { b.um.tglPengajuan }) to dasar
        pencairan += b.um.pencairanTambahan
        val barisBon = ArrayList<Int>()
        pencairan.forEachIndexed { i, (t, j) ->
            sh.text(r, 0, if (i == 0) "1.  Bon Sementara ${tgl(t)}" else "     Bon Sementara ${tgl(t)}", k2)
            sh.text(r, 3, "=", k2); sh.number(r, 4, j, kn); barisBon += r; r++
        }
        if (pencairan.isEmpty()) { sh.text(r, 0, "1.  Bon Sementara", k2); sh.text(r, 3, "=", k2); sh.number(r, 4, 0.0, kn); barisBon += r; r++ }
        sh.text(r, 0, "2.  Total Biaya Perjalanan", k2); sh.text(r, 3, "=", k2); sh.formula(r, 4, "K${barisTotal + 1}", jum, kn); val barisTb = r; r++
        val sumBon = pencairan.sumOf { it.second }
        sh.text(r, 0, "3.  Kelebihan Dana / Kekurangan Dana", k2); sh.text(r, 3, "=", k2)
        sh.formula(r, 4, "SUM(E${barisBon.first() + 1}:E${barisBon.last() + 1})-E${barisTb + 1}", sumBon - jum, kn); r++
        sh.text(r, 0, if (sumBon - jum >= 0) "     (lebih: dikembalikan ke perusahaan)" else "     (kurang: ditagihkan ke perusahaan)", st(h = "left", size = 8.0).copy(italic = true))

        // Tanda tangan (kolom H:J dan K:L)
        var s = barisTotal + 2
        sh.merge(s, 10, s, 11, st(h = "right", bd = ",,,thin")); sh.text(s, 10, "${id.kota}, ${id.tanggalCetak}", st(h = "right", bd = ",,,thin"))
        s++
        val kotak = st(h = "center", bd = "thin,thin,thin,thin")
        sh.merge(s, 7, s, 9, kotak); sh.text(s, 7, "Dipersiapkan Oleh ", kotak)
        sh.merge(s, 10, s, 11, kotak); sh.text(s, 10, "Diperiksa Oleh ", kotak)
        for (i in 1..5) { sh.rowHeight(s + i, 15.0); sh.merge(s + i, 7, s + i, 9, st(bd = "thin,thin,,")); sh.merge(s + i, 10, s + i, 11, st(bd = "thin,thin,,")) }
        val n = s + 6
        sh.merge(n, 7, n, 9, st(bold = true, h = "center", bd = "thin,thin,thin,thin")); sh.text(n, 7, id.penyiap, st(bold = true, h = "center", bd = "thin,thin,thin,thin"))
        sh.merge(n, 10, n, 11, st(bold = true, h = "center", bd = "thin,thin,thin,thin")); sh.text(n, 10, id.pemeriksa, st(bold = true, h = "center", bd = "thin,thin,thin,thin"))
        sh.merge(n + 1, 7, n + 1, 9, kotak); sh.text(n + 1, 7, id.jabatanPenyiap, kotak)
        sh.merge(n + 1, 10, n + 1, 11, kotak)
        wb.write(out)
    }
}
