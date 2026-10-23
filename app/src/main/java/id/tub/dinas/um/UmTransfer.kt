package id.tub.dinas.um

import android.util.Base64
import android.util.JsonReader
import android.util.JsonToken
import android.util.JsonWriter
import id.tub.dinas.aset.AsetImport
import id.tub.dinas.data.Repository
import id.tub.dinas.domain.*
import id.tub.dinas.export.Imaging
import id.tub.dinas.export.XStyle
import id.tub.dinas.aset.XlsxReader
import id.tub.dinas.export.XlsxWriter
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant

class UmImpor(var um: Int = 0, var bon: Int = 0, var foto: Int = 0, var lewat: Int = 0)

/** Ekspor/impor Uang Muka: JSON lengkap (foto tertanam) dan Excel (rekap + rincian bon dengan foto). */
object UmTransfer {
    private const val EMU = 9525L

    // ---------------- JSON ----------------

    fun tulisJson(daftar: List<UmBundle>, baca: (String) -> ByteArray?, out: OutputStream) {
        val w = JsonWriter(java.io.OutputStreamWriter(java.io.BufferedOutputStream(out, 64 * 1024), Charsets.UTF_8))
        w.beginObject()
        w.name("app").value("uangmuka"); w.name("v").value(1L); w.name("at").value(Instant.now().toString())
        w.name("items").beginArray()
        for (b in daftar) {
            val u = b.um
            w.beginObject()
            w.name("id").value(u.id); w.name("nomor").value(u.nomor); w.name("judul").value(u.judul); w.name("pemohon").value(u.pemohon)
            w.name("tglPengajuan").value(u.tglPengajuan); w.name("jumlahDiajukan").value(u.jumlahDiajukan)
            w.name("tglCair").value(u.tglCair); w.name("jumlahCair").value(u.jumlahCair)
            w.name("status").value(u.status.name.lowercase()); w.name("catatan").value(u.catatan)
            w.name("bon").beginArray()
            for (bb in b.bons) {
                val x = bb.bon
                w.beginObject()
                w.name("id").value(x.id); w.name("tanggal").value(x.tanggal); w.name("kategori").value(BonKategori.encode(x.kategori, x.tag).let { e -> e.substringBefore(":").lowercase() + (if (e.contains(":")) ":" + e.substringAfter(":") else "") })
                w.name("uraian").value(x.uraian); w.name("jumlah").value(x.jumlah)
                w.name("foto").beginArray()
                for (f in bb.fotos) baca(f.path)?.let { raw -> Imaging.jpeg(raw, 1400, 80)?.let { j -> w.value("data:image/jpeg;base64," + Base64.encodeToString(j, Base64.NO_WRAP)) } }
                w.endArray()
                w.endObject()
            }
            w.endArray()
            w.endObject()
        }
        w.endArray(); w.endObject(); w.flush()
    }

    private class JBon(val tanggal: String, val kategori: String, val uraian: String, val jumlah: Double, val id: String, val foto: List<ByteArray>)
    private class JUm(val m: UangMukaModel, val bon: List<JBon>)

    private fun dataUrl(d: String): ByteArray? {
        val i = d.indexOf("base64,"); if (i < 0) return null
        return runCatching { Base64.decode(d.substring(i + 7), Base64.DEFAULT) }.getOrNull()?.takeIf { it.size in 1..(12 * 1024 * 1024) }
    }

    private fun strOrNull(r: JsonReader): String? = when (r.peek()) { JsonToken.STRING, JsonToken.NUMBER -> r.nextString(); else -> { r.skipValue(); null } }
    private fun numOrZero(r: JsonReader): Double = when (r.peek()) { JsonToken.NUMBER -> r.nextDouble(); JsonToken.STRING -> AsetImport.parseAngka(r.nextString()) ?: 0.0; else -> { r.skipValue(); 0.0 } }

