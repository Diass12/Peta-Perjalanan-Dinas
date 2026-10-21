package id.tub.dinas.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.tub.dinas.ui.theme.C
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay

/**
 * Layar tunggu: penambang mengayunkan beliung ke dinding batu, bongkahan emas terlempar ke bak truk
 * yang terisi perlahan. Muncul bila pekerjaan berjalan lebih dari 0,3 detik supaya aplikasi tidak terlihat macet.
 */
@Composable
fun LoadingOverlay(aktif: Boolean, pesan: String? = null, onBerhenti: (() -> Unit)? = null) {
    var tampil by remember { mutableStateOf(false) }
    LaunchedEffect(aktif) { if (aktif) { delay(300); tampil = true } else tampil = false }
    if (!tampil) return
    Box(Modifier.fillMaxSize().background(Color(0xB3000000)).pointerInput(Unit) { detectTapGestures { } }, contentAlignment = Alignment.Center) {
        Column(Modifier.padding(24.dp).widthIn(max = 360.dp).fillMaxWidth().background(C.Surface, RoundedCornerShape(24.dp)).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            MinerScene(Modifier.fillMaxWidth().height(170.dp))
            Text(pesan ?: "Sedang memproses…", color = C.Ink, fontSize = 15.sp, maxLines = 3)
            Text("Mohon tunggu, jangan tutup aplikasi.", color = C.Muted, fontSize = 12.sp)
            if (onBerhenti != null) androidx.compose.material3.TextButton(onClick = onBerhenti) { Text("Berhenti") }
        }
    }
}

@Composable
fun MinerScene(modifier: Modifier = Modifier) {
    val tr = rememberInfiniteTransition(label = "tambang")
    val t by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Restart), label = "ayun")
    val isi by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(7800, easing = LinearEasing), RepeatMode.Restart), label = "isi")
    Canvas(modifier) { scene(t, isi) }
}

private val Gold = Color(0xFFFBBF24)
private val GoldDark = Color(0xFFD97706)
private val Rock = Color(0xFF5B6475)
private val RockDark = Color(0xFF3F4756)
private val Ground = Color(0xFF8B6B43)
private val Hat = Color(0xFFFFD43B)
private val Vest = Color(0xFFF97316)
private val Skin = Color(0xFFF1C27D)
private val Steel = Color(0xFFCBD5E1)
private val TruckBody = Color(0xFF2563EB)

