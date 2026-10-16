package id.tub.dinas

import id.tub.dinas.i18n.AppLang
import id.tub.dinas.i18n.Dict
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class DictTest {
    private fun dict(): Dict {
        val f = listOf(File("src/main/assets/i18n.tsv"), File("app/src/main/assets/i18n.tsv")).first { it.isFile }
        return Dict(f.readText())
    }
    @Test fun semuaBarisLimaKolom() {
        val f = listOf(File("src/main/assets/i18n.tsv"), File("app/src/main/assets/i18n.tsv")).first { it.isFile }
        f.readLines().filter { it.isNotBlank() }.forEachIndexed { i, l -> assertEquals("baris ${i + 1}: $l", 5, l.split('|').size); l.split('|').forEach { assertTrue("kosong di baris ${i + 1}", it.isNotBlank()) } }
    }
    @Test fun persis() {
        val d = dict()
        assertEquals("Home", d.terjemah("Beranda", AppLang.EN))
        assertEquals("首页", d.terjemah("Beranda", AppLang.ZH))
        assertEquals("ホーム", d.terjemah("Beranda", AppLang.JA))
        assertEquals("홈", d.terjemah("Beranda", AppLang.KO))
        assertEquals("Beranda", d.terjemah("Beranda", AppLang.ID))
    }
    @Test fun spasiDiAwalAkhir() { assertEquals(" Cancel ", dict().terjemah(" Batal ", AppLang.EN)) }
    @Test fun dinamis() {
        val d = dict()
        assertEquals("Finding 3", d.terjemah("Temuan 3", AppLang.EN))
        assertTrue(d.terjemah("12 titik", AppLang.EN).startsWith("12 point"))
    }
    @Test fun dataPenggunaTidakBerubah() {
        assertEquals("Dinas Halmahera", dict().terjemah("Dinas Halmahera", AppLang.EN))
    }
}