    private fun readBon(r: JsonReader): JBon {
        var tgl = ""; var kat = ""; var ur = ""; var jml = 0.0; var id = ""; val fotos = ArrayList<ByteArray>()
        r.beginObject()
        while (r.hasNext()) when (r.nextName()) {
            "tanggal" -> tgl = strOrNull(r).orEmpty()
            "kategori" -> kat = strOrNull(r).orEmpty()
            "uraian" -> ur = strOrNull(r).orEmpty()
            "jumlah" -> jml = numOrZero(r)
            "id" -> id = strOrNull(r).orEmpty()
            "foto" -> if (r.peek() == JsonToken.BEGIN_ARRAY) { r.beginArray(); while (r.hasNext()) { if (r.peek() == JsonToken.STRING) dataUrl(r.nextString())?.let { fotos.add(it) } else r.skipValue() }; r.endArray() } else r.skipValue()
            else -> r.skipValue()
        }
        r.endObject()
        return JBon(tgl, kat, ur, jml, id, fotos)
    }

    private suspend fun streamJson(input: InputStream, onItem: suspend (JUm) -> Unit) {
        val r = JsonReader(java.io.InputStreamReader(java.io.BufferedInputStream(input, 64 * 1024), Charsets.UTF_8))
        var app = ""; var saw = false
        r.beginObject()
        while (r.hasNext()) {
            when (r.nextName()) {
                "app" -> app = strOrNull(r).orEmpty()
                "items" -> if (r.peek() == JsonToken.BEGIN_ARRAY) {
                    saw = true
                    r.beginArray()
                    while (r.hasNext()) {
                        val s = HashMap<String, String>(); var dj = 0.0; var cr = 0.0; val bons = ArrayList<JBon>()
                        r.beginObject()
                        while (r.hasNext()) when (val k = r.nextName()) {
                            "jumlahDiajukan" -> dj = numOrZero(r)
                            "jumlahCair" -> cr = numOrZero(r)
                            "bon" -> if (r.peek() == JsonToken.BEGIN_ARRAY) { r.beginArray(); while (r.hasNext()) bons.add(readBon(r)); r.endArray() } else r.skipValue()
                            "id", "nomor", "judul", "pemohon", "tglPengajuan", "tglCair", "status", "catatan" -> s[k] = strOrNull(r).orEmpty()
                            else -> r.skipValue()
                        }
                        r.endObject()
                        val m = UangMukaModel(s["id"].orEmpty(), s["nomor"].orEmpty(), s["judul"].orEmpty(), null, s["pemohon"].orEmpty(),
                            s["tglPengajuan"].orEmpty(), dj, s["tglCair"].orEmpty(), cr, s["catatan"].orEmpty(), UmStatus.from(s["status"]?.uppercase()))
                        onItem(JUm(m, bons))
                    }
                    r.endArray()
                } else r.skipValue()
                else -> r.skipValue()
            }
        }
        r.endObject()
        if (!saw || (app.isNotEmpty() && app != "uangmuka")) throw IOException("Bukan berkas JSON Uang Muka")
    }

    // ---------------- penggabungan ke database ----------------

    private fun key(t: String, k: BonKategori, u: String, j: Double) = "$t|${k.name}|${u.trim().lowercase()}|${j.toLong()}"

    private class Gabung(val repo: Repository, val hasil: UmImpor) {
        var ada: List<UmBundle> = emptyList()
        suspend fun muat() { ada = repo.umBundles() }

        suspend fun um(m: UangMukaModel): UmBundle? =
            ada.firstOrNull { m.id.isNotEmpty() && it.um.id == m.id }
                ?: ada.firstOrNull { m.nomor.isNotBlank() && it.um.nomor.equals(m.nomor, true) }

        /** Mengembalikan id pengajuan (baru atau yang sudah ada, metadata diperbarui). */
        suspend fun simpanUm(m: UangMukaModel): String {
            val lama = um(m)
            return if (lama != null) { repo.saveUm(m.copy(id = lama.um.id, dinasId = lama.um.dinasId)); lama.um.id }
            else { hasil.um++; repo.saveUm(m.copy(id = "")) }
        }

