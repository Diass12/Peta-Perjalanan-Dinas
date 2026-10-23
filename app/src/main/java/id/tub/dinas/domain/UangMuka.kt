package id.tub.dinas.domain

enum class UmStatus(val label: String) {
    DIAJUKAN("Diajukan"), CAIR("Sudah cair"), SELESAI("Selesai");
    companion object { fun from(s: String?) = entries.firstOrNull { it.name == s } ?: DIAJUKAN }
}

/** Tag bon. */
enum class BonKategori(val label: String) {
    TIKET("Tiket Pesawat"), TRANSPORTASI("Transportasi"), KAPAL("Speedboat / Kapal"), HOTEL("Hotel"), MAKAN_PAGI("Makan Pagi"), MAKAN_SIANG("Makan Siang"),
    MAKAN_MALAM("Makan Malam"), UANG_SAKU("Uang Saku"), LAUNDRY("Laundry"), LAINNYA("Lainnya");
    companion object {
        /** Kategori disimpan sebagai "GRUP" atau "GRUP:nama kustom" (kategori buatan pengguna yang masuk ke kolom grup tsb). */
        fun encode(k: BonKategori, tag: String) = if (tag.isBlank()) k.name else "${k.name}:${tag.trim()}"
        fun tagDari(s: String?): String = s?.substringAfter(':', "")?.trim().orEmpty()
        fun from(s: String?): BonKategori {
            val t = s?.substringBefore(':')?.trim().orEmpty()
            entries.firstOrNull { it.name.equals(t, true) || it.label.equals(t, true) }?.let { return it }
            val l = t.lowercase()
            return when {
                l.contains("uang saku") || l.contains("uangsaku") -> UANG_SAKU
                l.contains("pesawat") || l.contains("tiket") -> TIKET
                l.contains("speedboat") || l.contains("kapal") || l.contains("ferry") || l.contains("feri") -> KAPAL
                l.contains("transport") || l.contains("taksi") || l.contains("taxi") || l.contains("travel") || l.contains("bensin") || l.contains("ojek") -> TRANSPORTASI
                l.contains("hotel") || l.contains("penginapan") || l.contains("losmen") -> HOTEL
                l.contains("pagi") || l.contains("sarapan") -> MAKAN_PAGI
                l.contains("siang") -> MAKAN_SIANG
                l.contains("malam") -> MAKAN_MALAM
                l.contains("laundry") || l.contains("binatu") -> LAUNDRY
                else -> LAINNYA
            }
        }
    }
}

/** Pengajuan uang muka (permintaan dana di muka untuk dinas) sekaligus realisasinya (kumpulan bon). */
data class UangMukaModel(
    val id: String,
    val nomor: String = "",
    val judul: String = "",
    val dinasId: String? = null,
    val pemohon: String = "",
    val tglPengajuan: String = "",     // yyyy-MM-dd
    val jumlahDiajukan: Double = 0.0,
    val tglCair: String = "",
    val jumlahCair: Double = 0.0,
    val catatan: String = "",
    val status: UmStatus = UmStatus.DIAJUKAN,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
) {
    /** Pencairan tambahan (bon sementara ke-2 dst), disimpan sebagai baris "#BON|yyyy-MM-dd|jumlah" di catatan. */
    val pencairanTambahan: List<Pair<String, Double>> get() = catatan.lines().mapNotNull { l ->
        if (!l.startsWith("#BON|")) return@mapNotNull null
        val p = l.split("|"); val j = p.getOrNull(2)?.trim()?.toDoubleOrNull() ?: return@mapNotNull null
        (p.getOrNull(1)?.trim().orEmpty()) to j
    }
    /** Catatan tanpa baris pencairan tambahan. */
    val catatanTeks: String get() = catatan.lines().filterNot { it.startsWith("#BON|") }.joinToString("\n").trim()
    companion object {
        fun gabungCatatan(teks: String, tambahan: List<Pair<String, Double>>) =
            (listOf(teks.trim()).filter { it.isNotEmpty() } + tambahan.map { "#BON|${it.first}|${it.second.toLong()}" }).joinToString("\n")
    }
}

