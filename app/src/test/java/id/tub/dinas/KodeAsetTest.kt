package id.tub.dinas

import id.tub.dinas.domain.*
import org.junit.Assert.*
import org.junit.Test

class KodeAsetTest {
    private val daftar = { k: String -> listOf(k) }

    @Test fun ekstrak() {
        assertEquals(listOf("TUBOE250018"), KodeAset.ekstrak("Asset No: TUBOE250018"))
        assertEquals(listOf("TUBOE250018"), KodeAset.ekstrak("TU8OE25OO18"))
        assertEquals(listOf("TUBOE250018", "TUBME260020"), KodeAset.ekstrak("TUBOE250018\nTUBME260020"))
        assertEquals(listOf("TUBOE25001"), KodeAset.ekstrak("TUBOE25001"))
        assertTrue(KodeAset.ekstrak("hello world 12345").isEmpty())
    }

    @Test fun validasi() {
        assertNull(KodeAset.validasi("TUBOE2500"))
        assertEquals("TUBOE250018", KodeAset.validasi("TUBOE250018"))
    }

    @Test fun cocokkan() {
        val reg = listOf("TUBOE250018")
        assertFalse(KodeAset.cocokkan(KodeAset.ekstrak("TUBOE250O18")[0], reg) { it }.fuzzy)
        assertEquals("TUBOE250018", KodeAset.cocokkan(KodeAset.ekstrak("TUBOE250O18")[0], reg) { it }.aset)
        val f = KodeAset.cocokkan("TUBOE250O18", reg) { it }
        assertTrue(f.fuzzy); assertEquals("TUBOE250018", f.aset)
        val f2 = KodeAset.cocokkan("TUBOE250019", reg) { it }
        assertTrue(f2.fuzzy)
        val none = KodeAset.cocokkan("TUBOE250099", reg) { it }
        assertNull(none.aset); assertFalse(none.fuzzy)
        // dua kandidat sama dekat -> tidak ditebak
        assertNull(KodeAset.cocokkan("TUBOE250019", listOf("TUBOE250018", "TUBOE250010")) { it }.aset)
    }

    @Test fun kelasDanKategori() {
        assertEquals("OE", KodeAset.kodeKelas("TUBOE250018"))
        assertEquals("Office Equipment", KodeAset.kelasDariKode("TUBOE250018")?.nama)
        assertEquals("machine", KodeAset.tebakKategori("TUBME260020"))
        assertEquals("vehicle", KodeAset.tebakKategori("", "Toyota Hilux pickup"))
    }

    @Test fun statusBarcode() {
        // dibaca & sama -> tagged
        assertEquals(StatusBarcode.TAGGED, KodeAset.hitungStatusBarcode("TUBOE250018", listOf("TUBOE250018"), true, null, SumberBarcode.NONE).first)
        // kode lain yang terbaca tidak membuatnya tagged
        assertEquals(StatusBarcode.UNTAGGED, KodeAset.hitungStatusBarcode("TUBOE250018", listOf("TUBME260020"), true, null, SumberBarcode.NONE).first)
        assertEquals(StatusBarcode.NONASSET, KodeAset.hitungStatusBarcode("", emptyList(), false, null, SumberBarcode.NONE).first)
        // Excel yang sudah tagged tetap tagged
        assertEquals(StatusBarcode.TAGGED, KodeAset.hitungStatusBarcode("TUBOE250018", emptyList(), false, StatusBarcode.TAGGED, SumberBarcode.EXCEL).first)
        // manual tidak ditimpa
        assertEquals(StatusBarcode.NONASSET, KodeAset.hitungStatusBarcode("TUBOE250018", listOf("TUBOE250018"), true, StatusBarcode.NONASSET, SumberBarcode.MANUAL).first)
    }
}
