package id.tub.dinas.domain

enum class UmStatus(val label: String) {
    DIAJUKAN("Diajukan"), CAIR("Sudah cair"), SELESAI("Selesai");
    companion object { fun from(s: String?) = entries.firstOrNull { it.name == s } ?: DIAJUKAN }
}

/** Tag bon. */
enum class BonKategori(val label: String) {
    TRANSPORTASI("Transportasi"), HOTEL("Hotel"), MAKAN_PAGI("Makan Pagi"), MAKAN_SIANG("Makan Siang"),
    MAKAN_MALAM("Makan Malam"), LAUNDRY("Laundry"), LAINNYA("Lainnya");
    companion object {
        fun from(s: String?): BonKategori {
            val t = s?.trim().orEmpty()
            entries.firstOrNull { it.name.equals(t, true) || it.label.equals(t, true) }?.let { return it }
            val l = t.lowercase()
            return when {
                l.contains("transport") || l.contains("taksi") || l.contains("tiket") || l.contains("bensin") || l.contains("ojek") -> TRANSPORTASI
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
)

data class BonModel(
    val id: String, val umId: String, val tanggal: String = "", val kategori: BonKategori = BonKategori.LAINNYA,
    val uraian: String = "", val jumlah: Double = 0.0, val order: Int = 0, val createdAt: Long = 0L,
)

data class BonFotoModel(val id: String, val bonId: String, val path: String, val order: Int)

class BonBundle(val bon: BonModel, val fotos: List<BonFotoModel>)

class UmBundle(val um: UangMukaModel, val bons: List<BonBundle>) {
    val totalBon: Double get() = bons.sumOf { it.bon.jumlah }
    /** Dana yang dipegang: jumlah cair bila sudah ada, kalau belum jumlah yang diajukan. */
    val dana: Double get() = if (um.jumlahCair > 0) um.jumlahCair else um.jumlahDiajukan
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