data class BonModel(
    val id: String, val umId: String, val tanggal: String = "", val kategori: BonKategori = BonKategori.LAINNYA,
    val uraian: String = "", val jumlah: Double = 0.0, val order: Int = 0, val createdAt: Long = 0L,
    /** Nama kategori kustom (kosong = kategori bawaan). */
    val tag: String = "",
) { val labelKat: String get() = tag.ifBlank { kategori.label } }

data class BonFotoModel(val id: String, val bonId: String, val path: String, val order: Int)

class BonBundle(val bon: BonModel, val fotos: List<BonFotoModel>)

class UmBundle(val um: UangMukaModel, val bons: List<BonBundle>) {
    val totalBon: Double get() = bons.sumOf { it.bon.jumlah }
    /** Dana yang dipegang: jumlah cair bila sudah ada, kalau belum jumlah yang diajukan. */
    val dana: Double get() = (if (um.jumlahCair > 0) um.jumlahCair else um.jumlahDiajukan) + um.pencairanTambahan.sumOf { it.second }
    /** Positif = sisa uang muka harus dikembalikan; negatif = kurang, ditagihkan (reimburse). */
    val sisa: Double get() = dana - totalBon
    fun perKategori(): Map<BonKategori, Double> = BonKategori.entries.associateWith { k -> bons.filter { it.bon.kategori == k }.sumOf { it.bon.jumlah } }.filterValues { it != 0.0 }
}

enum class JadwalJenis(val label: String) {
    AUDIT("Audit"), MEETING("Meeting"), DINAS("Dinas"), DEADLINE("Tenggat"), LAINNYA("Lainnya");
    companion object { fun from(s: String?) = entries.firstOrNull { it.name.equals(s, true) || it.label.equals(s, true) } ?: LAINNYA }
}

/** Satu agenda: audit, meeting, dinas, tenggat laporan, dll. Tanggal yyyy-MM-dd, jam HH:mm (boleh kosong). */
data class JadwalModel(
    val id: String, val judul: String = "", val jenis: JadwalJenis = JadwalJenis.MEETING,
    val tanggal: String = "", val jamMulai: String = "", val jamSelesai: String = "",
    val lokasi: String = "", val peserta: String = "", val catatan: String = "", val selesai: Boolean = false,
    val createdAt: Long = 0L, val updatedAt: Long = 0L,
)

enum class DokumenJenis(val label: String, val ikon: String) {
    PASPOR("Paspor", "🛂"), VISA("Visa", "🪪"), TIKET("Tiket Pesawat/Kereta", "🎫"), VOUCHER("Voucher Hotel", "🏨"),
    SURAT_TUGAS("Surat Tugas", "📄"), ID_CARD("ID Card Perusahaan", "💳"), LAINNYA("Lainnya", "📎");
    companion object { fun from(s: String?) = entries.firstOrNull { it.name.equals(s, true) || it.label.equals(s, true) } ?: LAINNYA }
}

/** Dokumen perjalanan digital. [path] kosong bila belum ada lampiran; [tipe] "jpg" atau "pdf". berlakuSampai yyyy-MM-dd. */
data class DokumenModel(
    val id: String,
    val jenis: DokumenJenis = DokumenJenis.LAINNYA,
    val judul: String = "",
    val nomor: String = "",
    val berlakuSampai: String = "",
    val catatan: String = "",
    val path: String = "",
    val tipe: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
) {
    /** Status validitas terhadap [hariIni] (yyyy-MM-dd). */
    fun status(hariIni: String): String = when {
        berlakuSampai.isBlank() -> "Active"
        berlakuSampai < hariIni -> "Kedaluwarsa"
        berlakuSampai <= tambahHari(hariIni, 30) -> "Segera habis"
        else -> "Valid"
    }
    companion object {
        fun tambahHari(iso: String, n: Int): String = runCatching {
            java.time.LocalDate.parse(iso).plusDays(n.toLong()).toString()
        }.getOrDefault(iso)
    }
}
