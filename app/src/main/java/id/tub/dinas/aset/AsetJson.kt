package id.tub.dinas.aset

import android.util.Base64
import android.util.JsonWriter
import id.tub.dinas.domain.*
import id.tub.dinas.export.Imaging
import java.io.OutputStream
import java.time.Instant

/** Ekspor ke cadangan JSON format AsetScan v2 (`{app:"asetscan",v:2,at,master,assets}`) — bisa dibuka di Aset Scan PC/PWA beserta foto & keterangan. */
object AsetJson {
    private fun iso(ms: Long?) = ms?.let { Instant.ofEpochMilli(it).toString() }

    private fun dataUrl(b: ByteArray, maxPx: Int, q: Int): String? =
        Imaging.jpeg(b, maxPx, q)?.let { "data:image/jpeg;base64," + Base64.encodeToString(it, Base64.NO_WRAP) }

    fun tulis(daftar: List<AsetModel>, fotos: Map<String, List<AsetFotoModel>>, master: MasterStore, register: List<AsetModel>,
              baca: (String) -> ByteArray?, out: OutputStream, denganFoto: Boolean = true) {
        val w = JsonWriter(java.io.OutputStreamWriter(java.io.BufferedOutputStream(out, 64 * 1024), Charsets.UTF_8))
        w.setIndent("")
        w.beginObject()
        w.name("app").value("asetscan"); w.name("v").value(2L); w.name("at").value(Instant.now().toString())
        w.name("master").beginObject()
        w.name("company").value(daftar.map { it.owner }.filter { it.isNotBlank() }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: "PT TRI USAHA BARU")
        w.name("classes").beginArray()
        for (k in KodeAset.KELAS) { w.beginObject(); w.name("code").value(k.kode); w.name("name").value(k.nama); w.name("cat").value(k.kategori); w.endObject() }
        w.endArray()
        fun list(n: String, f: MasterField) { w.name(n).beginArray(); master.options(f, register).forEach { w.value(it) }; w.endArray() }
        list("depts", MasterField.DEPT); list("locations", MasterField.LOKASI); list("conds", MasterField.KONDISI)
        w.endObject()
        w.name("assets").beginArray()
        for (a in daftar) {
            val fl = fotos[a.id].orEmpty().sortedBy { it.order }
            w.beginObject()
            w.name("id").value(a.id); w.name("ord").value(a.order.toLong())
            w.name("code").value(a.code); w.name("name").value(a.name); w.name("category").value(a.category)
            w.name("classif").value(a.classif); w.name("owner").value(a.owner); w.name("tenure").value(a.tenure)
            w.name("dept").value(a.dept); w.name("location").value(a.location); w.name("user").value(a.user); w.name("cond").value(a.cond)
            val ho = id.tub.dinas.security.Lisensi.ho
            w.name("nbv"); a.nbv?.takeIf { ho }?.let { w.value(it) } ?: w.nullValue()
            w.name("acq").value(a.acq); w.name("checkDate").value(a.checkDate); w.name("remarks").value(a.remarks)
            w.name("barcode").value(a.barcode?.name?.lowercase() ?: ""); w.name("barcodeSrc").value(a.barcodeSrc.name.lowercase().let { if (it == "none") "" else it })
            w.name("pcodes").beginArray(); a.pcodes.forEach { w.value(it) }; w.endArray()
            w.name("pat").beginArray(); fl.forEach { w.value(iso(it.takenAt) ?: "") }; w.endArray()
            w.name("ocrAt").nullValue()
            w.name("nbvKet").value(if (ho) a.nbvKet else ""); w.name("tglTercatat").value(if (ho) a.tglTercatat else "")
            w.name("masa"); a.masa?.takeIf { ho }?.let { w.value(it.toLong()) } ?: w.nullValue()
            w.name("harga"); a.harga?.takeIf { ho }?.let { w.value(it) } ?: w.nullValue()
            w.name("catatan").value(if (ho) a.catatan else ""); w.name("sumber").value(if (ho) a.sumber else ""); w.name("srcStatus").value(if (ho) a.srcStatus else "")
            val bytes = if (denganFoto) fl.mapNotNull { f -> baca(f.path) } else emptyList()
            w.name("photos").beginArray(); bytes.forEach { b -> dataUrl(b, 900, 75)?.let { w.value(it) } }; w.endArray()
            w.name("orig").beginArray(); w.endArray()
            w.name("media").beginArray(); w.endArray()
            w.name("thumb").value(bytes.firstOrNull()?.let { dataUrl(it, 240, 70) } ?: "")
            w.name("state").value(a.state.name.lowercase()); w.name("auditedAt"); iso(a.auditedAt)?.let { w.value(it) } ?: w.nullValue()
            w.name("log").beginArray()
            a.log.forEach { l ->
                val i = l.indexOf('|'); w.beginObject()
                w.name("at").value(if (i > 0) iso(l.substring(0, i).toLongOrNull()) ?: "" else ""); w.name("msg").value(if (i > 0) l.substring(i + 1) else l)
                w.endObject()
            }
            w.endArray()
            w.name("created").value(iso(a.createdAt) ?: ""); w.name("updated").value(iso(a.updatedAt) ?: "")
            w.endObject()
        }
        w.endArray(); w.endObject(); w.flush()
    }
}
