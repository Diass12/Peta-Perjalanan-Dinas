package id.tub.dinas.export

import java.io.OutputStream

/**
 * Templat Excel bawaan untuk impor aset (format "Master Database"): sheet data + sheet foto/pendukung.
 * Impor aplikasi mengenali kolom ini langsung; kolom yang tidak diisi dibiarkan kosong.
 */
object AsetTemplate {
    fun tulis(out: OutputStream) {
        val wb = XlsxWriter()
        val head = XStyle(bold = true, size = 10.0, fill = "FABF8F", h = "center", v = "center", border = true)
        val body = XStyle(size = 10.0, border = true, v = "center")
        val bodyC = body.copy(h = "center")

        // ---- Sheet 1: Data Base Aktiva ----
        val d = wb.sheet("Data Base Aktiva")
        d.colWidths(listOf(5.0, 38.0, 6.0, 6.0, 6.0, 6.0, 6.0, 18.0, 16.0, 20.0, 16.0, 12.0, 16.0, 24.0))
        val judul = listOf("No", "Keterangan", "Kategori Asset", "", "", "", "", "Kode Asset", "Tanggal Tercatat", "User", "Lokasi", "BARCODE", "Dept.", "PT Pemilik")
        judul.forEachIndexed { i, t -> d.text(0, i, t, head) }
        listOf("CB", "IN", "ME", "OE", "VC").forEachIndexed { i, t -> d.text(1, 2 + i, t, head) }
        d.merge(0, 2, 0, 6, head)
        for (c in listOf(0, 1, 7, 8, 9, 10, 11, 12, 13)) d.merge(0, c, 1, c, head)
        d.rowHeight(0, 22.0); d.rowHeight(1, 18.0)
        for (r in 0 until 300) {
            d.number(2 + r, 0, (r + 1).toDouble(), bodyC)
            for (c in 1..13) d.style(2 + r, c, body)
        }

        // ---- Sheet 2: Foto dan data pendukung ----
        val f = wb.sheet("Foto dan data Pendukung AKtiva")
        f.colWidths(listOf(2.0, 2.0, 2.0, 5.0, 22.0, 16.0, 14.0, 26.0, 14.0, 14.0, 16.0, 13.0, 40.0, 13.0))
        val judulStyle = XStyle(bold = true, size = 14.0, wrap = false)
        val lab = XStyle(bold = true, size = 11.0, wrap = false)
        f.text(0, 1, "Physical Check - Fixed Asset Good Condition", judulStyle)
        f.text(2, 1, "Company", lab); f.text(2, 2, ":", lab)
        f.text(3, 1, "Date", lab); f.text(3, 2, ":", lab)
        f.text(5, 3, "Listing Assets", lab)
        listOf("No.", "Assets Classification", "Asset Number", "Purchase Date", "Description", "Location", "Dept.", "User", "Condition", "Photo", "Barcode")
            .forEachIndexed { i, t -> f.text(6, 3 + i, t, head) }
        f.rowHeight(6, 22.0)

        // ---- Sheet 3: Petunjuk ----
        val p = wb.sheet("Petunjuk")
        p.colWidths(listOf(110.0))
        val h = XStyle(bold = true, size = 13.0, wrap = false)
        val t = XStyle(size = 11.0, wrap = true)
        listOf(
            "Petunjuk pengisian templat impor aset",
            "1. Sheet \"Data Base Aktiva\": isi satu aset per baris. Kolom minimal: Keterangan (nama barang) dan Kode Asset.",
            "2. Kode Asset: PT + jenis + tahun + urutan, contoh BAUME2600001 / TUBOE250001. Dua huruf ke-4 dan ke-5 menentukan kelas: ME mesin, OE office equipment, VC kendaraan, CB camp building.",
            "3. Tanggal Tercatat: tulis sebagai tanggal (yyyy-mm-dd atau dd/mm/yyyy). Kolom User, Lokasi, Dept., PT Pemilik boleh dikosongkan.",
            "4. BARCODE: kosongkan, atau isi Tagged / Untagged / Non Asset bila sudah diketahui.",
            "5. Sheet \"Foto dan data Pendukung AKtiva\": isi nama PT di sebelah \"Company :\" (dipakai sebagai PT Pemilik bila kolom PT Pemilik kosong). Foto boleh ditempel di sel kolom Photo pada baris aset; baris dengan Asset Number sama digabung dengan sheet data.",
            "6. Impor lewat Menu Aset → Impor Excel. Aplikasi menampilkan perbandingan database dan file; centang yang ingin diproses, lalu Konfirmasi.",
            "7. Jangan mengubah judul kolom di baris pertama.",
        ).forEachIndexed { i, s -> p.text(i * 2, 0, s, if (i == 0) h else t); p.rowHeight(i * 2, if (i == 0) 22.0 else 34.0) }
        wb.write(out)
    }
}
