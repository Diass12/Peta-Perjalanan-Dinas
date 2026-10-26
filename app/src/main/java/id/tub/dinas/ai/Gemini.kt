package id.tub.dinas.ai

import android.util.Base64
import id.tub.dinas.data.backup.MiniJson
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Hasil analisis satu foto aset oleh Gemini. */
class AiHasil(val nama: String, val jenis: String, val kode: List<String>, val barcode: String, val catatan: String)

/**
 * Klien Gemini (Google AI Studio, kunci gratis). Foto DIKIRIM ke server Google hanya saat tombol dipakai;
 * kunci disimpan di perangkat dan dikirim lewat header, bukan di URL.
 */
object Gemini {
    private const val BASE = "https://generativelanguage.googleapis.com/v1beta"

    private const val PROMPT = "Foto ini adalah barang/aset perusahaan, kadang dengan stiker barcode/QR atau label kode aset (contoh pola TUBOE250018, TUBME240003, BAUME2600001). " +
        "Jawab HANYA JSON: {\"nama\":\"nama barang singkat dalam bahasa Indonesia, sebut warna/merek bila terlihat\"," +
        "\"jenis\":\"jenis barang, mis. Genset, Laptop, Kendaraan, Meja\",\"kode\":[\"semua kode aset yang terbaca di stiker/label, huruf besar tanpa spasi\"]," +
        "\"barcode\":\"tagged bila ada stiker kode aset terbaca, untagged bila ada stiker tetapi kode tidak terbaca, none bila tidak ada stiker\"," +
        "\"catatan\":\"kondisi atau keterangan penting singkat, boleh kosong\"}. Jangan menebak kode yang tidak terlihat jelas."

    fun parseHasil(body: String): AiHasil {
        val root = MiniJson.parse(body) as? Map<*, *> ?: throw IOException("Balasan tidak dikenali")
        (root["error"] as? Map<*, *>)?.let { throw IOException((it["message"] as? String) ?: "Kesalahan dari Gemini") }
        val parts = (((root["candidates"] as? List<*>)?.firstOrNull() as? Map<*, *>)?.get("content") as? Map<*, *>)?.get("parts") as? List<*>
        val text = parts?.mapNotNull { (it as? Map<*, *>)?.get("text") as? String }?.joinToString("")?.trim()
            ?: throw IOException("Gemini tidak memberi jawaban (foto mungkin diblokir)")
        val clean = text.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val j = MiniJson.parse(clean) as? Map<*, *> ?: throw IOException("Jawaban Gemini bukan JSON")
        val kode = (j["kode"] as? List<*>).orEmpty().mapNotNull { it as? String }
            .map { it.uppercase().replace(Regex("\\s+"), "") }.filter { it.length in 6..20 }.distinct()
        return AiHasil(
            nama = (j["nama"] as? String).orEmpty().trim(), jenis = (j["jenis"] as? String).orEmpty().trim(),
            kode = kode, barcode = (j["barcode"] as? String).orEmpty().lowercase(), catatan = (j["catatan"] as? String).orEmpty().trim(),
        )
    }

    fun parseModel(body: String): List<String> {
        val root = MiniJson.parse(body) as? Map<*, *> ?: return emptyList()
        (root["error"] as? Map<*, *>)?.let { throw IOException((it["message"] as? String) ?: "Kunci ditolak") }
        return (root["models"] as? List<*>).orEmpty().mapNotNull { m ->
            val mm = m as? Map<*, *> ?: return@mapNotNull null
            val ok = (mm["supportedGenerationMethods"] as? List<*>)?.contains("generateContent") == true
            (mm["name"] as? String)?.removePrefix("models/")?.takeIf { ok && it.startsWith("gemini") }
        }.sorted()
    }

    /** Gangguan sementara di sisi Gemini (sibuk/habis waktu); aman dicoba ulang. */
    class Sibuk(msg: String) : IOException(msg)