private fun DrawScope.scene(t: Float, isi: Float) {
    val w = size.width; val h = size.height
    val u = h / 170f                                  // satuan relatif tinggi
    val gy = h * 0.82f                                // garis tanah
    // tanah
    drawRect(Ground, Offset(0f, gy), Size(w, h - gy))
    drawRect(Color(0x33000000), Offset(0f, gy), Size(w, 3f * u))
    // dinding batu kiri dengan urat emas
    val rx = w * 0.02f; val rw = w * 0.22f
    val rockPath = Path().apply {
        moveTo(rx, gy); lineTo(rx, h * 0.32f); lineTo(rx + rw * 0.45f, h * 0.2f); lineTo(rx + rw, h * 0.34f); lineTo(rx + rw * 1.05f, gy); close()
    }
    drawPath(rockPath, Rock)
    drawPath(Path().apply { moveTo(rx + rw * 0.45f, h * 0.2f); lineTo(rx + rw, h * 0.34f); lineTo(rx + rw * 1.05f, gy); lineTo(rx + rw * 0.6f, gy); close() }, RockDark)
    for ((fx, fy) in listOf(0.55f to 0.45f, 0.35f to 0.6f, 0.75f to 0.62f, 0.5f to 0.74f)) drawCircle(Gold, 3.5f * u, Offset(rx + rw * fx, h * fy))

    // penambang
    val mx = w * 0.40f                                 // posisi kaki
    val ayun = (sin((t * 2 * PI) - PI / 2).toFloat() + 1f) / 2f      // 0..1
    val bodyTop = gy - 62f * u
    // kaki
    drawLine(Color(0xFF334155), Offset(mx - 5f * u, gy), Offset(mx - 3f * u, gy - 26f * u), 7f * u, StrokeCap.Round)
    drawLine(Color(0xFF334155), Offset(mx + 8f * u, gy), Offset(mx + 4f * u, gy - 26f * u), 7f * u, StrokeCap.Round)
    // badan (rompi)
    drawRoundRect(Vest, Offset(mx - 12f * u, bodyTop + 12f * u), Size(24f * u, 34f * u), androidx.compose.ui.geometry.CornerRadius(6f * u))
    drawRect(Steel, Offset(mx - 12f * u, bodyTop + 26f * u), Size(24f * u, 3f * u))
    // kepala + helm + lampu
    drawCircle(Skin, 9f * u, Offset(mx, bodyTop + 4f * u))
    drawArc(Hat, 180f, 180f, true, Offset(mx - 11f * u, bodyTop - 8f * u), Size(22f * u, 18f * u))
    drawRect(Hat, Offset(mx - 13f * u, bodyTop + 1f * u), Size(26f * u, 3f * u))
    drawCircle(Color.White, 2.6f * u, Offset(mx + 8f * u, bodyTop - 1f * u))
    // lengan + beliung (ayunan dari belakang ke dinding batu)
    val shoulder = Offset(mx - 4f * u, bodyTop + 18f * u)
    val ang = (-125f + 125f * ayun) * (PI.toFloat() / 180f)         // -125° (diangkat ke belakang) .. 0° (menghantam batu di kiri)
    val dx = -cos(ang); val dy = sin(ang)
    val len = 38f * u
    val grip = Offset(shoulder.x + dx * 14f * u, shoulder.y + dy * 14f * u)
    val handleEnd = Offset(shoulder.x + dx * len, shoulder.y + dy * len)
    drawLine(Skin, shoulder, grip, 6f * u, StrokeCap.Round)
    drawLine(Color(0xFF92400E), shoulder, handleEnd, 4f * u, StrokeCap.Round)
    // kepala beliung (tegak lurus tangkai)
    val nx = -(handleEnd.y - shoulder.y); val ny = handleEnd.x - shoulder.x
    val nl = kotlin.math.sqrt(nx * nx + ny * ny).coerceAtLeast(1f)
    val ox = nx / nl * 11f * u; val oy = ny / nl * 11f * u
    drawLine(Steel, Offset(handleEnd.x - ox, handleEnd.y - oy), Offset(handleEnd.x + ox, handleEnd.y + oy), 5f * u, StrokeCap.Round)

    // percikan & bongkahan emas saat menghantam (ayun mendekati 1)
    val hit = ((t - 0.5f) / 0.5f).coerceIn(0f, 1f)                   // 0..1 setelah hantaman
    val ex = rx + rw * 1.02f; val ey = h * 0.5f
    val tx = w * 0.66f; val ty = gy - 48f * u                          // bak truk
    for (i in 0 until 3) {
        val p = ((hit * 1.4f) - i * 0.18f).coerceIn(0f, 1f)
        if (p <= 0f || p >= 1f || hit == 0f) continue
        val x = ex + (tx - ex) * p
        val y = ey + (ty - ey) * p - sin(p * PI.toFloat()) * 38f * u
        drawCircle(Gold, 3.6f * u, Offset(x, y)); drawCircle(GoldDark, 3.6f * u, Offset(x, y), style = Stroke(1.2f * u))
    }
    if (hit in 0.02f..0.35f) for (k in 0 until 5) {
        val a = (-40f + k * 20f) * (PI.toFloat() / 180f); val r = (6f + hit * 40f) * u
        drawCircle(Color(0xFFFEF08A), 1.8f * u, Offset(ex + cos(a) * r, ey - 4f * u + sin(a) * r))
    }

    // truk tambang
    val bob = if (hit in 0.15f..0.45f) sin(hit * 40f) * 0.8f * u else 0f
    val tl = w * 0.58f                                                  // kiri truk
    val tw = w * 0.38f
    val by = gy - 22f * u + bob
    // bak
    val bak = Path().apply {
        moveTo(tl, by - 34f * u); lineTo(tl + tw * 0.62f, by - 30f * u); lineTo(tl + tw * 0.58f, by); lineTo(tl + tw * 0.04f, by); close()
    }
    drawPath(bak, Color(0xFF475569))
    // muatan emas (naik seiring waktu)
    val fill = (isi * 1.15f).coerceIn(0f, 1f)
    val gh = 26f * u * fill
    if (gh > 1f) {
        val gp = Path().apply { moveTo(tl + tw * 0.05f, by - 1f * u); lineTo(tl + tw * 0.57f, by - 1f * u); lineTo(tl + tw * 0.585f, by - 1f * u - gh); lineTo(tl + tw * 0.045f, by - 1f * u - gh * 1.05f); close() }
        drawPath(gp, Gold)
        for (i in 0 until 5) drawCircle(GoldDark, 2f * u, Offset(tl + tw * (0.1f + i * 0.1f), by - 1f * u - gh * (0.3f + 0.15f * (i % 3))))
    }
    // kabin
    drawRoundRect(TruckBody, Offset(tl + tw * 0.64f, by - 28f * u), Size(tw * 0.36f, 28f * u), androidx.compose.ui.geometry.CornerRadius(5f * u))
    drawRect(Color(0xFFBAE6FD), Offset(tl + tw * 0.74f, by - 24f * u), Size(tw * 0.2f, 11f * u))
    drawRect(Color(0xFF1E293B), Offset(tl, by), Size(tw, 6f * u))
    // roda berputar pelan
    for (wx in listOf(tl + tw * 0.2f, tl + tw * 0.48f, tl + tw * 0.82f)) {
        val cy = gy - 6f * u
        drawCircle(Color(0xFF111827), 9f * u, Offset(wx, cy))
        drawCircle(Color(0xFF9CA3AF), 4f * u, Offset(wx, cy))
        val a = isi * 2 * PI.toFloat() * 2
        drawLine(Color(0xFF111827), Offset(wx - cos(a) * 3.5f * u, cy - sin(a) * 3.5f * u), Offset(wx + cos(a) * 3.5f * u, cy + sin(a) * 3.5f * u), 1.6f * u)
    }
    // debu di dekat tanah
    val dust = abs(sin(t * 2 * PI.toFloat()))
    drawCircle(Color(0x33FFFFFF), (3f + dust * 3f) * u, Offset(mx + 18f * u, gy - 2f * u))
}
