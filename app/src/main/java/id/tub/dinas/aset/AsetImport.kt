package id.tub.dinas.aset

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.JsonReader
import android.util.JsonToken
import id.tub.dinas.data.Repository
import id.tub.dinas.domain.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.time.Instant
import java.util.UUID

/** Hasil pemeriksaan (pratinjau) atau penerapan import. */
/** Satu baris perbandingan: aset di database (lama) vs aset di file (baru). status: baru | ubah | sama. diff = (kolom, nilai database, nilai import). */
class ImportItem(val key: String, val label: String, val status: String, val lama: AsetModel?, val baru: AsetModel, val diff: List<Triple<String, String, String>>, val tambahFoto: Int = 0)

class ImportReport(val total: Int, val baru: Int, val update: Int, val sama: Int, val ganda: Int, val foto: Int, val info: String, val perubahan: List<String> = emptyList(), val totalPerubahan: Int = 0, val items: List<ImportItem> = emptyList())

/** Menerapkan baris hasil baca ke register: kunci = kode ternormalisasi; bila kosong nama+dept. Mode "Lengkapi": hanya mengisi yang kosong. */
class AsetImporter(private val repo: Repository, existing: List<AsetModel>, private val fotoCount: Map<String, Int>, private val dryRun: Boolean, private val timpa: Boolean = false, private val pilih: Set<String>? = null) {
    val items = ArrayList<ImportItem>()
    private val byCode = HashMap<String, AsetModel>()
    private val byName = HashMap<String, AsetModel>()
    private val seen = HashSet<String>()
    val perubahan = ArrayList<String>(); var totalPerubahan = 0
    var total = 0; var baru = 0; var update = 0; var sama = 0; var ganda = 0; var foto = 0

    init { existing.forEach { index(it) } }

    private fun index(a: AsetModel) {
        val k = KodeAset.norm(a.code)
        if (k.isNotEmpty()) byCode[k] = a else if (a.name.isNotBlank()) byName[nameKey(a)] = a
    }
    private fun nameKey(a: AsetModel) = (a.name.trim() + "|" + a.dept.trim()).lowercase()
    private fun keyOf(a: AsetModel): String { val k = KodeAset.norm(a.code); return if (k.isNotEmpty()) "c:$k" else "n:${nameKey(a)}" }

    suspend fun add(m0: AsetModel, photoCount: Int, photos: () -> List<ByteArray>) {
        // Staff Site: kolom NBV di file diabaikan sama sekali.
        val m = if (id.tub.dinas.security.Lisensi.ho) m0 else m0.copy(nbv = null, harga = null, masa = null, tglTercatat = "", nbvKet = "", catatan = "", sumber = "", srcStatus = "")
        total++
        val key = keyOf(m)
        if (key == "n:|") return
        if (!seen.add(key)) { ganda++; return }
        val old = (KodeAset.norm(m.code).takeIf { it.isNotEmpty() }?.let { byCode[it] }) ?: if (KodeAset.norm(m.code).isEmpty()) byName[nameKey(m)] else null
        if (!dryRun && pilih != null && key !in pilih) return
        val judul = m.code.ifBlank { m.name }
        if (old == null) {
            baru++; foto += photoCount
            if (dryRun) { if (items.size < 3000) items.add(ImportItem(key, judul, "baru", null, m, emptyList(), photoCount)); return }
            val a = m.copy(id = UUID.randomUUID().toString(), createdAt = System.currentTimeMillis()).addLog("diimpor")
            val id = repo.saveAset(a)
            unik(photos()).forEach { repo.addAsetFoto(id, it, null) }
            index(a.copy(id = id))
            return
        }
        val merged = if (timpa) perbarui(old, m) else merge(old, m)
        val needPhotos = photoCount > 0 && (fotoCount[old.id] ?: 0) == 0
        if (merged == old && !needPhotos) { sama++; if (dryRun && items.size < 3000) items.add(ImportItem(key, judul, "sama", old, m, emptyList())); return }
        update++
        selisih(old, merged)
        if (dryRun && items.size < 3000) items.add(ImportItem(key, judul, "ubah", old, m, daftarBeda(old, merged), if (needPhotos) photoCount else 0))
        if (needPhotos) foto += photoCount
        if (dryRun) return
        repo.saveAset(merged.addLog(if (timpa) "diperbarui dari import" else "dilengkapi dari import"))
        if (needPhotos) unik(photos()).forEach { repo.addAsetFoto(old.id, it, null) }
        index(merged)
    }