    private fun call(url: String, key: String, post: String?): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 15000; c.readTimeout = 45000
            c.setRequestProperty("x-goog-api-key", key.trim())
            if (post != null) { c.requestMethod = "POST"; c.doOutput = true; c.setRequestProperty("Content-Type", "application/json"); c.outputStream.use { it.write(post.toByteArray()) } }
            val code = c.responseCode
            val body = (if (code in 200..299) c.inputStream else c.errorStream)?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
            if (code !in 200..299) {
                val msg = runCatching { ((MiniJson.parse(body) as Map<*, *>)["error"] as Map<*, *>)["message"] as String }.getOrNull()
                throw IOException(when (code) { 400, 403 -> "Kunci/model ditolak ($code): ${msg ?: ""}"; 429 -> "Kuota gratis habis, coba lagi nanti"; 500, 502, 503, 504 -> throw Sibuk("Server Gemini sedang sibuk ($code)"); else -> "Gemini error $code ${msg ?: ""}" })
            }
            return body
        } finally { c.disconnect() }
    }

    fun analisis(key: String, model: String, jpeg: ByteArray): AiHasil = kirimFoto(key, model, PROMPT, jpeg, ::parseHasil)

    private const val PROMPT_BON = "Ini foto bon/struk/nota/kwitansi pembayaran (bisa dicetak atau tulisan tangan). " +
        "Jawab HANYA JSON: {\"total\":angka rupiah total akhir yang harus dibayar, hanya digit tanpa titik, atau null bila tidak jelas," +
        "\"tanggal\":\"yyyy-MM-dd\" atau null,\"toko\":\"nama toko/penyedia/maskapai/hotel\"," +
        "\"uraian\":\"ringkasan singkat barang atau jasa yang dibayar, maksimal 80 huruf, bahasa Indonesia\"," +
        "\"kategori\":\"salah satu dari TIKET, TRANSPORTASI, KAPAL, HOTEL, MAKAN_PAGI, MAKAN_SIANG, MAKAN_MALAM, UANG_SAKU, LAUNDRY, LAINNYA\"," +
        "\"rincian\":[{\"item\":\"nama\",\"jumlah\":angka}]}. Bila ada rincian lalu total, pakai total. Jangan menebak angka yang tidak terlihat jelas."

    /** Hasil baca bon oleh Gemini (mendukung tulisan tangan). */
    fun bon(key: String, model: String, jpeg: ByteArray): id.tub.dinas.um.BonParse.Hasil = kirimFoto(key, model, PROMPT_BON, jpeg) { body ->
        val j = jsonJawaban(body)
        fun angka(v: Any?): Long? = when (v) { is Number -> v.toLong(); is String -> v.filter { it.isDigit() }.toLongOrNull(); else -> null }
        val total = angka(j["total"])?.takeIf { it > 0 }
        val rincian = (j["rincian"] as? List<*>).orEmpty().mapNotNull { angka((it as? Map<*, *>)?.get("jumlah")) }.filter { it > 0 }
        val kandidat = (listOfNotNull(total) + listOf(rincian.sum()).filter { it > 0 }).distinct()
        val tgl = (j["tanggal"] as? String)?.trim()?.takeIf { Regex("""\d{4}-\d{2}-\d{2}""").matches(it) }
        id.tub.dinas.um.BonParse.Hasil(total ?: kandidat.firstOrNull(), kandidat, tgl, (j["toko"] as? String)?.trim()?.takeIf { it.isNotEmpty() },
            (j["uraian"] as? String)?.trim()?.takeIf { it.isNotEmpty() }, id.tub.dinas.domain.BonKategori.entries.firstOrNull { it.name == (j["kategori"] as? String)?.trim()?.uppercase() })
    }

    private fun jsonJawaban(body: String): Map<*, *> {
        val root = MiniJson.parse(body) as? Map<*, *> ?: throw IOException("Balasan tidak dikenali")
        (root["error"] as? Map<*, *>)?.let { throw IOException((it["message"] as? String) ?: "Kesalahan dari Gemini") }
        val parts = (((root["candidates"] as? List<*>)?.firstOrNull() as? Map<*, *>)?.get("content") as? Map<*, *>)?.get("parts") as? List<*>
        val text = parts?.mapNotNull { (it as? Map<*, *>)?.get("text") as? String }?.joinToString("")?.trim()
            ?: throw IOException("Gemini tidak memberi jawaban (foto mungkin diblokir)")
        val clean = text.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        return MiniJson.parse(clean) as? Map<*, *> ?: throw IOException("Jawaban Gemini bukan JSON")
    }

    private fun <T> kirimFoto(key: String, model: String, prompt: String, jpeg: ByteArray, parse: (String) -> T): T {
        val req = mapOf(
            "contents" to listOf(mapOf("parts" to listOf(
                mapOf("text" to prompt),
                mapOf("inline_data" to mapOf("mime_type" to "image/jpeg", "data" to Base64.encodeToString(jpeg, Base64.NO_WRAP))),
            ))),
            "generationConfig" to mapOf("responseMimeType" to "application/json", "temperature" to 0.1),
        )
        val utama = model.trim().ifBlank { "gemini-flash-latest" }
        // Coba model pilihan 2x (jeda 2 dtk), lalu model ringan sebagai cadangan; gangguan sementara tidak langsung gagal.
        val cadangan = "gemini-flash-lite-latest"
        val urutan = if (utama == cadangan) listOf(utama, utama) else listOf(utama, utama, cadangan)
        val body = MiniJson.write(req)
        var terakhir: IOException? = null
        for ((i, m) in urutan.withIndex()) {
            try { return parse(call("$BASE/models/$m:generateContent", key, body)) }
            catch (e: java.net.SocketTimeoutException) { terakhir = Sibuk("Koneksi ke Gemini terlalu lama. Cek sinyal lalu coba lagi.") }
            catch (e: Sibuk) { terakhir = e }
            if (i < urutan.lastIndex) Thread.sleep(2000)
        }
        throw terakhir ?: IOException("Analisis gagal")
    }

    fun modelTersedia(key: String): List<String> = parseModel(call("$BASE/models?pageSize=100", key, null))
}
