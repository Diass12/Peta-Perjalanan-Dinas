package id.tub.dinas.ui.components

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import id.tub.dinas.MainViewModel
import id.tub.dinas.ui.theme.C
import kotlin.math.cos
import kotlin.math.sin

/** Hasil sebuah operasi (ekspor, impor, simpan, gagal). [uri] = berkas yang dihasilkan, bila ada. */
class HasilOp(val judul: String, val pesan: String, val uri: Uri? = null, val ok: Boolean = true)

private fun namaBerkas(ctx: android.content.Context, uri: Uri): String =
    runCatching { ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null } }.getOrNull()
        ?: uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':').orEmpty()

/** Lokasi yang mudah dibaca, mis. "Penyimpanan internal › Download › Laporan". */
private fun lokasiBerkas(uri: Uri): String {
    val a = uri.authority.orEmpty()
    return runCatching {
        when {
            a == "com.android.externalstorage.documents" -> {
                val id = DocumentsContract.getDocumentId(uri)
                val vol = id.substringBefore(':'); val path = id.substringAfter(':', "").substringBeforeLast('/', "")
                val v = if (vol == "primary") "Penyimpanan internal" else "Kartu SD / penyimpanan lain"
                (listOf(v) + path.split('/').filter { it.isNotBlank() }).joinToString(" › ")
            }
            a.contains("downloads") -> "Unduhan (Download)"
            a.contains("docs.storage") || a.contains("drive") -> "Google Drive"
            else -> a.ifBlank { "Lokasi yang Anda pilih" }
        }
    }.getOrDefault("Lokasi yang Anda pilih")
}

@Composable
fun HasilDialog(vm: MainViewModel) {
    val h = vm.hasilOp ?: return
    val ctx = LocalContext.current
    val nama = remember(h) { h.uri?.let { namaBerkas(ctx, it) } }
    val lokasi = remember(h) { h.uri?.let { lokasiBerkas(it) } }
    Dialog(onDismissRequest = { vm.hasilOp = null }) {
        Column(Modifier.fillMaxWidth().background(C.Surface, RoundedCornerShape(28.dp)).padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AnimasiHasil(h.ok)
            Text(h.judul, color = C.Ink, fontSize = 20.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            Text(h.pesan, color = C.Ink, fontSize = 14.sp)
            if (nama != null) {
                Column(Modifier.fillMaxWidth().background(C.Chip, RoundedCornerShape(14.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Nama berkas", color = C.Muted, fontSize = 11.sp)
                    Text(nama, color = C.Ink, fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text("Lokasi", color = C.Muted, fontSize = 11.sp)
                    Text(lokasi.orEmpty(), color = C.Ink, fontSize = 14.sp)
                }
            }
            Spacer(Modifier.height(2.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (h.uri != null && h.ok) TextButton(onClick = {
                    runCatching {
                        val mime = ctx.contentResolver.getType(h.uri) ?: "*/*"
                        ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW).setDataAndType(h.uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }.onFailure { vm.toast("Tidak ada aplikasi untuk membuka berkas ini") }
                }) { Text("Buka berkas") }
                TextButton(onClick = { vm.hasilOp = null }) { Text("OK", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) }
            }
        }
    }
}

/** Lingkaran tergambar, centang (atau silang) tergambar, dan percikan emas yang memancar. */
@Composable
private fun AnimasiHasil(ok: Boolean) {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) { p.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
    val warna = if (ok) Color(0xFF10B981) else Color(0xFFEF4444)
    Canvas(Modifier.size(96.dp)) {
        val c = Offset(size.width / 2, size.height / 2); val r = size.minDimension * 0.34f
        val lingkar = (p.value / 0.5f).coerceIn(0f, 1f)
        drawCircle(warna.copy(alpha = 0.15f), r)
        drawArc(warna, -90f, 360f * lingkar, false, topLeft = Offset(c.x - r, c.y - r), size = androidx.compose.ui.geometry.Size(r * 2, r * 2), style = Stroke(r * 0.14f, cap = StrokeCap.Round))
        val tanda = ((p.value - 0.45f) / 0.4f).coerceIn(0f, 1f)
        val path = Path().apply {
            if (ok) { moveTo(c.x - r * 0.42f, c.y + r * 0.02f); lineTo(c.x - r * 0.1f, c.y + r * 0.34f); lineTo(c.x + r * 0.45f, c.y - r * 0.32f) }
            else { moveTo(c.x - r * 0.34f, c.y - r * 0.34f); lineTo(c.x + r * 0.34f, c.y + r * 0.34f); moveTo(c.x + r * 0.34f, c.y - r * 0.34f); lineTo(c.x - r * 0.34f, c.y + r * 0.34f) }
        }
        val out = Path(); val m = PathMeasure(); m.setPath(path, false)
        m.getSegment(0f, m.length * tanda, out, true)
        drawPath(out, warna, style = Stroke(r * 0.16f, cap = StrokeCap.Round))
        if (ok) {
            val b = ((p.value - 0.5f) / 0.5f).coerceIn(0f, 1f)
            if (b > 0f && b < 1f) for (k in 0 until 10) {
                val a = k * 36f * (Math.PI.toFloat() / 180f); val d = r * (1.15f + b * 0.55f)
                drawCircle(if (k % 2 == 0) Color(0xFFFBBF24) else Color(0xFFFEF08A), r * 0.07f * (1f - b * 0.6f), Offset(c.x + cos(a) * d, c.y + sin(a) * d))
            }
        }
    }
}
