package id.tub.dinas

import id.tub.dinas.security.LisensiKode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LisensiTest {
    private val ho = "djF8SE98VEVTVC1ERVZJQ0V8MA.MEYCIQDMcK6I1InZqKX9rno4pKZ9FrbjMo3XPfHUya8WDA8fEgIhAPNijg1zcL_hcSP5Ffn91qGxgmuskRnF8rlL6lNPo-z0"
    private val semua = "djF8SE98Knww.MEQCIFDpDQ4xVBlPLmTFsHGj72XwBQ1r_ohUZoHTOWDV2L8_AiAoKf_s_g1PC7UufDFVBYNMHqBmNEYsKhXZpdXOdqS4Jg"

    @Test fun kodeSahUntukPerangkatnya() { val r = LisensiKode.periksa(ho, "test-device", 20261021); assertTrue(r.ok); assertEquals("HO", r.peran) }
    @Test fun perangkatLainDitolak() = assertFalse(LisensiKode.periksa(ho, "LAIN", 20261021).ok)
    @Test fun kodeSemuaPerangkat() = assertTrue(LisensiKode.periksa(semua, "APA-SAJA", 20261021).ok)
    @Test fun kodeDiubahDitolak() = assertFalse(LisensiKode.periksa(ho.replace("SE98", "SE99"), "TEST-DEVICE", 20261021).ok)
    @Test fun sampahDitolak() = assertFalse(LisensiKode.periksa("abc", "X", 20261021).ok)
}
