package id.tub.dinas

import id.tub.dinas.data.backup.BackupCodec
import id.tub.dinas.data.backup.MiniJson
import id.tub.dinas.domain.*
import id.tub.dinas.export.ExcelExporter
import id.tub.dinas.security.ChunkedCrypto
import id.tub.dinas.security.MediaVault
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException

class LogicTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun coordFormats() {
        assertEquals(1.5, CoordParser.parse("https://www.google.com/maps/@1.5,127.8,15z")!!.lat, 1e-9)
        assertEquals(127.8, CoordParser.parse("1.5, 127.8")!!.lng, 1e-9)
        assertNull(CoordParser.parse("91, 200"))
        assertNull(CoordParser.parse("bukan koordinat"))
    }

    @Test fun summaryZeroTemuanTidakError() {
        val s = SummaryCalc.calc(emptyList())
        assertEquals(0, s.total)
        assertTrue(SummaryCalc.sentence(s, 3).contains("3 titik"))
    }

    @Test fun cryptoRoundTripAndTamper() {
        val k = ChunkedCrypto.newKey()
        for (n in listOf(0, 1, 65536, 65537, 200_000)) {
            val d = ByteArray(n) { it.toByte() }
            assertArrayEquals(d, ChunkedCrypto.decryptBytes(k, ChunkedCrypto.encryptBytes(k, d)))
        }
        val e = ChunkedCrypto.encryptBytes(k, ByteArray(200_000))
        assertThrows(IOException::class.java) { ChunkedCrypto.decryptBytes(ChunkedCrypto.newKey(), e) }
        assertThrows(IOException::class.java) { ChunkedCrypto.decryptBytes(k, e.copyOf(e.size - 70_000)) }
    }

    @Test fun vaultBlocksTraversal() {
        val v = MediaVault(tmp.newFolder("v"), ChunkedCrypto.newKey())
        assertThrows(IOException::class.java) { v.read("../../etc/passwd") }
    }

    private fun snap(photoPath: String) = DinasSnapshot(
        DinasModel("d1", "Dinas", 1L, 2L, "cat"),
        listOf(TitikModel("t1", "Titik", "", GeoPoint(1.5, 127.8, 5, "gps"), 10L, 0)),
        listOf(TemuanModel("m1", "Judul", "K", "uraian", mapOf(Lens.ASET to "a", Lens.OPS to "", Lens.ADM to null), "x", "y", "z", Status.PROSES, null, 0)),
        listOf(FotoModel("f1", OwnerType.TEMUAN, "m1", photoPath, "cap", 0, 100, 50)),
        listOf(LampiranModel("l1", "m1", "a.pdf", "lampiran/x.pdf.enc", 3, 0)),
        listOf(PoinModel("p1", "sistem", 0)),
    )

    @Test fun backupRoundTrip() {
        val v1 = MediaVault(tmp.newFolder("a"), ChunkedCrypto.newKey())
        val p = v1.save(ByteArray(1000) { it.toByte() }, "foto", "jpg")
        val s = snap(p)
        for (pass in listOf<CharArray?>(null, "rahasia".toCharArray())) {
            val o = ByteArrayOutputStream(); BackupCodec.write(listOf(s), v1, o, pass)
            val v2 = MediaVault(tmp.newFolder(), ChunkedCrypto.newKey())
            val r = BackupCodec.read(ByteArrayInputStream(o.toByteArray()), v2, pass)
            assertEquals(listOf(s), r.snapshots)
            assertEquals(1000, v2.read(p).size)
        }
    }

    @Test fun excelProducesZip() {
        val o = ByteArrayOutputStream()
        ExcelExporter.export(snap("foto/none.jpg.enc"), { null }, o, java.time.ZoneId.of("Asia/Jakarta"))
        val b = o.toByteArray()
        assertTrue(b.size > 1000 && b[0] == 'P'.code.toByte() && b[1] == 'K'.code.toByte())
    }

    @Test fun jsonRoundTrip() {
        val m = mapOf("a" to listOf(1L, "x\n\"", null, true))
        assertEquals(m, MiniJson.parse(MiniJson.write(m)))
    }
}
