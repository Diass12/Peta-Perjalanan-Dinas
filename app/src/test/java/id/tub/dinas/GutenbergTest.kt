package id.tub.dinas

import id.tub.dinas.news.Gutenberg
import org.junit.Assert.*
import org.junit.Test

class GutenbergTest {
    @Test fun parse() {
        val j = """{"count":1,"results":[{"id":1342,"title":"Pride and Prejudice","authors":[{"name":"Austen, Jane"}]},{"id":2,"title":""}]}"""
        val r = Gutenberg.parse(j)
        assertEquals(1, r.size)
        assertEquals("https://www.gutenberg.org/ebooks/1342", r[0].link)
        assertEquals("Austen, Jane", r[0].ringkas)
    }
}