        suspend fun bon(umId: String, tgl: String, kat: BonKategori, ur: String, jml: Double, id: String, foto: List<ByteArray>, tag: String = "") {
            val u = repo.umBundles().first { it.um.id == umId }
            val sama = u.bons.firstOrNull { id.isNotEmpty() && it.bon.id == id } ?: u.bons.firstOrNull { key(it.bon.tanggal, it.bon.kategori, it.bon.uraian, it.bon.jumlah) == key(tgl, kat, ur, jml) }
            if (sama != null) {
                hasil.lewat++
                if (sama.fotos.isEmpty()) for (f in foto) { repo.addBonFoto(sama.bon.id, f); hasil.foto++ }
                return
            }
            val nid = repo.saveBon(BonModel("", umId, tgl, kat, ur, jml, tag = tag))
            hasil.bon++
            for (f in foto) { repo.addBonFoto(nid, f); hasil.foto++ }
        }
    }

    suspend fun impor(file: File, repo: Repository): UmImpor {
        val hasil = UmImpor()
        val g = Gabung(repo, hasil); g.muat()
        val head = file.inputStream().use { s -> ByteArray(2).also { s.read(it) } }
        if (head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte()) imporExcel(file, g)
        else file.inputStream().use { s ->
            streamJson(s) { item ->
                val id = g.simpanUm(item.m)
                for (b in item.bon) g.bon(id, b.tanggal, BonKategori.from(b.kategori), b.uraian, b.jumlah, b.id, b.foto.mapNotNull { Imaging.jpeg(it, 1600, 85) }, BonKategori.tagDari(b.kategori))
                g.muat()
            }
        }
        return hasil
    }

    // ---------------- Excel ----------------

    private val HEAD = XStyle(bold = true, color = "FFFFFF", fill = "1F6FE0", h = "center", v = "center", border = true)
    private val CELL = XStyle(border = true, v = "center")
    private val NUM = XStyle(border = true, v = "center", h = "right")
    private val TITLE = XStyle(bold = true, size = 14.0)
    private val SUB = XStyle(bold = true, fill = "E8F1FF", border = true, v = "center")
    private val SUBNUM = XStyle(bold = true, fill = "E8F1FF", border = true, v = "center", h = "right")

