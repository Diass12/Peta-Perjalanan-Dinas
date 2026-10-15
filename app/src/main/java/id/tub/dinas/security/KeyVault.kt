package id.tub.dinas.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Kunci acak untuk database dan media dibungkus dengan kunci AES yang tersimpan di Android Keystore
 * (tidak bisa diekspor). Kunci terbungkus disimpan di SharedPreferences privat.
 */
class KeyVault(ctx: Context) {
    private val prefs = ctx.applicationContext.getSharedPreferences("kv", Context.MODE_PRIVATE)
    private val alias = "dinas_master_v1"

    private fun master(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        val g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        g.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return g.generateKey()
    }

    @Synchronized
    private fun getOrCreate(name: String): ByteArray {
        prefs.getString(name, null)?.let { stored ->
            val raw = Base64.decode(stored, Base64.NO_WRAP)
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, master(), GCMParameterSpec(128, raw.copyOfRange(0, 12)))
            return c.doFinal(raw, 12, raw.size - 12)
        }
        val k = ChunkedCrypto.newKey()
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, master())
        val ct = c.doFinal(k)
        val blob = c.iv + ct
        // commit() agar kunci pasti tertulis sebelum dipakai
        check(prefs.edit().putString(name, Base64.encodeToString(blob, Base64.NO_WRAP)).commit()) { "Gagal menyimpan kunci" }
        return k
    }

    fun dbKey(): ByteArray = getOrCreate("db")
    fun mediaKey(): ByteArray = getOrCreate("media")
}
