package id.tub.dinas

import id.tub.dinas.news.*
import org.junit.Assert.*
import org.junit.Test

class NewsFeedTest {
    @Test fun rss() {
        val x = """<?xml version="1.0"?><rss version="2.0"><channel><title>T</title>
            <item><title>Rupiah menguat</title><link>https://a.test/1</link><description><![CDATA[<p>Isi &amp; ringkas</p>]]></description><pubDate>Mon, 1 Jan 2026 10:00:00 +0700</pubDate></item>
            <item><title></title><link>https://a.test/2</link></item></channel></rss>"""
        val r = NewsFeed.parse(x.toByteArray(), Sumber("A", "https://a.test/rss"))
        assertEquals(1, r.size)
        assertEquals("Rupiah menguat", r[0].judul); assertEquals("Isi & ringkas", r[0].ringkas); assertEquals("https://a.test/1", r[0].link)
    }
    @Test fun atom() {
        val x = """<feed xmlns="http://www.w3.org/2005/Atom"><entry><title>Aturan baru</title><link href="https://b.test/x"/><summary>Ringkas</summary><updated>2026-01-01</updated></entry></feed>"""
        val r = NewsFeed.parse(x.toByteArray(), Sumber("B", "https://b.test/a"))
        assertEquals("https://b.test/x", r.single().link)
    }
    @Test fun bmkg() {
        val x = """<Infogempa><gempa><Tanggal>01 Jan 2026</Tanggal><Jam>10:00:00 WIB</Jam><DateTime>2026-01-01T03:00:00+00:00</DateTime><Magnitude>5.1</Magnitude><Kedalaman>10 km</Kedalaman><Wilayah>Pusat gempa di laut</Wilayah><Potensi>Tidak berpotensi tsunami</Potensi></gempa></Infogempa>"""
        val r = NewsFeed.parse(x.toByteArray(), Sumber("G", "https://d.test/g.xml", Tipe.BMKG_GEMPA))
        assertTrue(r.single().judul.startsWith("Gempa M5.1"))
    }
    @Test fun extra() {
        val l = NewsFeed.parseExtra("Satu|https://x.test/rss\nsalah|http://x.test\nhanyaurl")
        assertEquals(1, l.size)
    }
    @Test fun doctypeDitolak() {
        val x = """<!DOCTYPE a [<!ENTITY e SYSTEM "file:///etc/passwd">]><rss><channel><item><title>&e;</title><link>https://a.test</link></item></channel></rss>"""
        assertTrue(runCatching { NewsFeed.parse(x.toByteArray(), Sumber("A", "https://a.test")) }.isFailure)
    }
}
