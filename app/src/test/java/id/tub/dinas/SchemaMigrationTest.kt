package id.tub.dinas

import id.tub.dinas.data.local.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Migrasi ditulis tangan, jadi dicocokkan dengan skema yang Room hasilkan sendiri (schemas/.../3.json).
 * Bila beda, Room akan menolak membuka database pengguna setelah pembaruan: tes ini mencegahnya.
 */
class SchemaMigrationTest {
    private fun schemaFile(): File {
        val rel = "schemas/id.tub.dinas.data.local.AppDatabase/3.json"
        return listOf(File(rel), File("app/$rel")).firstOrNull { it.isFile }
            ?: throw AssertionError("Skema Room 3.json tidak ditemukan; periksa room.schemaLocation")
    }

    private fun norm(s: String) = s.replace(Regex("\\s+"), " ").replace(" )", ")").trim()

    @Test fun migrasi23SamaDenganSkemaRoom() {
        val text = schemaFile().readText()
        @Suppress("UNCHECKED_CAST")
        val root = id.tub.dinas.data.backup.MiniJson.parse(text) as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val entities = ((root["database"] as Map<String, Any?>)["entities"] as List<Map<String, Any?>>)
        fun table(name: String) = entities.first { it["tableName"] == name }
        fun create(name: String) = norm((table(name)["createSql"] as String).replace("\${TABLE_NAME}", name))
        @Suppress("UNCHECKED_CAST")
        fun idx(name: String) = ((table(name)["indices"] as List<Map<String, Any?>>)).map { norm((it["createSql"] as String).replace("\${TABLE_NAME}", name)) }

        assertEquals(create("aset"), norm(AppDatabase.SQL_ASET))
        assertEquals(create("aset_foto"), norm(AppDatabase.SQL_ASET_FOTO))
        assertEquals(listOf(norm(AppDatabase.SQL_ASET_IDX)), idx("aset"))
        assertEquals(listOf(norm(AppDatabase.SQL_ASET_FOTO_IDX)), idx("aset_foto"))
        assertTrue(entities.size == 9)
    }
}
