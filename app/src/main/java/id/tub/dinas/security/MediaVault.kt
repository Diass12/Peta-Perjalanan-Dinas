package id.tub.dinas.security

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * Penyimpanan berkas terenkripsi di direktori privat. Database hanya menyimpan path relatif.
 * Tulis dilakukan ke berkas sementara lalu di-rename supaya tidak ada berkas setengah jadi.
 */
class MediaVault(private val root: File, private val key: ByteArray) {
    init { root.mkdirs() }

    private fun resolve(rel: String): File {
        val f = File(root, rel).canonicalFile
        val base = root.canonicalFile
        if (!f.path.startsWith(base.path + File.separator)) throw IOException("Path di luar penyimpanan")
        return f
    }

    /** Menyimpan byte dan mengembalikan path relatif. */
    fun save(plain: ByteArray, folder: String, ext: String): String {
        val safeExt = ext.lowercase().filter { it.isLetterOrDigit() }.take(8).ifEmpty { "bin" }
        val rel = "$folder/${UUID.randomUUID()}.$safeExt.enc"
        val target = resolve(rel)
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, target.name + ".tmp")
        try {
            tmp.outputStream().use { ChunkedCrypto.encrypt(key, plain.inputStream(), it) }
            if (!tmp.renameTo(target)) throw IOException("Gagal menyimpan berkas")
        } finally {
            if (tmp.exists()) tmp.delete()
        }
        return rel
    }

    fun saveStream(input: java.io.InputStream, folder: String, ext: String, maxBytes: Long): Pair<String, Long> {
        val safeExt = ext.lowercase().filter { it.isLetterOrDigit() }.take(8).ifEmpty { "bin" }
        val rel = "$folder/${UUID.randomUUID()}.$safeExt.enc"
        val target = resolve(rel)
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, target.name + ".tmp")
        var total = 0L
        try {
            val limited = object : java.io.InputStream() {
                override fun read(): Int { val b = input.read(); if (b >= 0 && ++total > maxBytes) throw IOException("Berkas terlalu besar"); return b }
                override fun read(b: ByteArray, off: Int, len: Int): Int {
                    val n = input.read(b, off, len)
                    if (n > 0) { total += n; if (total > maxBytes) throw IOException("Berkas terlalu besar") }
                    return n
                }
            }
            tmp.outputStream().use { ChunkedCrypto.encrypt(key, limited, it) }
            if (!tmp.renameTo(target)) throw IOException("Gagal menyimpan berkas")
        } finally {
            if (tmp.exists()) tmp.delete()
        }
        return rel to total
    }

    /** Menyimpan byte pada path relatif tertentu (dipakai saat memulihkan cadangan). */
    fun saveAt(plain: ByteArray, rel: String): String {
        val target = resolve(rel)
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, target.name + ".tmp")
        try {
            tmp.outputStream().use { ChunkedCrypto.encrypt(key, plain.inputStream(), it) }
            if (!tmp.renameTo(target)) { target.delete(); if (!tmp.renameTo(target)) throw IOException("Gagal menyimpan berkas") }
        } finally { if (tmp.exists()) tmp.delete() }
        return rel
    }

    /** Mendekripsi langsung ke stream (untuk berkas besar, tanpa memuat semuanya ke memori). Stream tujuan tidak ditutup. */
    fun readTo(rel: String, out: java.io.OutputStream) {
        resolve(rel).inputStream().use { ChunkedCrypto.decrypt(key, it, out) }
    }

    /** Menyimpan dari stream ke path relatif tertentu (pemulihan cadangan berkas besar). Mengembalikan jumlah byte. */
    fun saveStreamAt(input: java.io.InputStream, rel: String, maxBytes: Long): Long {
        val target = resolve(rel)
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, target.name + ".tmp")
        var total = 0L
        try {
            val limited = object : java.io.InputStream() {
                override fun read(): Int { val b = input.read(); if (b >= 0 && ++total > maxBytes) throw IOException("Entri terlalu besar"); return b }
                override fun read(b: ByteArray, off: Int, len: Int): Int {
                    val n = input.read(b, off, len)
                    if (n > 0) { total += n; if (total > maxBytes) throw IOException("Entri terlalu besar") }
                    return n
                }
            }
            tmp.outputStream().use { ChunkedCrypto.encrypt(key, limited, it) }
            if (!tmp.renameTo(target)) { target.delete(); if (!tmp.renameTo(target)) throw IOException("Gagal menyimpan berkas") }
        } finally { if (tmp.exists()) tmp.delete() }
        return total
    }

    fun read(rel: String): ByteArray {
        val o = ByteArrayOutputStream()
        resolve(rel).inputStream().use { ChunkedCrypto.decrypt(key, it, o) }
        return o.toByteArray()
    }

    /** Mendekripsi ke berkas sementara (untuk PdfRenderer yang butuh berkas biasa). Hapus setelah dipakai. */
    fun decryptToTemp(rel: String, tempDir: File, suffix: String): File {
        tempDir.mkdirs()
        val out = File.createTempFile("tmp_", suffix, tempDir)
        try {
            resolve(rel).inputStream().use { i -> out.outputStream().use { ChunkedCrypto.decrypt(key, i, it) } }
        } catch (e: Exception) { out.delete(); throw e }
        return out
    }

    fun delete(rel: String) { runCatching { resolve(rel).delete() } }

    fun exists(rel: String): Boolean = runCatching { resolve(rel).isFile }.getOrDefault(false)

    /** Menghapus berkas yang tidak lagi dirujuk database. Mengembalikan jumlah berkas yang dihapus. */
    fun deleteOrphans(referenced: Set<String>): Int {
        var n = 0
        root.walkTopDown().filter { it.isFile }.forEach { f ->
            val rel = f.relativeTo(root).path.replace(File.separatorChar, '/')
            if (rel !in referenced) { if (f.delete()) n++ }
        }
        return n
    }
}
