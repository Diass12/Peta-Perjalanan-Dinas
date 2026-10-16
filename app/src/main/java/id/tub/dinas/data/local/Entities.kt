package id.tub.dinas.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "dinas")
data class DinasEntity(
    @PrimaryKey val id: String,
    val name: String,
    val startMs: Long?,
    val endMs: Long?,
    val note: String,
    val createdAt: Long,
)

@Entity(
    tableName = "titik",
    foreignKeys = [ForeignKey(DinasEntity::class, ["id"], ["dinasId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("dinasId")],
)
data class TitikEntity(
    @PrimaryKey val id: String,
    val dinasId: String,
    val title: String,
    val note: String,
    val lat: Double?,
    val lng: Double?,
    val acc: Int?,
    val src: String,
    val recordedAt: Long,
    val urutan: Int,
)

@Entity(
    tableName = "temuan",
    foreignKeys = [ForeignKey(DinasEntity::class, ["id"], ["dinasId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("dinasId")],
)
data class TemuanEntity(
    @PrimaryKey val id: String,
    val dinasId: String,
    val title: String,
    val code: String,
    val description: String,
    val ketAset: String?,
    val ketOps: String?,
    val ketAdm: String?,
    val action: String,
    val pic: String,
    val target: String,
    val status: String,
    val lat: Double?,
    val lng: Double?,
    val acc: Int?,
    val src: String,
    val urutan: Int,
)

@Entity(
    tableName = "foto",
    foreignKeys = [ForeignKey(DinasEntity::class, ["id"], ["dinasId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("dinasId"), Index("ownerId")],
)
data class FotoEntity(
    @PrimaryKey val id: String,
    val dinasId: String,
    val ownerType: String,
    val ownerId: String,
    val path: String,
    val caption: String,
    val urutan: Int,
    val width: Int,
    val height: Int,
    val takenAt: Long?,
)

@Entity(
    tableName = "lampiran",
    foreignKeys = [ForeignKey(DinasEntity::class, ["id"], ["dinasId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("dinasId"), Index("temuanId")],
)
data class LampiranEntity(
    @PrimaryKey val id: String,
    val dinasId: String,
    val temuanId: String,
    val name: String,
    val path: String,
    val sizeBytes: Long,
    val urutan: Int,
)

@Entity(
    tableName = "poin",
    foreignKeys = [ForeignKey(DinasEntity::class, ["id"], ["dinasId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("dinasId")],
)
data class PoinEntity(
    @PrimaryKey val id: String,
    val dinasId: String,
    val text: String,
    val urutan: Int,
)

@Entity(
    tableName = "media",
    foreignKeys = [ForeignKey(DinasEntity::class, ["id"], ["dinasId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("dinasId"), Index("ownerId")],
)
data class MediaEntity(
    @PrimaryKey val id: String,
    val dinasId: String,
    val ownerType: String,
    val ownerId: String,
    val kind: String,
    val path: String,
    val name: String,
    val sizeBytes: Long,
    val createdAt: Long,
    val urutan: Int,
)

@Entity(tableName = "aset", indices = [Index("code")])
data class AsetEntity(
    @PrimaryKey val id: String,
    val ord: Int,
    val code: String,
    val name: String,
    val category: String,
    val classif: String,
    val owner: String,
    val tenure: String,
    val dept: String,
    val location: String,
    val pengguna: String,
    val cond: String,
    val acq: String,
    val checkDate: String,
    val remarks: String,
    val nbv: Double?,
    val harga: Double?,
    val masa: Int?,
    val tglTercatat: String,
    val nbvKet: String,
    val catatan: String,
    val sumber: String,
    val srcStatus: String,
    val barcode: String,
    val barcodeSrc: String,
    val pcodes: String,
    val state: String,
    val auditedAt: Long?,
    val log: String,
    val createdAt: Long,
    val updatedAt: Long,
    val temuanId: String?,
)

@Entity(
    tableName = "aset_foto",
    foreignKeys = [ForeignKey(AsetEntity::class, ["id"], ["asetId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("asetId")],
)
data class AsetFotoEntity(
    @PrimaryKey val id: String,
    val asetId: String,
    val path: String,
    val urutan: Int,
    val takenAt: Long?,
)

@Entity(
    tableName = "aset_media",
    foreignKeys = [ForeignKey(AsetEntity::class, ["id"], ["asetId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("asetId")],
)
data class AsetMediaEntity(
    @PrimaryKey val id: String,
    val asetId: String,
    val kind: String,          // VIDEO | DOC
    val path: String,
    val name: String,
    val sizeBytes: Long,
    val createdAt: Long,
    val urutan: Int,
)

@Entity(tableName = "uang_muka")
data class UangMukaEntity(
    @PrimaryKey val id: String,
    val nomor: String,
    val judul: String,
    val dinasId: String?,
    val pemohon: String,
    val tglPengajuan: String,
    val jumlahDiajukan: Double,
    val tglCair: String,
    val jumlahCair: Double,
    val catatan: String,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "um_bon",
    foreignKeys = [ForeignKey(UangMukaEntity::class, ["id"], ["umId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("umId")],
)
data class UmBonEntity(
    @PrimaryKey val id: String,
    val umId: String,
    val tanggal: String,
    val kategori: String,
    val uraian: String,
    val jumlah: Double,
    val urutan: Int,
    val createdAt: Long,
)

@Entity(
    tableName = "um_foto",
    foreignKeys = [ForeignKey(UmBonEntity::class, ["id"], ["bonId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("bonId")],
)
data class UmFotoEntity(
    @PrimaryKey val id: String,
    val bonId: String,
    val path: String,
    val urutan: Int,
)

@Entity(tableName = "jadwal")
data class JadwalEntity(
    @PrimaryKey val id: String,
    val judul: String,
    val jenis: String,
    val tanggal: String,
    val jamMulai: String,
    val jamSelesai: String,
    val lokasi: String,
    val peserta: String,
    val catatan: String,
    val selesai: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)
