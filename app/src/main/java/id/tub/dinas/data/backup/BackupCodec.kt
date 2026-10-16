package id.tub.dinas.data.backup

import id.tub.dinas.domain.*
import id.tub.dinas.security.ChunkedCrypto
import id.tub.dinas.security.MediaVault
import java.io.*
import java.security.SecureRandom
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Cadangan: ZIP berisi data.json + media/<path>. Berkas media di dalam ZIP berupa teks-jernih
 * (didekripsi dari vault) supaya bisa dibuka di perangkat lain; karena itu cadangan SEBAIKNYA
 * dilindungi kata sandi. Dengan kata sandi seluruh ZIP dienkripsi (PBKDF2 + AES-GCM berpotongan).
 *
 * Format luar: "DNSBAK01" | flag(1: 0 polos, 1 terenkripsi) | [salt(16)] | isi
 */
object BackupCodec {
    private val MAGIC = "DNSBAK01".toByteArray(Charsets.US_ASCII)
    private const val MAX_ENTRY = 120L * 1024 * 1024
    private const val MAX_TOTAL = 4000L * 1024 * 1024
    private const val MAX_MEDIA_ENTRY = 600L * 1024 * 1024
    private const val MAX_ENTRIES = 20000
    private val rnd = SecureRandom()

    data class Restored(val snapshots: List<DinasSnapshot>, val mediaCount: Int, val assets: List<AsetBundle> = emptyList())

    fun write(snaps: List<DinasSnapshot>, vault: MediaVault, out: OutputStream, passphrase: CharArray?, assets: List<AsetBundle> = emptyList()) {
        out.write(MAGIC)
        if (passphrase == null || passphrase.isEmpty()) {
            out.write(0)
            zip(snaps, vault, out, assets)
        } else {
            out.write(1)
            val salt = ByteArray(16).also { rnd.nextBytes(it) }
            out.write(salt)
            val key = ChunkedCrypto.deriveKey(passphrase, salt)
            val tmp = File.createTempFile("bak_", ".zip")
            try {
                tmp.outputStream().use { zip(snaps, vault, it, assets) }
                tmp.inputStream().use { ChunkedCrypto.encrypt(key, it, out) }
            } finally { tmp.delete(); key.fill(0) }
        }
        out.flush()
    }

    private fun zip(snaps: List<DinasSnapshot>, vault: MediaVault, out: OutputStream, assets: List<AsetBundle>) {
        val zos = ZipOutputStream(BufferedOutputStream(out))
        zos.putNextEntry(ZipEntry("data.json"))
        zos.write(MiniJson.write(toJson(snaps, assets)).toByteArray(Charsets.UTF_8))
        zos.closeEntry()
        zos.setLevel(java.util.zip.Deflater.BEST_SPEED)
        val seen = HashSet<String>()
        for (s in snaps) {
            val paths = s.foto.map { it.path } + s.lampiran.map { it.path }
            for (p in paths) {
                if (!seen.add(p) || !vault.exists(p)) continue
                zos.putNextEntry(ZipEntry("media/$p"))
                zos.write(vault.read(p))
                zos.closeEntry()
            }
            for (mm in s.media) {
                if (!seen.add(mm.path) || !vault.exists(mm.path)) continue
                zos.putNextEntry(ZipEntry("media/${mm.path}"))
                vault.readTo(mm.path, zos)
                zos.closeEntry()
            }
        }
        for (b in assets) for (f in b.fotos) {
            if (!seen.add(f.path) || !vault.exists(f.path)) continue
            zos.putNextEntry(ZipEntry("media/${f.path}"))
            zos.write(vault.read(f.path))
            zos.closeEntry()
        }
        zos.finish()
        zos.flush()
    }

    fun isEncrypted(head: ByteArray): Boolean = head.size > 8 && head[8].toInt() == 1

