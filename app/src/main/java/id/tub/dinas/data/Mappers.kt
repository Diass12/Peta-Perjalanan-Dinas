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