    /** Membuang foto kembar (dHash, jarak Hamming ≤ 6) di dalam satu aset, mis. gambar yang tertanam dua kali. */
    private fun unik(l: List<ByteArray>): List<ByteArray> {
        val hs = ArrayList<Long>(); val out = ArrayList<ByteArray>()
        for (b in l) {
            val h = id.tub.dinas.export.Imaging.dHash(b)
            if (h != null && hs.any { id.tub.dinas.export.Imaging.hamming(it, h) <= 6 }) continue
            if (h != null) hs.add(h)
            out.add(b)
        }
        return out
    }

    /** Mencatat tiap kolom yang berubah (maks. 300 baris ditampilkan). */
    private fun selisih(o: AsetModel, n: AsetModel) {
        val nama = o.code.ifBlank { o.name }
        for ((label, x, y) in daftarBeda(o, n)) {
            totalPerubahan++
            if (perubahan.size < 300) perubahan.add("$nama · $label: ${x.ifBlank { "(kosong)" }} → ${y.ifBlank { "(kosong)" }}")
        }
    }

    private fun daftarBeda(o: AsetModel, n: AsetModel): List<Triple<String, String, String>> {
        val out = ArrayList<Triple<String, String, String>>()
        fun c(label: String, a: Any?, b: Any?) {
            val x = a?.toString().orEmpty(); val y = b?.toString().orEmpty()
            if (x != y) out.add(Triple(label, x, y))
        }
        c("Nama", o.name, n.name); c("Klasifikasi", o.classif, n.classif); c("PT Pemilik", o.owner, n.owner)
        c("Kepemilikan", o.tenure, n.tenure); c("Dept", o.dept, n.dept); c("Lokasi", o.location, n.location); c("User", o.user, n.user)
        c("Kondisi", o.cond, n.cond); c("Tgl perolehan", o.acq, n.acq); c("Tgl cek", o.checkDate, n.checkDate); c("Keterangan", o.remarks, n.remarks)
        c("Nilai buku", o.nbv, n.nbv); c("Harga", o.harga, n.harga); c("Masa manfaat", o.masa, n.masa)
        c("Tgl tercatat", o.tglTercatat, n.tglTercatat); c("NBV ket.", o.nbvKet, n.nbvKet); c("Catatan", o.catatan, n.catatan)
        c("Sumber", o.sumber, n.sumber); c("Status match", o.srcStatus, n.srcStatus); c("Barcode", o.barcode, n.barcode)
        c("Status audit", o.state, n.state)
        if (o.pcodes != n.pcodes) c("Kode tambahan", o.pcodes.joinToString(), n.pcodes.joinToString())
        return out
    }

    /** Mode "Perbarui": nilai tak kosong dari file menggantikan data lama; sel kosong di file tidak menghapus. Status audit & foto tetap. */
    private fun perbarui(o: AsetModel, n: AsetModel): AsetModel {
        fun s(a: String, b: String) = b.trim().ifBlank { a }
        val kelasBaru = n.classif.isNotBlank()
        return o.copy(
            name = s(o.name, n.name),
            category = if (kelasBaru) n.category else o.category, classif = s(o.classif, n.classif),
            owner = s(o.owner, n.owner), tenure = if (n.tenure == "lease") "lease" else o.tenure,
            dept = s(o.dept, n.dept), location = s(o.location, n.location), user = s(o.user, n.user),
            cond = s(o.cond, n.cond), acq = s(o.acq, n.acq), checkDate = s(o.checkDate, n.checkDate), remarks = s(o.remarks, n.remarks),
            nbv = n.nbv ?: o.nbv, harga = n.harga ?: o.harga, masa = n.masa ?: o.masa,
            tglTercatat = s(o.tglTercatat, n.tglTercatat), nbvKet = s(o.nbvKet, n.nbvKet), catatan = s(o.catatan, n.catatan),
            sumber = s(o.sumber, n.sumber), srcStatus = s(o.srcStatus, n.srcStatus),
            barcode = n.barcode ?: o.barcode, barcodeSrc = if (n.barcode != null) n.barcodeSrc else o.barcodeSrc,
            pcodes = (o.pcodes + n.pcodes).distinct(),
            state = if (n.state != AuditState.PENDING) n.state else o.state, auditedAt = n.auditedAt ?: o.auditedAt,
        )
    }