    /** Melempar IOException/IllegalArgumentException bila berkas salah, rusak, atau kata sandi keliru. */
    fun read(input: InputStream, vault: MediaVault, passphrase: CharArray?): Restored {
        val head = ByteArray(9)
        if (readN(input, head) != 9) throw IOException("Berkas cadangan tidak lengkap")
        for (i in 0 until 8) if (head[i] != MAGIC[i]) throw IOException("Bukan berkas cadangan aplikasi ini")
        return if (head[8].toInt() == 1) {
            if (passphrase == null || passphrase.isEmpty()) throw IOException("Cadangan ini butuh kata sandi")
            val salt = ByteArray(16)
            if (readN(input, salt) != 16) throw IOException("Berkas cadangan tidak lengkap")
            val key = ChunkedCrypto.deriveKey(passphrase, salt)
            val tmp = File.createTempFile("res_", ".zip")
            try {
                tmp.outputStream().use { ChunkedCrypto.decrypt(key, input, it) }
                tmp.inputStream().use { unzip(it, vault) }
            } finally { tmp.delete(); key.fill(0) }
        } else unzip(input, vault)
    }

    private fun readN(i: InputStream, b: ByteArray): Int { var n = 0; while (n < b.size) { val r = i.read(b, n, b.size - n); if (r < 0) break; n += r }; return n }

    private fun unzip(input: InputStream, vault: MediaVault): Restored {
        val zin = ZipInputStream(BufferedInputStream(input))
        var json: String? = null
        val written = ArrayList<String>()
        var total = 0L; var count = 0; var media = 0
        try {
            while (true) {
                val e = zin.nextEntry ?: break
                if (++count > MAX_ENTRIES) throw IOException("Terlalu banyak entri")
                val name = e.name
                if (name.contains("..") || name.startsWith("/") || name.contains("\\")) throw IOException("Nama entri tidak aman")
                if (e.isDirectory) continue
                if (name.startsWith("media/media/")) {
                    val rel = name.removePrefix("media/")
                    val noClose = object : java.io.FilterInputStream(zin) { override fun close() {} }
                    val sz = vault.saveStreamAt(noClose, rel, MAX_MEDIA_ENTRY)
                    written.add(rel); media++; total += sz
                    if (total > MAX_TOTAL) throw IOException("Cadangan terlalu besar")
                    continue
                }
                val data = readLimited(zin)
                total += data.size
                if (total > MAX_TOTAL) throw IOException("Cadangan terlalu besar")
                if (name == "data.json") json = String(data, Charsets.UTF_8)
                else if (name.startsWith("media/")) {
                    val rel = name.removePrefix("media/")
                    if (rel.isEmpty()) continue
                    written.add(vault.saveAt(data, rel)); media++
                }
            }
            val j = json ?: throw IOException("data.json tidak ada di cadangan")
            val root = MiniJson.parse(j)
            return Restored(fromJson(root), media, assetsFromJson(root))
        } catch (e: Exception) {
            written.forEach { vault.delete(it) }
            if (e is IOException) throw e
            throw IOException("Cadangan rusak: ${e.message}", e)
        }
    }

    private fun readLimited(z: ZipInputStream): ByteArray {
        val o = ByteArrayOutputStream(); val b = ByteArray(16 * 1024); var t = 0L
        while (true) { val r = z.read(b); if (r < 0) break; t += r; if (t > MAX_ENTRY) throw IOException("Entri terlalu besar"); o.write(b, 0, r) }
        return o.toByteArray()
    }

    // ---------- JSON ----------
    private fun geo(g: GeoPoint?): Any? = g?.let { mapOf("lat" to it.lat, "lng" to it.lng, "acc" to it.accuracyM, "src" to it.source) }

