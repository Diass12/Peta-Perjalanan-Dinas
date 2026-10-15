package id.tub.dinas.domain

/** Tiga lensa laporan. */
enum class Lens(val label: String, val key: String) {
    ASET("Aset", "aset"),
    OPS("Teknis Operasional", "ops"),
    ADM("Administrasi", "adm");
}

enum class Status(val label: String, val tag: String) {
    BELUM("Belum", "BELUM"), PROSES("Proses", "PENDING"), SELESAI("Selesai", "VERIFIED"), SELISIH("Selisih", "DISCREPANCY");

    companion object {
        fun from(s: String?): Status = values().firstOrNull { it.name == s } ?: BELUM
    }
}

enum class OwnerType { TITIK, TEMUAN }

data class GeoPoint(
    val lat: Double,
    val lng: Double,
    val accuracyM: Int? = null,
    val source: String = "",
)

/** Foto yang sudah tersimpan (file terenkripsi); width/height dipakai untuk menjaga rasio di Excel. */
data class FotoModel(
    val id: String,
    val ownerType: OwnerType,
    val ownerId: String,
    val path: String,
    val caption: String,
    val order: Int,
    val width: Int,
    val height: Int,
    val takenAt: Long? = null,
)

data class LampiranModel(
    val id: String,
    val temuanId: String,
    val name: String,
    val path: String,
    val sizeBytes: Long,
    val order: Int,
)

data class TitikModel(
    val id: String,
    val title: String,
    val note: String,
    val geo: GeoPoint?,
    val recordedAt: Long,
    val order: Int,
)

/**
 * Aturan lensa: null = lensa TIDAK dipilih, "" = dipilih tetapi keterangan belum diisi.
 */
data class TemuanModel(
    val id: String,
    val title: String,
    val code: String,
    val description: String,
    val lens: Map<Lens, String?>,
    val action: String,
    val pic: String,
    val target: String,
    val status: Status,
    val geo: GeoPoint?,
    val order: Int,
) {
    fun isActive(l: Lens): Boolean = lens[l] != null
    val activeLenses: List<Lens> get() = Lens.values().filter { isActive(it) }
}

data class PoinModel(val id: String, val text: String, val order: Int)

data class DinasModel(
    val id: String,
    val name: String,
    val startMs: Long?,
    val endMs: Long?,
    val note: String,
)

/** Semua isi satu dinas, dipakai oleh export dan backup (tanpa ketergantungan Android). */
enum class MediaKind(val label: String) {
    VIDEO("Video"), AUDIO("Rekaman suara");
    companion object { fun from(s: String?) = values().firstOrNull { it.name == s } ?: AUDIO }
}

/** Video atau rekaman suara yang melekat pada titik/temuan (berkas terenkripsi). */
data class MediaModel(
    val id: String,
    val ownerType: OwnerType,
    val ownerId: String,
    val kind: MediaKind,
    val path: String,
    val name: String,
    val sizeBytes: Long,
    val createdAt: Long,
    val order: Int,
)

data class DinasSnapshot(
    val dinas: DinasModel,
    val titik: List<TitikModel>,
    val temuan: List<TemuanModel>,
    val foto: List<FotoModel>,
    val lampiran: List<LampiranModel>,
    val poin: List<PoinModel>,
    val media: List<MediaModel> = emptyList(),
) {
    fun mediaOf(type: OwnerType, ownerId: String): List<MediaModel> =
        media.filter { it.ownerType == type && it.ownerId == ownerId }.sortedBy { it.order }

    fun fotoOf(type: OwnerType, ownerId: String): List<FotoModel> =
        foto.filter { it.ownerType == type && it.ownerId == ownerId }.sortedBy { it.order }

    fun lampiranOf(temuanId: String): List<LampiranModel> =
        lampiran.filter { it.temuanId == temuanId }.sortedBy { it.order }
}