    private fun thumb(b: ByteArray): Triple<ByteArray, Int, Int>? {
        val bmp = Imaging.decode(b, 640) ?: return null
        val o = java.io.ByteArrayOutputStream(64 * 1024)
        bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, o)
        val w = bmp.width; val h = bmp.height; bmp.recycle()
        return Triple(o.toByteArray(), w, h)
    }

    private const val MAX_FOTO_COL = 3

    fun tulisExcel(daftar: List<UmBundle>, baca: (String) -> ByteArray?, out: OutputStream) {
        val wb = XlsxWriter()
        // --- Rekap
        val rk = wb.sheet("Rekap")
        rk.colWidths(listOf(5.0, 20.0, 34.0, 13.0, 18.0, 18.0, 18.0, 18.0, 14.0, 22.0, 30.0))
        rk.text(0, 0, "Rekap Uang Muka & Realisasi", TITLE)
        val hr = listOf("No.", "No. Pengajuan", "Keperluan / Judul", "Tgl Pengajuan", "Diajukan (Rp)", "Cair (Rp)", "Total Bon (Rp)", "Sisa / (Kurang) (Rp)", "Status", "Pemohon", "Catatan")
        hr.forEachIndexed { c, t -> rk.text(2, c, t, HEAD) }
        rk.rowHeight(2, 30.0)
        var r = 3
        daftar.forEachIndexed { i, b ->
            val u = b.um
            rk.number(r, 0, (i + 1).toDouble(), CELL); rk.text(r, 1, u.nomor, CELL); rk.text(r, 2, u.judul, CELL); rk.text(r, 3, fmtTgl(u.tglPengajuan), CELL)
            rk.number(r, 4, u.jumlahDiajukan, NUM); rk.number(r, 5, u.jumlahCair, NUM); rk.number(r, 6, b.totalBon, NUM); rk.number(r, 7, b.sisa, NUM)
            rk.text(r, 8, u.status.label, CELL); rk.text(r, 9, u.pemohon, CELL); rk.text(r, 10, u.catatan, CELL)
            r++
        }
        rk.text(r, 0, "", SUB); rk.text(r, 1, "TOTAL", SUB); rk.text(r, 2, "", SUB); rk.text(r, 3, "", SUB)
        rk.number(r, 4, daftar.sumOf { it.um.jumlahDiajukan }, SUBNUM); rk.number(r, 5, daftar.sumOf { it.um.jumlahCair }, SUBNUM)
        rk.number(r, 6, daftar.sumOf { it.totalBon }, SUBNUM); rk.number(r, 7, daftar.sumOf { it.sisa }, SUBNUM)
        r += 2
        rk.text(r, 1, "Per kategori bon", TITLE); r++
        rk.text(r, 1, "Kategori", HEAD); rk.text(r, 2, "Jumlah bon", HEAD); rk.text(r, 4, "Total (Rp)", HEAD); r++
        for (k in BonKategori.entries) {
            val bons = daftar.flatMap { it.bons }.filter { it.bon.kategori == k }
            if (bons.isEmpty()) continue
            rk.text(r, 1, k.label, CELL); rk.number(r, 2, bons.size.toDouble(), NUM); rk.number(r, 4, bons.sumOf { it.bon.jumlah }, NUM); r++
        }

        // --- Bon (dengan foto)
        val bs = wb.sheet("Bon")
        val fotoW = 22.0
        bs.colWidths(listOf(5.0, 20.0, 13.0, 16.0, 36.0, 18.0) + List(MAX_FOTO_COL) { fotoW })
        val hb = listOf("No.", "No. Pengajuan", "Tanggal", "Kategori", "Uraian", "Jumlah (Rp)") + (1..MAX_FOTO_COL).map { "Foto $it" }
        hb.forEachIndexed { c, t -> bs.text(0, c, t, HEAD) }
        bs.rowHeight(0, 28.0)
        var row = 1; var no = 1
        for (b in daftar) for (bb in b.bons) {
            val x = bb.bon
            bs.number(row, 0, (no++).toDouble(), CELL); bs.text(row, 1, b.um.nomor, CELL); bs.text(row, 2, fmtTgl(x.tanggal), CELL)
            bs.text(row, 3, x.labelKat, CELL); bs.text(row, 4, x.uraian, CELL); bs.number(row, 5, x.jumlah, NUM)
            var hasImg = false
            bb.fotos.take(MAX_FOTO_COL).forEachIndexed { k, f ->
                val t = baca(f.path)?.let { thumb(it) } ?: return@forEachIndexed
                val boxPx = 150.0
                val sc = boxPx / maxOf(t.second, t.third)
                val cx = (t.second * sc * EMU).toLong(); val cy = (t.third * sc * EMU).toLong()
                bs.image(t.first, "jpeg", row, 6 + k, 6 * EMU, 6 * EMU, cx, cy)
                hasImg = true
            }
            for (k in 0 until MAX_FOTO_COL) bs.style(row, 6 + k, CELL)
            bs.rowHeight(row, if (hasImg) 124.0 else 30.0)
            row++
        }
        wb.write(out)
    }

    private fun nh(s: String) = s.lowercase().replace(Regex("\\s+"), " ").trim()

    private suspend fun imporExcel(file: File, g: Gabung) {
        val sheets = XlsxReader.read(file)
        fun col(h: Map<Int, String>, vararg al: String) = h.entries.firstOrNull { e -> al.any { e.value == it } }?.key
        fun headerOf(sh: id.tub.dinas.aset.XSheet, need: (Map<Int, String>) -> Boolean): Pair<Int, Map<Int, String>>? {
            for (r in sh.rows.keys.filter { it < 15 }) {
                val h = sh.rows[r]!!.mapValues { nh(it.value.text) }
                if (need(h)) return r to h
            }
            return null
        }
        val nomorIdx = mutableMapOf<String, String>()   // nomor -> id pengajuan
        // 1) Sheet rekap: metadata pengajuan
        for (sh in sheets) {
            val (hr, h) = headerOf(sh) { it.values.any { v -> v.startsWith("no. pengajuan") || v == "no pengajuan" || v == "nomor pengajuan" } && it.values.any { v -> v.startsWith("keperluan") || v == "judul" } } ?: continue
            val cN = col(h, "no. pengajuan", "no pengajuan", "nomor pengajuan"); val cJ = h.entries.firstOrNull { it.value.startsWith("keperluan") || it.value == "judul" }?.key
            val cT = h.entries.firstOrNull { it.value.startsWith("tgl pengajuan") || it.value.startsWith("tanggal pengajuan") }?.key
            val cD = h.entries.firstOrNull { it.value.startsWith("diajukan") }?.key; val cC = h.entries.firstOrNull { it.value.startsWith("cair") }?.key
            val cS = col(h, "status"); val cP = col(h, "pemohon"); val cK = col(h, "catatan")
            for ((ri, row) in sh.rows) {
                if (ri <= hr) continue
                fun t(c: Int?) = c?.let { row[it]?.text?.trim() }.orEmpty()
                fun n(c: Int?) = c?.let { row[it] }?.let { it.num ?: AsetImport.parseAngka(it.text) } ?: 0.0
                val nomor = t(cN); if (nomor.isEmpty() || nomor.equals("total", true)) continue
                val st = t(cS).lowercase().let { when { it.contains("selesai") -> UmStatus.SELESAI; it.contains("cair") -> UmStatus.CAIR; else -> UmStatus.DIAJUKAN } }
                val m = UangMukaModel("", nomor, t(cJ), null, t(cP), AsetImport.parseTanggal(cT?.let { row[it] }), n(cD), "", n(cC), t(cK), st)
                nomorIdx[nomor] = g.simpanUm(m)
                g.muat()
            }
        }
        // 2) Sheet bon
        var any = false
        for (sh in sheets) {
            val (hr, h) = headerOf(sh) { it.values.any { v -> v == "uraian" } && it.values.any { v -> v.startsWith("jumlah") } } ?: continue
            val cN = h.entries.firstOrNull { it.value.startsWith("no. pengajuan") || it.value == "no pengajuan" || it.value == "nomor pengajuan" }?.key
            val cT = h.entries.firstOrNull { it.value == "tanggal" || it.value == "tgl" }?.key
            val cK = h.entries.firstOrNull { it.value == "kategori" || it.value == "tag" }?.key
            val cU = col(h, "uraian"); val cJ = h.entries.firstOrNull { it.value.startsWith("jumlah") }?.key
            for ((ri, row) in sh.rows) {
                if (ri <= hr) continue
                val imgs = sh.images[ri].orEmpty()
                val uraian = cU?.let { row[it]?.text?.trim() }.orEmpty()
                val jml = cJ?.let { row[it] }?.let { it.num ?: AsetImport.parseAngka(it.text) } ?: 0.0
                if (uraian.isEmpty() && jml == 0.0 && imgs.isEmpty()) continue
                val nomor = cN?.let { row[it]?.text?.trim() }.orEmpty().ifEmpty { "UM-IMPOR" }
                val umId = nomorIdx[nomor] ?: g.simpanUm(UangMukaModel("", nomor, if (nomor == "UM-IMPOR") "Hasil impor" else nomor, null, "", todayIso(), 0.0, "", 0.0, "", UmStatus.DIAJUKAN)).also { nomorIdx[nomor] = it; g.muat() }
                val tgl = AsetImport.parseTanggal(cT?.let { row[it] }).ifEmpty { todayIso() }
                val fotos = imgs.mapNotNull { e -> XlsxReader.readImage(file, e)?.let { b -> Imaging.jpeg(b, 1600, 85) } }
                val katTeks = cK?.let { row[it]?.text }?.trim().orEmpty()
                val katDasar = BonKategori.from(katTeks)
                val katKustom = if (katTeks.isNotEmpty() && !katTeks.equals(katDasar.label, true) && !katTeks.equals(katDasar.name, true) && BonKategori.entries.none { it.label.equals(katTeks, true) }) katTeks else ""
                g.bon(umId, tgl, katDasar, uraian, jml, "", fotos, katKustom)
                any = true
            }
        }
        if (nomorIdx.isEmpty() && !any) throw IOException("Format Excel tidak dikenali. Gunakan berkas hasil ekspor aplikasi ini (sheet Rekap dan Bon).")
    }
}