    fun toJson(snaps: List<DinasSnapshot>, assets: List<AsetBundle> = emptyList()): Map<String, Any?> = mapOf(
        "aset" to assets.map { asetJson(it) },
        "format" to "dinas-backup", "version" to 1,
        "dinas" to snaps.map { s ->
            mapOf(
                "id" to s.dinas.id, "name" to s.dinas.name, "start" to s.dinas.startMs, "end" to s.dinas.endMs, "note" to s.dinas.note,
                "titik" to s.titik.map { mapOf("id" to it.id, "title" to it.title, "note" to it.note, "geo" to geo(it.geo), "at" to it.recordedAt, "order" to it.order) },
                "temuan" to s.temuan.map { t ->
                    mapOf("id" to t.id, "title" to t.title, "code" to t.code, "desc" to t.description,
                        "aset" to t.lens[Lens.ASET], "ops" to t.lens[Lens.OPS], "adm" to t.lens[Lens.ADM],
                        "action" to t.action, "pic" to t.pic, "target" to t.target, "status" to t.status.name,
                        "geo" to geo(t.geo), "order" to t.order)
                },
                "foto" to s.foto.map { mapOf("id" to it.id, "type" to it.ownerType.name, "owner" to it.ownerId, "path" to it.path, "caption" to it.caption, "order" to it.order, "w" to it.width, "h" to it.height, "taken" to it.takenAt) },
                "lampiran" to s.lampiran.map { mapOf("id" to it.id, "temuan" to it.temuanId, "name" to it.name, "path" to it.path, "size" to it.sizeBytes, "order" to it.order) },
                "poin" to s.poin.map { mapOf("id" to it.id, "text" to it.text, "order" to it.order) },
                "media" to s.media.map { mapOf("id" to it.id, "type" to it.ownerType.name, "owner" to it.ownerId, "kind" to it.kind.name, "path" to it.path, "name" to it.name, "size" to it.sizeBytes, "at" to it.createdAt, "order" to it.order) },
            )
        },
    )

    @Suppress("UNCHECKED_CAST")
    private fun m(x: Any?): Map<String, Any?> = x as? Map<String, Any?> ?: throw IllegalArgumentException("Objek diharapkan")
    private fun l(x: Any?): List<Any?> = x as? List<Any?> ?: emptyList()
    private fun s(x: Any?): String = (x as? String) ?: ""
    private fun sn(x: Any?): String? = x as? String
    private fun n(x: Any?): Long? = (x as? Number)?.toLong()
    private fun i(x: Any?): Int = (x as? Number)?.toInt() ?: 0
    private fun g(x: Any?): GeoPoint? {
        val o = x as? Map<*, *> ?: return null
        val la = (o["lat"] as? Number)?.toDouble() ?: return null
        val ln = (o["lng"] as? Number)?.toDouble() ?: return null
        if (la !in -90.0..90.0 || ln !in -180.0..180.0) return null
        return GeoPoint(la, ln, (o["acc"] as? Number)?.toInt(), (o["src"] as? String) ?: "")
    }
    private fun safePath(p: String): String {
        if (p.isEmpty() || p.contains("..") || p.startsWith("/") || p.contains("\\")) throw IllegalArgumentException("Path media tidak aman")
        return p
    }

    fun fromJson(root: Any?): List<DinasSnapshot> {
        val r = m(root)
        if (r["format"] != "dinas-backup") throw IllegalArgumentException("Format cadangan tidak dikenal")
        val ver = i(r["version"])
        if (ver < 1 || ver > 1) throw IllegalArgumentException("Versi cadangan $ver belum didukung")
        return l(r["dinas"]).map { dx ->
            val d = m(dx)
            val dinas = DinasModel(s(d["id"]).ifEmpty { throw IllegalArgumentException("id dinas kosong") }, s(d["name"]), n(d["start"]), n(d["end"]), s(d["note"]))
            DinasSnapshot(
                dinas,
                l(d["titik"]).map { val o = m(it); TitikModel(s(o["id"]), s(o["title"]), s(o["note"]), g(o["geo"]), n(o["at"]) ?: 0L, i(o["order"])) },
                l(d["temuan"]).map { val o = m(it)
                    TemuanModel(s(o["id"]), s(o["title"]), s(o["code"]), s(o["desc"]),
                        mapOf(Lens.ASET to sn(o["aset"]), Lens.OPS to sn(o["ops"]), Lens.ADM to sn(o["adm"])),
                        s(o["action"]), s(o["pic"]), s(o["target"]), Status.from(sn(o["status"])), g(o["geo"]), i(o["order"])) },
                l(d["foto"]).map { val o = m(it)
                    FotoModel(s(o["id"]), if (o["type"] == "TEMUAN") OwnerType.TEMUAN else OwnerType.TITIK, s(o["owner"]), safePath(s(o["path"])), s(o["caption"]), i(o["order"]), i(o["w"]), i(o["h"]), n(o["taken"])) },
                l(d["lampiran"]).map { val o = m(it); LampiranModel(s(o["id"]), s(o["temuan"]), s(o["name"]), safePath(s(o["path"])), n(o["size"]) ?: 0L, i(o["order"])) },
                l(d["poin"]).map { val o = m(it); PoinModel(s(o["id"]), s(o["text"]), i(o["order"])) },
                l(d["media"]).map { val o = m(it)
                    MediaModel(s(o["id"]), if (o["type"] == "TEMUAN") OwnerType.TEMUAN else OwnerType.TITIK, s(o["owner"]), MediaKind.from(sn(o["kind"])), safePath(s(o["path"])), s(o["name"]), n(o["size"]) ?: 0L, n(o["at"]) ?: 0L, i(o["order"])) },
            )
        }
    }

