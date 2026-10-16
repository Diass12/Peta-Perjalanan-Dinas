package id.tub.dinas.domain

/** Satu aset di register (berlaku lintas dinas). Foto disimpan terpisah di AsetFotoModel. */
data class AsetModel(
    val id: String,
    val order: Int = 0,
    val code: String = "",
    val name: String = "",
    val category: String = "other",
    val classif: String = "",
    val owner: String = "",
    val tenure: String = "own",          // own | lease
    val dept: String = "",
    val location: String = "",
    val user: String = "",
    val cond: String = "",
    val acq: String = "",                // yyyy-MM-dd
    val checkDate: String = "",          // yyyy-MM-dd
    val remarks: String = "",
    val nbv: Double? = null,
    val harga: Double? = null,
    val masa: Int? = null,
    val tglTercatat: String = "",
    val nbvKet: String = "",
    val catatan: String = "",
    val sumber: String = "",
    val srcStatus: String = "",
    val barcode: StatusBarcode? = null,
    val barcodeSrc: SumberBarcode = SumberBarcode.NONE,
    val pcodes: List<String> = emptyList(),
    val state: AuditState = AuditState.PENDING,
    val auditedAt: Long? = null,
    val log: List<String> = emptyList(),  // "epochMs|pesan", terbaru di depan, maksimum 30
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val temuanId: String? = null,
) {
    val tenureLabel get() = if (tenure == "lease") "Sewa" else "Milik Sendiri"
}

data class AsetFotoModel(val id: String, val asetId: String, val path: String, val order: Int, val takenAt: Long?)

class AsetBundle(val aset: AsetModel, val fotos: List<AsetFotoModel>)

fun AsetModel.addLog(msg: String, now: Long = System.currentTimeMillis()): AsetModel =
    copy(log = (listOf("$now|${msg.replace('\n', ' ')}") + log).take(30), updatedAt = now)
