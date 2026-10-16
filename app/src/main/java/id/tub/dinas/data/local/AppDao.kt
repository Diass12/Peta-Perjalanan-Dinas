package id.tub.dinas.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    // ---- dinas
    @Query("SELECT * FROM dinas ORDER BY createdAt DESC")
    fun observeDinas(): Flow<List<DinasEntity>>
    @Query("SELECT * FROM dinas ORDER BY createdAt DESC")
    suspend fun observeDinasOnce(): List<DinasEntity>
    @Query("SELECT * FROM dinas WHERE id = :id")
    suspend fun dinasById(id: String): DinasEntity?
    @Upsert suspend fun upsertDinas(e: DinasEntity)
    @Query("DELETE FROM dinas WHERE id = :id") suspend fun deleteDinas(id: String)
    @Query("SELECT COUNT(*) FROM titik") fun countAllTitik(): Flow<Int>
    @Query("SELECT COUNT(*) FROM temuan") fun countAllTemuan(): Flow<Int>
    @Query("SELECT COUNT(*) FROM temuan WHERE status = 'SELESAI'") fun countAllSelesai(): Flow<Int>
    @Query("SELECT COUNT(*) FROM titik WHERE dinasId = :id") fun countTitik(id: String): Flow<Int>
    @Query("SELECT COUNT(*) FROM temuan WHERE dinasId = :id") fun countTemuan(id: String): Flow<Int>

    // ---- titik
    @Query("SELECT * FROM titik WHERE dinasId = :id ORDER BY urutan, recordedAt")
    fun observeTitik(id: String): Flow<List<TitikEntity>>
    @Query("SELECT * FROM titik WHERE dinasId = :id ORDER BY urutan, recordedAt")
    suspend fun titikList(id: String): List<TitikEntity>
    @Query("SELECT * FROM titik WHERE id = :id") suspend fun titikById(id: String): TitikEntity?
    @Upsert suspend fun upsertTitik(e: TitikEntity)
    @Upsert suspend fun upsertTitikAll(e: List<TitikEntity>)
    @Query("DELETE FROM titik WHERE id = :id") suspend fun deleteTitik(id: String)

    // ---- temuan
    @Query("SELECT * FROM temuan WHERE dinasId = :id ORDER BY urutan")
    fun observeTemuan(id: String): Flow<List<TemuanEntity>>
    @Query("SELECT * FROM temuan WHERE dinasId = :id ORDER BY urutan")
    suspend fun temuanList(id: String): List<TemuanEntity>
    @Query("SELECT * FROM temuan WHERE id = :id") suspend fun temuanById(id: String): TemuanEntity?
    @Upsert suspend fun upsertTemuan(e: TemuanEntity)
    @Upsert suspend fun upsertTemuanAll(e: List<TemuanEntity>)
    @Query("DELETE FROM temuan WHERE id = :id") suspend fun deleteTemuan(id: String)

    // ---- media (video / rekaman suara)
    @Query("SELECT * FROM media WHERE dinasId = :id ORDER BY urutan") fun observeMedia(id: String): Flow<List<MediaEntity>>
    @Query("SELECT * FROM media WHERE dinasId = :id ORDER BY urutan") suspend fun mediaList(id: String): List<MediaEntity>
    @Query("SELECT * FROM media WHERE ownerId = :ownerId") suspend fun mediaOfOwner(ownerId: String): List<MediaEntity>
    @Query("SELECT * FROM media WHERE id = :id") suspend fun mediaById(id: String): MediaEntity?
    @Upsert suspend fun upsertMedia(e: MediaEntity)
    @Upsert suspend fun upsertMediaAll(e: List<MediaEntity>)
    @Query("DELETE FROM media WHERE id = :id") suspend fun deleteMedia(id: String)
    @Query("DELETE FROM media WHERE ownerId = :ownerId") suspend fun deleteMediaOfOwner(ownerId: String)
    @Query("UPDATE media SET ownerType = :type, ownerId = :ownerId WHERE ownerId = :oldOwner")
    suspend fun reassignMedia(oldOwner: String, ownerId: String, type: String)
    @Query("SELECT path FROM media") suspend fun allMediaPaths(): List<String>

    // ---- foto
    @Query("SELECT * FROM foto WHERE dinasId = :id ORDER BY urutan")
    fun observeFoto(id: String): Flow<List<FotoEntity>>
    @Query("SELECT * FROM foto WHERE dinasId = :id ORDER BY urutan")
    suspend fun fotoList(id: String): List<FotoEntity>
    @Query("SELECT * FROM foto WHERE ownerId = :ownerId") suspend fun fotoOfOwner(ownerId: String): List<FotoEntity>
    @Query("SELECT * FROM foto WHERE id = :id") suspend fun fotoById(id: String): FotoEntity?
    @Upsert suspend fun upsertFoto(e: FotoEntity)
    @Upsert suspend fun upsertFotoAll(e: List<FotoEntity>)
    @Query("DELETE FROM foto WHERE id = :id") suspend fun deleteFoto(id: String)
    @Query("DELETE FROM foto WHERE ownerId = :ownerId") suspend fun deleteFotoOfOwner(ownerId: String)
    @Query("UPDATE foto SET ownerType = :type, ownerId = :ownerId WHERE ownerId = :oldOwner")
    suspend fun reassignFoto(oldOwner: String, ownerId: String, type: String)

    // ---- lampiran
    @Query("SELECT * FROM lampiran WHERE dinasId = :id ORDER BY urutan")
    fun observeLampiran(id: String): Flow<List<LampiranEntity>>
    @Query("SELECT * FROM lampiran WHERE dinasId = :id ORDER BY urutan")
    suspend fun lampiranList(id: String): List<LampiranEntity>
    @Query("SELECT * FROM lampiran WHERE temuanId = :tid") suspend fun lampiranOfTemuan(tid: String): List<LampiranEntity>
    @Query("SELECT * FROM lampiran WHERE id = :id") suspend fun lampiranById(id: String): LampiranEntity?
    @Upsert suspend fun upsertLampiran(e: LampiranEntity)
    @Upsert suspend fun upsertLampiranAll(e: List<LampiranEntity>)
    @Query("DELETE FROM lampiran WHERE id = :id") suspend fun deleteLampiran(id: String)

    // ---- poin
    @Query("SELECT * FROM poin WHERE dinasId = :id ORDER BY urutan")
    fun observePoin(id: String): Flow<List<PoinEntity>>
    @Query("SELECT * FROM poin WHERE dinasId = :id ORDER BY urutan")
    suspend fun poinList(id: String): List<PoinEntity>
    @Upsert suspend fun upsertPoin(e: PoinEntity)
    @Upsert suspend fun upsertPoinAll(e: List<PoinEntity>)
    @Query("DELETE FROM poin WHERE id = :id") suspend fun deletePoin(id: String)

    // ---- aset (register)
    @Query("SELECT * FROM aset ORDER BY ord, name") fun observeAset(): Flow<List<AsetEntity>>
    @Query("SELECT * FROM aset ORDER BY ord, name") suspend fun asetList(): List<AsetEntity>
    @Query("SELECT * FROM aset WHERE id = :id") suspend fun asetById(id: String): AsetEntity?
    @Upsert suspend fun upsertAset(e: AsetEntity)
    @Upsert suspend fun upsertAsetAll(e: List<AsetEntity>)
    @Query("DELETE FROM aset WHERE id = :id") suspend fun deleteAset(id: String)
    @Query("SELECT COALESCE(MAX(ord), 0) FROM aset") suspend fun maxAsetOrd(): Int
    @Query("UPDATE aset SET state = 'PENDING', auditedAt = NULL") suspend fun resetAudit()
    @Query("SELECT * FROM aset_foto ORDER BY asetId, urutan") fun observeAsetFoto(): Flow<List<AsetFotoEntity>>
    @Query("SELECT * FROM aset_foto ORDER BY asetId, urutan") suspend fun asetFotoAll(): List<AsetFotoEntity>
    @Query("SELECT * FROM aset_foto WHERE asetId = :asetId ORDER BY urutan") suspend fun asetFotoOf(asetId: String): List<AsetFotoEntity>
    @Query("SELECT * FROM aset_foto WHERE id = :id") suspend fun asetFotoById(id: String): AsetFotoEntity?
    @Upsert suspend fun upsertAsetFoto(e: AsetFotoEntity)
    @Upsert suspend fun upsertAsetFotoAll(e: List<AsetFotoEntity>)
    @Query("DELETE FROM aset_foto WHERE id = :id") suspend fun deleteAsetFoto(id: String)
    @Query("SELECT path FROM aset_foto") suspend fun allAsetFotoPaths(): List<String>

    // ---- pembersihan berkas yatim
    @Query("SELECT path FROM foto") suspend fun allFotoPaths(): List<String>
    @Query("SELECT path FROM lampiran") suspend fun allLampiranPaths(): List<String>
}