    private fun asetJson(b: AsetBundle): Map<String, Any?> {
        val a = b.aset
        return mapOf(
            "id" to a.id, "ord" to a.order, "code" to a.code, "name" to a.name, "cat" to a.category, "classif" to a.classif,
            "owner" to a.owner, "tenure" to a.tenure, "dept" to a.dept, "loc" to a.location, "user" to a.user, "cond" to a.cond,
            "acq" to a.acq, "check" to a.checkDate, "remarks" to a.remarks, "nbv" to a.nbv, "harga" to a.harga, "masa" to a.masa,
            "tgl" to a.tglTercatat, "nbvKet" to a.nbvKet, "catatan" to a.catatan, "sumber" to a.sumber, "src" to a.srcStatus,
            "barcode" to a.barcode?.name, "bsrc" to a.barcodeSrc.name, "pcodes" to a.pcodes, "state" to a.state.name,
            "audited" to a.auditedAt, "log" to a.log, "created" to a.createdAt, "updated" to a.updatedAt, "temuan" to a.temuanId,
            "foto" to b.fotos.map { mapOf("id" to it.id, "path" to it.path, "order" to it.order, "taken" to it.takenAt) },
        )
    }

    /** Cadangan lama tanpa kunci "aset" menghasilkan daftar kosong. */
    fun assetsFromJson(root: Any?): List<AsetBundle> = l(m(root)["aset"]).map { x ->
        val o = m(x)
        val id = s(o["id"]).ifEmpty { throw IllegalArgumentException("id aset kosong") }
        val a = AsetModel(
            id, i(o["ord"]), s(o["code"]), s(o["name"]), s(o["cat"]).ifEmpty { "other" }, s(o["classif"]), s(o["owner"]), s(o["tenure"]).ifEmpty { "own" },
            s(o["dept"]), s(o["loc"]), s(o["user"]), s(o["cond"]), s(o["acq"]), s(o["check"]), s(o["remarks"]),
            (o["nbv"] as? Number)?.toDouble(), (o["harga"] as? Number)?.toDouble(), (o["masa"] as? Number)?.toInt(),
            s(o["tgl"]), s(o["nbvKet"]), s(o["catatan"]), s(o["sumber"]), s(o["src"]),
            StatusBarcode.from(sn(o["barcode"])), SumberBarcode.from(sn(o["bsrc"])), l(o["pcodes"]).mapNotNull { it as? String },
            AuditState.from(sn(o["state"])), n(o["audited"]), l(o["log"]).mapNotNull { it as? String },
            n(o["created"]) ?: 0L, n(o["updated"]) ?: 0L, sn(o["temuan"]),
        )
        AsetBundle(a, l(o["foto"]).map { fx -> val f = m(fx); AsetFotoModel(s(f["id"]), id, safePath(s(f["path"])), i(f["order"]), n(f["taken"])) })
    }
}