    private fun merge(o: AsetModel, n: AsetModel): AsetModel = o.copy(
        name = o.name.ifBlank { n.name },
        category = if (o.classif.isBlank() && n.classif.isNotBlank()) n.category else o.category,
        classif = o.classif.ifBlank { n.classif },
        owner = o.owner.ifBlank { n.owner },
        dept = o.dept.ifBlank { n.dept }, location = o.location.ifBlank { n.location }, user = o.user.ifBlank { n.user },
        cond = o.cond.ifBlank { n.cond }, acq = o.acq.ifBlank { n.acq }, checkDate = o.checkDate.ifBlank { n.checkDate },
        remarks = o.remarks.ifBlank { n.remarks },
        nbv = o.nbv ?: n.nbv, harga = o.harga ?: n.harga, masa = o.masa ?: n.masa,
        tglTercatat = o.tglTercatat.ifBlank { n.tglTercatat }, nbvKet = o.nbvKet.ifBlank { n.nbvKet },
        catatan = o.catatan.ifBlank { n.catatan }, sumber = o.sumber.ifBlank { n.sumber }, srcStatus = o.srcStatus.ifBlank { n.srcStatus },
        barcode = o.barcode ?: n.barcode, barcodeSrc = if (o.barcode == null && n.barcode != null) n.barcodeSrc else o.barcodeSrc,
        pcodes = (o.pcodes + n.pcodes).distinct(),
        state = if (o.state == AuditState.PENDING && n.state != AuditState.PENDING) n.state else o.state,
        auditedAt = o.auditedAt ?: n.auditedAt,
    )

    fun report(info: String) = ImportReport(total, baru, update, sama, ganda, foto, info, perubahan.toList(), totalPerubahan, items.toList())
}

object AsetImport {
    // ---------------- JSON (cadangan AsetScan v2 / Master Database.json) ----------------

    /** Membaca JSON AsetScan secara streaming. Bila `denganFoto` false, foto dilewati (hanya dihitung). */
    suspend fun streamJson(input: InputStream, denganFoto: Boolean, onAsset: suspend (AsetModel, Int, () -> List<ByteArray>) -> Unit): Int {
        val r = JsonReader(java.io.InputStreamReader(java.io.BufferedInputStream(input, 64 * 1024), Charsets.UTF_8))
        var n = 0
        var sawAssets = false
        r.beginObject()
        while (r.hasNext()) {
            val name = r.nextName()
            if (name == "assets" && r.peek() == JsonToken.BEGIN_ARRAY) {
                sawAssets = true
                r.beginArray()
                while (r.hasNext()) {
                    val (m, photos, count) = readAsset(r, denganFoto)
                    n++
                    onAsset(m, count) { photos.mapNotNull { dataUrlToBytes(it) } }
                }
                r.endArray()
            } else r.skipValue()
        }
        r.endObject()
        if (!sawAssets) throw IOException("Bukan cadangan AsetScan (tidak ada daftar aset)")
        return n
    }

    private data class Read(val m: AsetModel, val photos: List<String>, val count: Int)

