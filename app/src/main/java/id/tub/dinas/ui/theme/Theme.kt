package id.tub.dinas.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import id.tub.dinas.domain.Lens

/** Tema tampilan: Klasik (krem/gelap, paling terbaca di bawah matahari) atau Langit (kaca di atas langit biru). */
object ThemeState {
    var sky by mutableStateOf(true)
}

object C {
    /** Latar layar. Pada tema Langit transparan karena latar langit digambar di Root. */
    val Bg: Color get() = if (ThemeState.sky) Color.Transparent else Color(0xFFF6F4EE)
    /** Latar padat untuk dialog layar penuh (tidak boleh transparan). */
    val Solid: Color get() = if (ThemeState.sky) Color(0xFFD9EAFB) else Color(0xFFF6F4EE)
    val Card: Color get() = if (ThemeState.sky) Color(0xCCFFFFFF) else Color(0xFFFFFFFF)
    val HeaderBg: Color get() = if (ThemeState.sky) Color(0xE60B2545) else Color(0xFF12202B)
    val Ink = Color(0xFF12202B)
    val Amber = Color(0xFFE9A23B)
    val Line = Color(0xFFDDD8CC)
    val Muted: Color get() = if (ThemeState.sky) Color(0xFF2C4257) else Color(0xFF5B6B78)
    val Danger = Color(0xFFB3261E)
    val PinDok = Color(0xFFE9C23B)
    val PinLap = Color(0xFF14532D)

    fun lens(l: Lens): Color = when (l) { Lens.ASET -> Color(0xFFB45309); Lens.OPS -> Color(0xFF0F766E); Lens.ADM -> Color(0xFF4F46E5) }
    fun lensBg(l: Lens): Color = when (l) { Lens.ASET -> Color(0xFFFBEBD9); Lens.OPS -> Color(0xFFD8F0EC); Lens.ADM -> Color(0xFFE6E5FB) }
}

private val scheme = lightColorScheme(
    primary = C.Ink, onPrimary = Color.White,
    secondary = C.Amber, onSecondary = C.Ink,
    background = Color(0xFFF6F4EE), onBackground = Color(0xFF12202B),
    surface = Color.White, onSurface = Color(0xFF12202B),
    outline = Color(0xFFDDD8CC), error = Color(0xFFB3261E),
    surfaceContainerHigh = Color.White,
)

private val type = Typography(
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 15.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp),
)

@Composable
fun DinasTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = scheme, typography = type, content = content)


private fun DrawScope.blob(cx: Float, cy: Float, r: Float, a: Float) {
    drawCircle(
        brush = Brush.radialGradient(listOf(Color.White.copy(alpha = a), Color.Transparent), center = Offset(cx, cy), radius = r),
        radius = r, center = Offset(cx, cy),
    )
}

private fun DrawScope.sparkle(cx: Float, cy: Float, r: Float) {
    val p = Path().apply {
        moveTo(cx, cy - r); quadraticTo(cx, cy, cx + r, cy); quadraticTo(cx, cy, cx, cy + r)
        quadraticTo(cx, cy, cx - r, cy); quadraticTo(cx, cy, cx, cy - r); close()
    }
    drawPath(p, Color.White.copy(alpha = 0.85f))
}

/** Langit bergaya game: gradasi, awan lembut, pulau melayang, kilau. Semua digambar dengan kode. */
fun DrawScope.drawSky() {
    val w = size.width; val h = size.height
    // awan lembut
    blob(w * 0.15f, h * 0.12f, w * 0.40f, 0.55f); blob(w * 0.35f, h * 0.10f, w * 0.30f, 0.45f)
    blob(w * 0.85f, h * 0.33f, w * 0.45f, 0.50f); blob(w * 0.10f, h * 0.52f, w * 0.40f, 0.45f)
    blob(w * 0.70f, h * 0.72f, w * 0.50f, 0.50f); blob(w * 0.20f, h * 0.90f, w * 0.45f, 0.55f)
    // pulau melayang (kanan atas)
    val ix = w * 0.80f; val iy = h * 0.075f
    val rock = Path().apply {
        moveTo(ix - w * 0.17f, iy); lineTo(ix + w * 0.17f, iy)
        quadraticTo(ix + w * 0.10f, iy + h * 0.035f, ix + w * 0.02f, iy + h * 0.065f)
        quadraticTo(ix, iy + h * 0.07f, ix - w * 0.03f, iy + h * 0.06f)
        quadraticTo(ix - w * 0.12f, iy + h * 0.03f, ix - w * 0.17f, iy); close()
    }
    drawPath(rock, Color(0xFF5E7FA8).copy(alpha = 0.45f))
    drawOval(Color(0xFF8FD1A8).copy(alpha = 0.65f), Offset(ix - w * 0.17f, iy - h * 0.008f), Size(w * 0.34f, h * 0.016f))
    // menara kecil
    listOf(-0.05f to 0.05f, -0.015f to 0.075f, 0.03f to 0.045f).forEach { (dx, hh) ->
        val x = ix + w * dx
        drawRect(Color.White.copy(alpha = 0.75f), Offset(x, iy - h * hh), Size(w * 0.012f, h * hh))
        val roof = Path().apply { moveTo(x - w * 0.004f, iy - h * hh); lineTo(x + w * 0.006f, iy - h * hh - h * 0.012f); lineTo(x + w * 0.016f, iy - h * hh); close() }
        drawPath(roof, Color(0xFFBFD9FF).copy(alpha = 0.9f))
    }
    // kilau
    listOf(0.08f to 0.06f, 0.62f to 0.04f, 0.92f to 0.24f, 0.05f to 0.40f, 0.55f to 0.58f, 0.90f to 0.66f, 0.30f to 0.80f)
        .forEach { (fx, fy) -> sparkle(w * fx, h * fy, w * 0.014f) }
}

/** Latar layar: langit pada tema Langit, krem pada tema Klasik. */
@Composable
fun Modifier.screenBackground(): Modifier =
    if (ThemeState.sky) this
        .background(Brush.verticalGradient(listOf(Color(0xFF1E6FD9), Color(0xFF5FA8EE), Color(0xFFAED6F7), Color(0xFFD9EDFB))))
        .drawBehind { drawSky() }
    else this.background(Color(0xFFF6F4EE))
