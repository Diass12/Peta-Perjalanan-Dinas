package id.tub.dinas.aset

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import id.tub.dinas.data.Repository
import id.tub.dinas.domain.*
import id.tub.dinas.export.Imaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/** Satu foto dinas (Temuan/Titik) dan rencana tindakannya: baru | tempel | lewati. */
class RencanaFoto(
    val foto: FotoModel, val jpeg: ByteArray, val kode: String, val lokasi: String,
    val cocok: AsetModel?, val catatan: String, aksi: String, nama: String,
) {
    var aksi by mutableStateOf(aksi)
    var nama by mutableStateOf(nama)
    /** "Tempel" tersedia bila ada aset yang cocok, atau foto lain dalam daftar ini dengan kode yang sama. */
    var bisaTempel = cocok != null
}

/** Memindahkan foto hasil dinas menjadi data Aset: baca kode, cocokkan dengan aset yang ada, cegah dobel. */
object FotoKeAset {
    private fun hash(b: ByteArray) = Imaging.dHash(b)
    private fun mirip(a: Long, b: Long) = Imaging.hamming(a, b) <= 8

    suspend fun analisis(repo: Repository, fotos: List<FotoModel>, sudah: Set<String>, lokasiDari: (FotoModel) -> String, namaDari: (FotoModel) -> String): List<RencanaFoto> = withContext(Dispatchers.IO) {
        val register = repo.asetList()
        val tanpaKode = register.filter { it.code.isBlank() }
        // Hash foto semua aset tanpa kode dihitung sekali.
        val hashTanpaKode = ArrayList<Pair<AsetModel, Long>>()
        for (a in tanpaKode) for (f in repo.asetFotoOf(a.id)) { val b = repo.asetFotoBytes(f.path) ?: continue; hash(b)?.let { hashTanpaKode.add(a to it) } }
        val hasilHash = ArrayList<Long>()          // foto dalam daftar ini yang sudah direncanakan "baru"
        val kodeDalamDaftar = HashSet<String>()
        val out = ArrayList<RencanaFoto>()
        for (f in fotos) {
            val b = repo.asetFotoBytes(f.path) ?: continue
            val h = hash(b)
            val nama = namaDari(f)
            val lok = lokasiDari(f)
            if (f.id in sudah) { out.add(RencanaFoto(f, b, "", lok, null, "Sudah pernah dijadikan aset", "lewati", nama)); continue }
            val kode = runCatching { CodeScanner.bacaJpeg(b).kode.firstOrNull() }.getOrNull().orEmpty()
            val norm = KodeAset.norm(kode)
            if (norm.isNotEmpty()) {
                val ada = register.firstOrNull { KodeAset.norm(it.code) == norm }
                if (ada != null) {
                    val kembar = h != null && repo.asetFotoOf(ada.id).any { af -> repo.asetFotoBytes(af.path)?.let { hash(it) }?.let { x -> mirip(h, x) } == true }
                    if (kembar) out.add(RencanaFoto(f, b, kode, lok, ada, "Foto yang sama sudah ada di ${ada.name.ifBlank { ada.code }}", "lewati", nama))
                    else out.add(RencanaFoto(f, b, kode, lok, ada, "Kode sudah ada: ${ada.name.ifBlank { ada.code }}, foto ditempel", "tempel", nama))
                } else if (!kodeDalamDaftar.add(norm)) {
                    out.add(RencanaFoto(f, b, kode, lok, null, "Kode sama dengan foto lain di daftar ini, ditempel ke aset itu", "tempel", nama).also { it.bisaTempel = true })
                } else {
                    h?.let { hasilHash.add(it) }
                    out.add(RencanaFoto(f, b, kode, lok, null, "Kode baru, aset dibuat", "baru", nama))
                }
                continue
            }
            // Tanpa kode: cari foto mirip pada aset tanpa kode yang sudah ada, dan pada foto lain di daftar ini.
            val ada = h?.let { hh -> hashTanpaKode.firstOrNull { mirip(hh, it.second) }?.first }
            if (ada != null) { out.add(RencanaFoto(f, b, "", lok, ada, "Mirip dengan aset ${ada.name.ifBlank { "tanpa nama" }}", "lewati", nama)); continue }
            if (h != null && hasilHash.any { mirip(h, it) }) { out.add(RencanaFoto(f, b, "", lok, null, "Mirip dengan foto lain di daftar ini", "lewati", nama)); continue }
            h?.let { hasilHash.add(it) }
            out.add(RencanaFoto(f, b, "", lok, null, "Tidak ada kode terbaca, jadi aset tanpa kode", "baru", nama))
        }
        out
    }

