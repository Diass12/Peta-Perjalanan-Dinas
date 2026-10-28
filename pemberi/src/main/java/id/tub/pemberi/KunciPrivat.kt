package id.tub.pemberi

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyFactory
import java.security.KeyStore
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Penyimpanan kunci privat lisensi. Berkas lisensi_private.pem diimpor SEKALI, lalu disimpan terenkripsi AES-GCM
 * dengan kunci AndroidKeyStore yang hanya bisa dipakai setelah sidik jari/PIN perangkat (berlaku 10 detik).
 */
object KunciPrivat {
    private const val ALIAS = "pemberi_lisensi_aes"
    private const val PUB = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEu1QiBbGrcgU+j0si4ncmsKqbKe4p5TesI8/2YnHjbVu/aVeZ7lEqLYu4HU40w7B2bAiCzywUhjYZadkkGsBcgA=="

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("pemberi", Context.MODE_PRIVATE)

    fun ada(ctx: Context): Boolean = prefs(ctx).contains("ct")

    private fun kunciAes(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val b = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(false)
        if (Build.VERSION.SDK_INT >= 30) b.setUserAuthenticationParameters(10, KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL)
        else @Suppress("DEPRECATION") b.setUserAuthenticationValidityDurationSeconds(10)
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply { init(b.build()) }.generateKey()
    }

    /** Mengurai PEM PKCS#8 menjadi byte. Melempar bila bukan kunci EC PKCS#8. */
    fun pemKeByte(pem: String): ByteArray {
        val isi = pem.replace(Regex("-----[^-]+-----"), "").replace(Regex("\\s+"), "")
        require(isi.isNotEmpty()) { "Berkas kosong" }
        require(!pem.contains("EC PRIVATE KEY")) { "Format kunci bukan PKCS#8 (lisensi_private.pem)" }
        return Base64.getDecoder().decode(isi)
    }

    /** Memastikan kunci privat cocok dengan kunci publik yang tertanam di aplikasi staff. */
    private fun cocok(pkcs8: ByteArray): Boolean {
        val priv = KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(pkcs8))
        val uji = "uji".toByteArray()
        val sig = Signature.getInstance("SHA256withECDSA").apply { initSign(priv); update(uji) }.sign()
        val pub = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(PUB)))
        return Signature.getInstance("SHA256withECDSA").apply { initVerify(pub); update(uji) }.verify(sig)
    }

    /** Impor + enkripsi. Panggil SESAAT setelah autentikasi berhasil. Mengembalikan pesan galat atau null bila sukses. */
    fun impor(ctx: Context, pem: String): String? = try {
        val bytes = pemKeByte(pem)
        if (!cocok(bytes)) "Kunci ini TIDAK cocok dengan kunci publik aplikasi staff."
        else {
            val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, kunciAes()) }
            val ct = c.doFinal(bytes)
            bytes.fill(0)
            prefs(ctx).edit().putString("iv", Base64.getEncoder().encodeToString(c.iv)).putString("ct", Base64.getEncoder().encodeToString(ct)).apply()
            null
        }
    } catch (e: Exception) { "Berkas kunci tidak valid: ${e.message ?: e.javaClass.simpleName}" }

    fun hapus(ctx: Context) {
        prefs(ctx).edit().clear().apply()
        runCatching { KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(ALIAS) }
    }

    /** Menandatangani [payload]; hasil = base64url(payload) + "." + base64url(tanda tangan DER). Panggil setelah autentikasi. */
    fun terbitkan(ctx: Context, payload: String): String {
        val p = prefs(ctx)
        val iv = Base64.getDecoder().decode(p.getString("iv", "")); val ct = Base64.getDecoder().decode(p.getString("ct", ""))
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, kunciAes(), GCMParameterSpec(128, iv)) }
        val bytes = c.doFinal(ct)
        try {
            val priv = KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(bytes))
            val data = payload.toByteArray(Charsets.UTF_8)
            val sig = Signature.getInstance("SHA256withECDSA").apply { initSign(priv); update(data) }.sign()
            val enc = Base64.getUrlEncoder().withoutPadding()
            return enc.encodeToString(data) + "." + enc.encodeToString(sig)
        } finally { bytes.fill(0) }
    }
}
