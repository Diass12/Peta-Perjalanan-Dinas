package id.tub.dinas.data

import id.tub.dinas.data.local.*
import id.tub.dinas.domain.*

fun DinasEntity.toModel() = DinasModel(id, name, startMs, endMs, note)
fun TitikEntity.toModel() = TitikModel(id, title, note, if (lat != null && lng != null) GeoPoint(lat, lng, acc, src) else null, recordedAt, urutan)
fun TemuanEntity.toModel() = TemuanModel(
    id, title, code, description,
    mapOf(Lens.ASET to ketAset, Lens.OPS to ketOps, Lens.ADM to ketAdm),
    action, pic, target, Status.from(status),
    if (lat != null && lng != null) GeoPoint(lat, lng, acc, src) else null, urutan,
)
fun FotoEntity.toModel() = FotoModel(id, if (ownerType == "TEMUAN") OwnerType.TEMUAN else OwnerType.TITIK, ownerId, path, caption, urutan, width, height, takenAt)
fun LampiranEntity.toModel() = LampiranModel(id, temuanId, name, path, sizeBytes, urutan)
fun PoinEntity.toModel() = PoinModel(id, text, urutan)

fun TitikModel.toEntity(dinasId: String) = TitikEntity(id, dinasId, title, note, geo?.lat, geo?.lng, geo?.accuracyM, geo?.source ?: "", recordedAt, order)
fun TemuanModel.toEntity(dinasId: String) = TemuanEntity(
    id, dinasId, title, code, description, lens[Lens.ASET], lens[Lens.OPS], lens[Lens.ADM],
    action, pic, target, status.name, geo?.lat, geo?.lng, geo?.accuracyM, geo?.source ?: "", order,
)
fun FotoModel.toEntity(dinasId: String) = FotoEntity(id, dinasId, ownerType.name, ownerId, path, caption, order, width, height, takenAt)
fun LampiranModel.toEntity(dinasId: String) = LampiranEntity(id, dinasId, temuanId, name, path, sizeBytes, order)
fun PoinModel.toEntity(dinasId: String) = PoinEntity(id, dinasId, text, order)
fun DinasModel.toEntity(createdAt: Long) = DinasEntity(id, name, startMs, endMs, note, createdAt)

fun MediaEntity.toModel() = MediaModel(id, if (ownerType == "TEMUAN") OwnerType.TEMUAN else OwnerType.TITIK, ownerId, MediaKind.from(kind), path, name, sizeBytes, createdAt, urutan)
fun MediaModel.toEntity(dinasId: String) = MediaEntity(id, dinasId, ownerType.name, ownerId, kind.name, path, name, sizeBytes, createdAt, order)

fun AsetEntity.toModel() = AsetModel(
    id, ord, code, name, category, classif, owner, tenure, dept, location, pengguna, cond, acq, checkDate, remarks,
    nbv, harga, masa, tglTercatat, nbvKet, catatan, sumber, srcStatus,
    StatusBarcode.from(barcode), SumberBarcode.from(barcodeSrc),
    if (pcodes.isEmpty()) emptyList() else pcodes.split('|').filter { it.isNotEmpty() },
    AuditState.from(state), auditedAt, if (log.isEmpty()) emptyList() else log.split('\n').filter { it.isNotEmpty() },
    createdAt, updatedAt, temuanId,
)
fun AsetModel.toEntity() = AsetEntity(
    id, order, code, name, category, classif, owner, tenure, dept, location, user, cond, acq, checkDate, remarks,
    nbv, harga, masa, tglTercatat, nbvKet, catatan, sumber, srcStatus,
    barcode?.name ?: "", barcodeSrc.name, pcodes.joinToString("|"),
    state.name, auditedAt, log.joinToString("\n"), createdAt, updatedAt, temuanId,
)
fun AsetFotoEntity.toModel() = AsetFotoModel(id, asetId, path, urutan, takenAt)
fun AsetFotoModel.toEntity() = AsetFotoEntity(id, asetId, path, order, takenAt)

fun AsetMediaEntity.toModel() = AsetMediaModel(id, asetId, kind, path, name, sizeBytes, createdAt, urutan)
fun AsetMediaModel.toEntity() = AsetMediaEntity(id, asetId, kind, path, name, sizeBytes, createdAt, order)

fun UangMukaEntity.toModel() = UangMukaModel(id, nomor, judul, dinasId, pemohon, tglPengajuan, jumlahDiajukan, tglCair, jumlahCair, catatan, UmStatus.from(status), createdAt, updatedAt)
fun UangMukaModel.toEntity() = UangMukaEntity(id, nomor, judul, dinasId, pemohon, tglPengajuan, jumlahDiajukan, tglCair, jumlahCair, catatan, status.name, createdAt, updatedAt)
fun UmBonEntity.toModel() = BonModel(id, umId, tanggal, BonKategori.from(kategori), uraian, jumlah, urutan, createdAt)
fun BonModel.toEntity() = UmBonEntity(id, umId, tanggal, kategori.name, uraian, jumlah, order, createdAt)
fun UmFotoEntity.toModel() = BonFotoModel(id, bonId, path, urutan)
fun BonFotoModel.toEntity() = UmFotoEntity(id, bonId, path, order)
fun JadwalEntity.toModel() = JadwalModel(id, judul, JadwalJenis.from(jenis), tanggal, jamMulai, jamSelesai, lokasi, peserta, catatan, selesai, createdAt, updatedAt)
fun JadwalModel.toEntity() = JadwalEntity(id, judul, jenis.name, tanggal, jamMulai, jamSelesai, lokasi, peserta, catatan, selesai, createdAt, updatedAt)
