package id.tub.dinas.security

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/** Hasil pemeriksaan kode lisensi. */
class HasilLisensi(val ok: Boolean, val peran: String, val pesan: String)

/**
 * Pemeriksa kode lisensi offline. Kode = base64url(payload) + "." + base64url(tanda tangan ECDSA P-256 / SHA-256).
 * payload = "v1|PERAN|ID-PERANGKAT atau *|YYYYMMDD atau 0". Kunci publik ada di aplikasi; kunci privat hanya dipegang pemilik.
 */
object LisensiKode {
    private const val PUB = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEu1QiBbGrcgU+j0si4ncmsKqbKe4p5TesI8/2YnHjbVu/aVeZ7lEqLYu4HU40w7B2bAiCzywUhjYZadkkGsBcgA=="

    fun periksa(kode: String, idPerangkat: String, hariIni: Int, pub: String = PUB): HasilLisensi {
        val t = kode.trim().replace(Regex("\\s+"), "")
        val i = t.indexOf('.')
        if (i <= 0) return HasilLisensi(false, "", "Format kode tidak dikenali")
        return try {
            val dec = Base64.getUrlDecoder()
            val payload = dec.decode(t.substring(0, i)); val sig = dec.decode(t.substring(i + 1))
            val key = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(pub)))
            val v = Signature.getInstance("SHA256withECDSA").apply { initVerify(key); update(payload) }
            if (!v.verify(sig)) return HasilLisensi(false, "", "Kode tidak sah")
            val p = String(payload, Charsets.UTF_8).split("|")
            if (p.size != 4 || p[0] != "v1") return HasilLisensi(false, "", "Versi kode tidak dikenali")
            if (p[2] != "*" && !p[2].equals(idPerangkat, true)) return HasilLisensi(false, "", "Kode ini untuk perangkat lain")
            val exp = p[3].toIntOrNull() ?: 0
            if (exp != 0 && hariIni > exp) return HasilLisensi(false, "", "Lisensi sudah berakhir ($exp)")
            HasilLisensi(true, p[1].uppercase(), if (exp == 0) "tanpa masa berlaku" else "berlaku sampai $exp")
        } catch (e: Exception) { HasilLisensi(false, "", "Kode tidak dapat dibaca") }
    }
}

/** Peran aktif: Staff Site (NBV disembunyikan di tampilan, ekspor, dan impor) atau Staff HO (lengkap, perlu kode lisensi). */
object Lisensi {
    var ho by mutableStateOf(false)
    var keterangan by mutableStateOf("")

    fun idPerangkat(ctx: android.content.Context): String {
        val raw = android.provider.Settings.Secure.getString(ctx.contentResolver, android.provider.Settings.Secure.ANDROID_ID).orEmpty()
        return java.security.MessageDigest.getInstance("SHA-256").digest(("dinas$raw").toByteArray()).take(6).joinToString("") { "%02X".format(it) }.chunked(4).joinToString("-")
    }

    private fun hari(): Int = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date()).toInt()

    /** Memeriksa ulang kode tersimpan; tanpa kode sah = Staff Site. Mengembalikan hasil periksa. */
    fun muat(ctx: android.content.Context, kode: String): HasilLisensi {
        if (kode.isBlank()) { ho = false; keterangan = ""; return HasilLisensi(false, "", "") }
        val r = LisensiKode.periksa(kode, idPerangkat(ctx), hari())
        ho = r.ok && r.peran == "HO"
        keterangan = if (r.ok) r.pesan else r.pesan
        return r
    }
}
