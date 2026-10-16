package id.tub.dinas

import id.tub.dinas.news.Wikisource
import org.junit.Assert.*
import org.junit.Test

class WikisourceTest {
    @Test fun parse() {
        val j = """{"batchcomplete":"","query":{"search":[{"ns":0,"title":"Siti Nurbaya","snippet":"<span class=\"searchmatch\">novel</span> karya Marah Rusli"},{"title":"","snippet":"x"}]}}"""
        val r = Wikisource.parse(j)
        assertEquals(1, r.size)
        assertEquals("Siti Nurbaya", r[0].judul)
        assertEquals("https://id.wikisource.org/wiki/Siti_Nurbaya", r[0].link)
        assertTrue(r[0].ringkas.startsWith("novel karya"))
    }
    @Test fun rusak() { assertTrue(Wikisource.parse("[]").isEmpty()) }
}
