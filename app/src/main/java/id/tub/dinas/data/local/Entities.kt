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
