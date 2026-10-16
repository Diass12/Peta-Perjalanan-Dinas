package id.tub.dinas

import id.tub.dinas.aset.AsetImport
import id.tub.dinas.aset.XlsxReader
import id.tub.dinas.domain.StatusBarcode
import id.tub.dinas.export.XStyle
import id.tub.dinas.export.XlsxWriter
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AsetImportTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun sampleFile(withImage: Boolean): java.io.File {
        val w = XlsxWriter()
        val sh = w.sheet("Data")
        val st = XStyle()
        sh.text(0, 1, "Physical Check", st)
        listOf("No.", "Assets Classification", "Asset Number", "Purchase Date", "Description", "Dept.", "Barcode", "Nilai Buku (IDR)")
            .forEachIndexed { i, t -> sh.text(2, i, t, st) }
        listOf("1", "Office Equipment", "tubOE250018", "2025-02-12", "Laptop", "Geologist", "Tagged", "6.283.750")
            .forEachIndexed { i, t -> sh.text(3, i, t, st) }
        listOf("2", "", "", "", "Kursi", "HSE", "Non Aset", "")
            .forEachIndexed { i, t -> if (t.isNotEmpty()) sh.text(4, i, t, st) }
        if (withImage) sh.image(byteArrayOf(1, 2, 3, 4), "jpeg", 3, 8, 0, 0, 100, 100)
        val f = tmp.newFile()
        f.outputStream().use { w.write(it) }
        return f
    }

    @Test fun bacaXlsxDanPetakanKolom() {
        val plan = AsetImport.planXlsx(sampleFile(false))
        assertEquals("Data", plan.sheet)
        assertEquals(2, plan.rows.size)
        val a = plan.rows[0].first
        assertEquals("TUBOE250018", a.code); assertEquals("Office Equipment", a.classif); assertEquals("office", a.category)
        assertEquals("2025-02-12", a.acq); assertEquals("Geologist", a.dept); assertEquals(StatusBarcode.TAGGED, a.barcode)
        assertEquals(6283750.0, a.nbv!!, 0.0)
        val b = plan.rows[1].first
        assertEquals("", b.code); assertEquals("Kursi", b.name); assertEquals(StatusBarcode.NONASSET, b.barcode)
        assertEquals("office", b.category)
    }

    @Test fun gambarTertanamDipetakanKeBaris() {
        val f = sampleFile(true)
        val plan = AsetImport.planXlsx(f)
        assertEquals(1, plan.rows[0].second)
        assertEquals(0, plan.rows[1].second)
        val bytes = XlsxReader.readImage(f, plan.rows[0].third[0])
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), bytes)
    }

    @Test fun angkaIndonesiaDanInternasional() {
        assertEquals(1250000.0, AsetImport.parseAngka("1.250.000")!!, 0.0)
        assertEquals(1250000.5, AsetImport.parseAngka("1,250,000.5")!!, 0.0)
        assertEquals(1250000.5, AsetImport.parseAngka("1.250.000,5")!!, 0.0)
        assertEquals(12.5, AsetImport.parseAngka("12,5")!!, 0.0)
        assertEquals(6283750.0, AsetImport.parseAngka("Rp 6.283.750")!!, 0.0)
        assertNull(AsetImport.parseAngka("abc"))
    }

    @Test fun serialTanggal() {
        assertEquals("2025-02-12", XlsxReader.serialToIso(45700.0))
    }
}