    /** Menerapkan rencana. Mengembalikan (baru, ditempel, dilewati). */
    suspend fun terapkan(repo: Repository, rencana: List<RencanaFoto>, owner: String, lokasiGlobal: String, sudah: MutableSet<String>): Triple<Int, Int, Int> = withContext(Dispatchers.IO) {
        var baru = 0; var tempel = 0; var lewat = 0
        val dibuat = HashMap<String, String>()                           // kode ternormalisasi -> id aset yang dibuat di sini
        val namaTerpakai = HashSet<String>()
        repo.asetList().filter { it.code.isBlank() }.forEach { namaTerpakai.add((it.name.trim() + "|" + it.location.trim()).lowercase()) }
        val now = System.currentTimeMillis()
        for (r in rencana) {
            val lok = lokasiGlobal.ifBlank { r.lokasi }
            when (r.aksi) {
                "tempel" -> {
                    val target = r.cocok?.id ?: dibuat[KodeAset.norm(r.kode)]
                    if (target != null) { repo.addAsetFoto(target, r.jpeg, r.foto.takenAt ?: now); tempel++; sudah.add(r.foto.id) }
                    else { buat(repo, r, owner, lok, now, namaTerpakai)?.let { id -> dibuat[KodeAset.norm(r.kode)] = id }; baru++; sudah.add(r.foto.id) }
                }
                "baru" -> {
                    val id = buat(repo, r, owner, lok, now, namaTerpakai)
                    if (id != null && r.kode.isNotBlank()) dibuat[KodeAset.norm(r.kode)] = id
                    baru++; sudah.add(r.foto.id)
                }
                else -> lewat++
            }
        }
        Triple(baru, tempel, lewat)
    }

    private suspend fun buat(repo: Repository, r: RencanaFoto, owner: String, lokasi: String, now: Long, terpakai: MutableSet<String>): String? {
        val kode = KodeAset.norm(r.kode)
        var nama = r.nama.trim()
        if (kode.isEmpty()) {
            if (nama.isEmpty()) nama = "(nama belum diisi)"
            // Impor JSON menolak nama + departemen kembar untuk aset tanpa kode, jadi nama diberi nomor bila sudah dipakai.
            var n = nama; var i = 2
            while (!terpakai.add((n + "|" + lokasi.trim()).lowercase())) { n = "$nama $i"; i++ }
            nama = n
        }
        val kelas = KodeAset.kelasDariKode(kode)
        var a = AsetModel(
            id = UUID.randomUUID().toString(), code = kode, name = nama, owner = owner, location = lokasi.trim(),
            category = kelas?.kategori ?: KodeAset.tebakKategori(kode, nama), classif = kelas?.nama.orEmpty(),
            sumber = "dinas", barcode = StatusBarcode.NONASSET, barcodeSrc = SumberBarcode.AUTO, createdAt = now,
        )
        val (st, src) = KodeAset.hitungStatusBarcode(a.code, a.pcodes, a.pcodes.isNotEmpty(), a.barcode, a.barcodeSrc)
        a = a.copy(barcode = st, barcodeSrc = src).addLog("dibuat dari foto dinas")
        val id = repo.saveAset(a)
        repo.addAsetFoto(id, r.jpeg, r.foto.takenAt ?: now)
        return id
    }
}