    private fun readAsset(r: JsonReader, withPhotos: Boolean): Read {
        val s = HashMap<String, String>()
        val nums = HashMap<String, Double?>()
        var pcodes = emptyList<String>()
        val photos = ArrayList<String>()
        var count = 0
        r.beginObject()
        while (r.hasNext()) {
            val k = r.nextName()
            when (k) {
                "photos" -> {
                    if (r.peek() != JsonToken.BEGIN_ARRAY) { r.skipValue(); continue }
                    r.beginArray()
                    while (r.hasNext()) {
                        if (r.peek() == JsonToken.STRING) { count++; if (withPhotos) photos.add(r.nextString()) else r.skipValue() } else r.skipValue()
                    }
                    r.endArray()
                }
                "pcodes" -> {
                    if (r.peek() != JsonToken.BEGIN_ARRAY) { r.skipValue(); continue }
                    val l = ArrayList<String>(); r.beginArray()
                    while (r.hasNext()) if (r.peek() == JsonToken.STRING) l.add(r.nextString()) else r.skipValue()
                    r.endArray(); pcodes = l
                }
                "code", "name", "category", "classif", "owner", "tenure", "dept", "location", "user", "cond", "acq", "checkDate", "remarks",
                "barcode", "barcodeSrc", "state", "auditedAt", "nbvKet", "tglTercatat", "catatan", "sumber", "srcStatus" ->
                    when (r.peek()) { JsonToken.STRING -> s[k] = r.nextString(); JsonToken.NUMBER -> s[k] = r.nextString(); else -> r.skipValue() }
                "nbv", "harga", "masa", "ord" ->
                    if (r.peek() == JsonToken.NUMBER) nums[k] = r.nextDouble() else if (r.peek() == JsonToken.STRING) nums[k] = r.nextString().toDoubleOrNull() else r.skipValue()
                else -> r.skipValue()
            }
        }
        r.endObject()
        val code = s["code"].orEmpty().uppercase().replace(Regex("\\s+"), "").let { if (it == "N/A") "" else it }
        val cond = when (s["cond"]) { "good" -> "Baik"; "fair" -> "Cukup"; "poor" -> "Rusak"; else -> s["cond"].orEmpty() }
        val kelas = KodeAset.kelasDariKode(code) ?: KodeAset.kelasDariNama(s["classif"])
        val name = s["name"].orEmpty().trim()
        val m = AsetModel(
            id = "", order = nums["ord"]?.toInt() ?: 0, code = code, name = name,
            category = kelas?.kategori ?: s["category"]?.takeIf { it in KodeAset.KATEGORI } ?: KodeAset.tebakKategori(code, name),
            classif = kelas?.nama ?: s["classif"].orEmpty(),
            owner = s["owner"].orEmpty(), tenure = if (s["tenure"] == "lease") "lease" else "own",
            dept = s["dept"].orEmpty(), location = s["location"].orEmpty(), user = s["user"].orEmpty(), cond = cond,
            acq = s["acq"].orEmpty(), checkDate = s["checkDate"].orEmpty(), remarks = s["remarks"].orEmpty(),
            nbv = nums["nbv"], harga = nums["harga"], masa = nums["masa"]?.toInt(),
            tglTercatat = s["tglTercatat"].orEmpty(), nbvKet = s["nbvKet"].orEmpty(), catatan = s["catatan"].orEmpty(),
            sumber = s["sumber"].orEmpty(), srcStatus = s["srcStatus"].orEmpty(),
            barcode = StatusBarcode.from(s["barcode"]?.uppercase()),
            barcodeSrc = if (s["barcode"].isNullOrEmpty()) SumberBarcode.NONE else SumberBarcode.from(s["barcodeSrc"]).let { if (it == SumberBarcode.NONE) SumberBarcode.AUTO else it },
            pcodes = pcodes.map { KodeAset.norm(it) }.filter { it.isNotEmpty() },
            state = when (s["state"]) { "found" -> AuditState.FOUND; "missing" -> AuditState.MISSING; "damaged" -> AuditState.DAMAGED; else -> AuditState.PENDING },
            auditedAt = s["auditedAt"]?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() },
        )
        return Read(m, photos, count)
    }

    private fun dataUrlToBytes(d: String): ByteArray? {
        val i = d.indexOf("base64,")
        if (i < 0) return null
        return runCatching { Base64.decode(d.substring(i + 7), Base64.DEFAULT) }.getOrNull()?.takeIf { it.size in 1..(12 * 1024 * 1024) }
    }

    // ---------------- Excel ----------------

    private val ALIAS: List<Pair<String, List<String>>> = listOf(
        "no" to listOf("no", "no.", "nomor", "urut"),
        "code" to listOf("asset number", "kode", "code", "tag", "no aset", "nomor aset", "asset no", "asset id"),
        "classif" to listOf("assets classification", "asset classification", "klasifikasi", "kategori", "category", "jenis", "kelas", "type", "tipe"),
        "acq" to listOf("purchase date", "tgl perolehan", "tanggal perolehan", "acquisition date"),
        "name" to listOf("description", "deskripsi", "nama", "uraian", "asset name", "item", "barang"),
        "location" to listOf("location", "lokasi", "ruang", "area"),
        "dept" to listOf("dept.", "departemen", "department", "dept", "divisi", "bagian", "unit kerja"),
        "user" to listOf("user", "pengguna", "pemakai"),
        "checkDate" to listOf("date of physical check", "tanggal pengecekan", "tgl pengecekan", "physical check"),
        "cond" to listOf("condition", "kondisi"),
        "barcode" to listOf("barcode"),
        "nbvKet" to listOf("nbv - keterangan"),
        "tglTercatat" to listOf("tanggal tercatat"),
        "masa" to listOf("masa manfaat", "masa manfaat (bln)"),
        "harga" to listOf("harga perolehan", "harga perolehan (idr)"),
        "catatan" to listOf("catatan (nbv)"),
        "sumber" to listOf("sumber nbv", "sumber nbv (sheet!baris)"),
        "nbv" to listOf("nilai buku (idr)", "nilai buku", "book value", "net book", "nbv"),
        "remarks" to listOf("remarks", "keterangan", "catatan", "note", "remark"),
        "tenure" to listOf("status kepemilikan", "kepemilikan", "ownership", "milik/sewa", "milik / sewa", "sewa", "tenure"),
        "owner" to listOf("pt pemilik", "nama pt", "pemilik", "owner", "perusahaan", "entitas", "company"),
        "srcStatus" to listOf("status match", "status"),
    )

    private fun nh(s: String) = s.lowercase().replace(Regex("\\s+"), " ").trim()

    private class Layout(val sheet: XSheet, val headerRow: Int, val cols: Map<String, Int>, val score: Int)

    private fun layoutOf(sh: XSheet): Layout? {
        var best: Layout? = null
        for (r in sh.rows.keys.filter { it < 20 }) {
            val cells = sh.rows[r] ?: continue
            val heads = cells.mapValues { nh(it.value.text) }
            val cols = HashMap<String, Int>(); val used = HashSet<Int>()
            for ((f, al) in ALIAS) heads.entries.firstOrNull { it.key !in used && it.value in al }?.let { cols[f] = it.key; used.add(it.key) }
            for ((f, al) in ALIAS) {
                if (f in cols || f == "no") continue
                heads.entries.firstOrNull { e -> e.key !in used && al.any { a -> a.length > 2 && e.value.contains(a) } }?.let { cols[f] = it.key; used.add(it.key) }
            }
            val score = cols.size
            if (best == null || score > best.score) best = Layout(sh, r, cols, score)
        }
        val b = best?.takeIf { it.score >= 3 && ("code" in it.cols || "name" in it.cols) } ?: return null
        // Judul "Keterangan" tanpa kolom nama/deskripsi = nama barang (format Master Database BAU).
        val rc = b.cols["remarks"]
        if ("name" !in b.cols && rc != null && nh(b.sheet.rows[b.headerRow]?.get(rc)?.text.orEmpty()) == "keterangan")
            return Layout(b.sheet, b.headerRow, b.cols.filterKeys { it != "remarks" } + ("name" to rc), b.score)
        return b
    }

    /** Memilih sheet dengan judul terbaik. Mengembalikan null bila tidak ada yang cocok. */
    private fun pick(sheets: List<XSheet>): Layout? =
        sheets.mapNotNull { layoutOf(it) }.maxByOrNull { it.score * 1000 + it.sheet.images.values.sumOf { l -> l.size } + it.sheet.rows.size }

    /** Nama perusahaan dari baris "Company : …" di bagian atas salah satu sheet (templat Memo FAT). */
    private fun perusahaan(sheets: List<XSheet>): String {
        for (sh in sheets) for (r in sh.rows.keys.filter { it < 12 }) {
            val cells = sh.rows[r] ?: continue
            val ks = cells.keys.sorted()
            val i = ks.indexOfFirst { cells[it]?.text?.trim()?.equals("company", true) == true }
            if (i >= 0) ks.drop(i + 1).map { cells[it]?.text?.trim().orEmpty() }.firstOrNull { it.isNotEmpty() && it != ":" }?.let { return it }
        }
        return ""
    }

    fun parseAngka(s: String): Double? {
        var t = s.trim().replace(Regex("[^0-9.,\\-]"), "")
        if (t.isEmpty() || t == "-") return null
        val dots = t.count { it == '.' }; val commas = t.count { it == ',' }
        t = when {
            dots > 1 && commas == 0 -> t.replace(".", "")
            commas > 1 && dots == 0 -> t.replace(",", "")
            dots >= 1 && commas == 1 && t.lastIndexOf(',') > t.lastIndexOf('.') -> t.replace(".", "").replace(',', '.')
            commas >= 1 && dots == 1 && t.lastIndexOf('.') > t.lastIndexOf(',') -> t.replace(",", "")
            dots == 1 && commas == 0 && Regex("^-?\\d{1,3}\\.\\d{3}$").matches(t) -> t.replace(".", "")
            commas == 1 && dots == 0 -> if (Regex("^-?\\d{1,3},\\d{3}$").matches(t)) t.replace(",", "") else t.replace(',', '.')
            else -> t
        }
        return t.toDoubleOrNull()
    }

    fun parseTanggal(c: XCell?): String {
        if (c == null) return ""
        if (c.date) return c.text
        val t = c.text.trim()
        Regex("^(\\d{4})-(\\d{1,2})-(\\d{1,2})").find(t)?.let { return "%04d-%02d-%02d".format(it.groupValues[1].toInt(), it.groupValues[2].toInt(), it.groupValues[3].toInt()) }
        Regex("^(\\d{1,2})[/.-](\\d{1,2})[/.-](\\d{2,4})").find(t)?.let {
            val y = it.groupValues[3].toInt().let { v -> if (v < 100) 2000 + v else v }
            return "%04d-%02d-%02d".format(y, it.groupValues[2].toInt(), it.groupValues[1].toInt())
        }
        return ""
    }

    class XlsxPlan(val rows: List<Triple<AsetModel, Int, List<String>>>, val sheet: String, val headerRow: Int, val kolom: List<String>, val file: File)

    /** Membaca .xlsx menjadi baris aset. `rows` = (model, jumlah foto, nama entri gambar di zip). Semua sheet yang berisi data dibaca; baris berkode sama digabung. */
    fun planXlsx(file: File): XlsxPlan {
        val sheets = XlsxReader.read(file)
        val layouts = sheets.mapNotNull { layoutOf(it) }
        if (layouts.isEmpty()) throw IOException("Judul kolom tidak dikenali. Pastikan ada kolom seperti Asset Number, Description, Dept.")
        val pt = perusahaan(sheets)
        val hasil = ArrayList<Triple<AsetModel, Int, List<String>>>()
        val indeks = HashMap<String, Int>()
        val dipakai = ArrayList<Layout>()
        for (lay in layouts.sortedByDescending { it.score }) {
            val baris = bacaSheet(lay, pt)
            if (baris.isEmpty()) continue
            dipakai.add(lay)
            for (t in baris) {
                val k = KodeAset.norm(t.first.code)
                val i = if (k.isEmpty()) null else indeks[k]
                if (i == null) { if (k.isNotEmpty()) indeks[k] = hasil.size; hasil.add(t) }
                else {
                    val o = hasil[i]; val n = t.first
                    val m = o.first.copy(
                        name = o.first.name.ifBlank { n.name }, classif = o.first.classif.ifBlank { n.classif }, dept = o.first.dept.ifBlank { n.dept },
                        location = o.first.location.ifBlank { n.location }, user = o.first.user.ifBlank { n.user }, cond = o.first.cond.ifBlank { n.cond },
                        acq = o.first.acq.ifBlank { n.acq }, remarks = o.first.remarks.ifBlank { n.remarks }, owner = o.first.owner.ifBlank { n.owner },
                        barcode = o.first.barcode ?: n.barcode, barcodeSrc = if (o.first.barcode == null && n.barcode != null) n.barcodeSrc else o.first.barcodeSrc,
                    )
                    hasil[i] = Triple(m, o.second + t.second, o.third + t.third)
                }
            }
        }
        if (dipakai.isEmpty()) throw IOException("Judul kolom dikenali tetapi tidak ada baris aset yang terisi.")
        val first = dipakai.first()
        return XlsxPlan(hasil, dipakai.joinToString(" + ") { it.sheet.name }, first.headerRow + 1, dipakai.flatMap { it.cols.keys }.distinct(), file)
    }

    private fun bacaSheet(lay: Layout, pt: String): List<Triple<AsetModel, Int, List<String>>> {
        val sh = lay.sheet
        val rows = ArrayList<Triple<AsetModel, Int, List<String>>>()
        fun cell(r: Map<Int, XCell>, f: String) = lay.cols[f]?.let { r[it] }
        fun txt(r: Map<Int, XCell>, f: String) = cell(r, f)?.text?.trim().orEmpty()
        for ((ri, r) in sh.rows) {
            if (ri <= lay.headerRow) continue
            val imgs = sh.images[ri].orEmpty()
            val code = txt(r, "code").uppercase().replace(Regex("\\s+"), "").let { if (it == "N/A") "" else it }
            val name = txt(r, "name")
            val remarks = txt(r, "remarks")
            if (code.isEmpty() && name.isEmpty() && remarks.isEmpty() && imgs.isEmpty()) continue
            if (code == "ASSETNUMBER" || name.equals("description", true)) continue
            val classifTxt = txt(r, "classif").takeIf { it.length > 2 }.orEmpty()
            val kelas = KodeAset.kelasDariKode(code) ?: KodeAset.kelasDariNama(classifTxt)
            val bc = txt(r, "barcode").lowercase().let { when { it.contains("untag") -> StatusBarcode.UNTAGGED; it.contains("tagged") -> StatusBarcode.TAGGED; it.contains("non") -> StatusBarcode.NONASSET; else -> null } }
            val ten = txt(r, "tenure").lowercase().let { if (Regex("sewa|rent|lease|rental|kontrak").containsMatchIn(it)) "lease" else "own" }
            val cond = txt(r, "cond").let { when (it.lowercase()) { "good" -> "Baik"; "fair" -> "Cukup"; "poor" -> "Rusak"; else -> it } }
            fun num(f: String) = cell(r, f)?.let { it.num ?: parseAngka(it.text) }
            val tglCatat = parseTanggal(cell(r, "tglTercatat"))
            val m = AsetModel(
                id = "", code = code, name = name,
                category = kelas?.kategori ?: KodeAset.tebakKategori(code, name + " " + classifTxt),
                classif = kelas?.nama ?: classifTxt,
                owner = txt(r, "owner").ifBlank { pt }, tenure = ten, dept = txt(r, "dept"), location = txt(r, "location"), user = txt(r, "user"), cond = cond,
                acq = parseTanggal(cell(r, "acq")).ifBlank { tglCatat }, checkDate = parseTanggal(cell(r, "checkDate")), remarks = remarks,
                nbv = num("nbv"), harga = num("harga"), masa = num("masa")?.toInt(),
                tglTercatat = tglCatat, nbvKet = txt(r, "nbvKet"), catatan = txt(r, "catatan"),
                sumber = txt(r, "sumber"), srcStatus = txt(r, "srcStatus"),
                barcode = bc, barcodeSrc = if (bc != null) SumberBarcode.EXCEL else SumberBarcode.NONE,
            )
            rows.add(Triple(m, imgs.size, imgs))
        }
        return rows
    }

    /** Gambar apa pun -> JPEG ≤ maxSide. Null bila tidak dapat dibaca. */
    fun toJpeg(bytes: ByteArray, maxSide: Int = 1600, quality: Int = 85): ByteArray? {
        val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, b)
        if (b.outWidth <= 0 || b.outHeight <= 0) return null
        var sample = 1
        while (maxOf(b.outWidth, b.outHeight) / (sample * 2) >= maxSide) sample *= 2
        var bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val sc = maxSide.toFloat() / maxOf(bmp.width, bmp.height)
        if (sc < 1f) { val t = Bitmap.createScaledBitmap(bmp, (bmp.width * sc).toInt().coerceAtLeast(1), (bmp.height * sc).toInt().coerceAtLeast(1), true); if (t !== bmp) bmp.recycle(); bmp = t }
        val o = ByteArrayOutputStream(256 * 1024)
        bmp.compress(Bitmap.CompressFormat.JPEG, quality, o)
        bmp.recycle()
        return o.toByteArray()
    }
}
