package id.tub.dinas.security

import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Enkripsi berkas berpotongan (AES-256-GCM). Tiap potongan 64 KB punya tag sendiri,
 * nonce diturunkan dari penghitung, dan potongan terakhir ditandai di AAD sehingga
 * berkas yang terpotong atau potongannya ditukar akan ditolak.
 *
 * Format: "DNC1" | nonce-prefix(8) | { panjang(4, big-endian) | ciphertext+tag }*
 */
object ChunkedCrypto {
    const val CHUNK = 64 * 1024
    private val MAGIC = byteArrayOf('D'.code.toByte(), 'N'.code.toByte(), 'C'.code.toByte(), '1'.code.toByte())
    private const val TAG_BITS = 128
    private val random = SecureRandom()

    fun newKey(): ByteArray = ByteArray(32).also { random.nextBytes(it) }

    fun deriveKey(passphrase: CharArray, salt: ByteArray, iterations: Int = 210_000): ByteArray {
        val spec = PBEKeySpec(passphrase, salt, iterations, 256)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun cipher(mode: Int, key: ByteArray, prefix: ByteArray, counter: Int, last: Boolean): Cipher {
        val nonce = ByteArray(12)
        System.arraycopy(prefix, 0, nonce, 0, 8)
        nonce[8] = (counter ushr 24).toByte(); nonce[9] = (counter ushr 16).toByte()
        nonce[10] = (counter ushr 8).toByte(); nonce[11] = counter.toByte()
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        c.updateAAD(byteArrayOf(if (last) 1 else 0))
        return c
    }

    private fun readFully(input: InputStream, buf: ByteArray, len: Int): Int {
        var n = 0
        while (n < len) {
            val r = input.read(buf, n, len - n)
            if (r < 0) break
            n += r
        }
        return n
    }

    fun encrypt(key: ByteArray, input: InputStream, output: OutputStream) {
        require(key.size == 32) { "Kunci harus 32 byte" }
        val prefix = ByteArray(8).also { random.nextBytes(it) }
        output.write(MAGIC); output.write(prefix)
        var cur = ByteArray(CHUNK); var nxt = ByteArray(CHUNK)
        var curLen = readFully(input, cur, CHUNK)
        var counter = 0
        while (true) {
            val nxtLen = if (curLen == CHUNK) readFully(input, nxt, CHUNK) else 0
            val last = nxtLen == 0
            val ct = cipher(Cipher.ENCRYPT_MODE, key, prefix, counter, last).doFinal(cur, 0, curLen)
            output.write(byteArrayOf((ct.size ushr 24).toByte(), (ct.size ushr 16).toByte(), (ct.size ushr 8).toByte(), ct.size.toByte()))
            output.write(ct)
            if (last) break
            val t = cur; cur = nxt; nxt = t; curLen = nxtLen; counter++
        }
        output.flush()
    }

    /** Melempar IOException bila berkas rusak, terpotong, atau kunci salah. */
    fun decrypt(key: ByteArray, input: InputStream, output: OutputStream) {
        require(key.size == 32) { "Kunci harus 32 byte" }
        val head = ByteArray(12)
        if (readFully(input, head, 12) != 12) throw IOException("Berkas terenkripsi tidak lengkap")
        for (i in 0 until 4) if (head[i] != MAGIC[i]) throw IOException("Bukan berkas terenkripsi aplikasi ini")
        val prefix = head.copyOfRange(4, 12)
        var counter = 0
        val lenBuf = ByteArray(4)
        var pendingLen = readFully(input, lenBuf, 4)
        if (pendingLen != 4) throw IOException("Berkas terenkripsi terpotong")
        while (true) {
            val len = ((lenBuf[0].toInt() and 0xFF) shl 24) or ((lenBuf[1].toInt() and 0xFF) shl 16) or
                ((lenBuf[2].toInt() and 0xFF) shl 8) or (lenBuf[3].toInt() and 0xFF)
            if (len < 16 || len > CHUNK + 16) throw IOException("Panjang potongan tidak valid")
            val ct = ByteArray(len)
            if (readFully(input, ct, len) != len) throw EOFException("Berkas terenkripsi terpotong")
            val nextHdr = readFully(input, lenBuf, 4)
            val last = nextHdr == 0
            if (nextHdr in 1..3) throw IOException("Berkas terenkripsi rusak")
            try {
                output.write(cipher(Cipher.DECRYPT_MODE, key, prefix, counter, last).doFinal(ct))
            } catch (e: GeneralSecurityException) {
                throw IOException("Berkas rusak atau kunci salah", e)
            }
            if (last) break
            counter++
        }
        output.flush()
    }

    fun encryptBytes(key: ByteArray, data: ByteArray): ByteArray {
        val o = ByteArrayOutputStream(data.size + 64)
        encrypt(key, data.inputStream(), o)
        return o.toByteArray()
    }

    fun decryptBytes(key: ByteArray, data: ByteArray): ByteArray {
        val o = ByteArrayOutputStream(data.size)
        decrypt(key, data.inputStream(), o)
        return o.toByteArray()
    }

    fun looksEncrypted(file: File): Boolean = try {
        file.inputStream().use { s -> val h = ByteArray(4); readFully(s, h, 4) == 4 && h.contentEquals(MAGIC) }
    } catch (e: IOException) { false }
}
