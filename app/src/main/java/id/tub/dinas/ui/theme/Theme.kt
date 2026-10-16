package id.tub.dinas.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.tub.dinas.domain.Lens

/** Tema bersih & terang: satu aksen biru, kartu putih di atas latar abu sangat muda. */
object ThemeState {
    var playerName by mutableStateOf("Dias")
}

object C {
    val Bg = Color(0xFFF5F7FB)
    /** Latar padat untuk dialog layar penuh. */
    val Solid = Color(0xFFF5F7FB)
    val Card = Color.White
    val HeaderBg = Color.White
    val Ink = Color(0xFF111827)
    val Brand = Color(0xFF2563EB)
    val BrandSoft = Color(0xFFE8F0FE)
    val Amber = Color(0xFFD97706)
    val Line = Color(0xFFE5E7EB)
    val Muted = Color(0xFF6B7280)
    val Danger = Color(0xFFDC2626)
    val Ok = Color(0xFF15803D)
    val OkSoft = Color(0xFFE3F5E9)
    val Chip = Color(0xFFEEF1F6)
    val PinDok = Color(0xFFE9C23B)
    val PinLap = Color(0xFF14532D)

    fun lens(l: Lens): Color = when (l) { Lens.ASET -> Color(0xFFB45309); Lens.OPS -> Color(0xFF0F766E); Lens.ADM -> Color(0xFF4F46E5) }
    fun lensBg(l: Lens): Color = when (l) { Lens.ASET -> Color(0xFFFBEBD9); Lens.OPS -> Color(0xFFD8F0EC); Lens.ADM -> Color(0xFFE6E5FB) }
}

private val scheme = lightColorScheme(
    primary = C.Brand, onPrimary = Color.White,
    secondary = C.Brand, onSecondary = Color.White,
    background = C.Bg, onBackground = C.Ink,
    surface = Color.White, onSurface = C.Ink,
    surfaceVariant = C.Chip, onSurfaceVariant = C.Muted,
    outline = C.Line, error = C.Danger,
    surfaceContainerHigh = Color.White, surfaceContainerHighest = C.Chip, surfaceContainer = Color.White,
    primaryContainer = C.BrandSoft, onPrimaryContainer = C.Brand,
)

private val type = Typography(
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 21.sp),
    labelSmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.3.sp),
)

private val shapes = Shapes(
    small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(14.dp), large = RoundedCornerShape(20.dp), extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun DinasTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = scheme, typography = type, shapes = shapes, content = content)

/** Latar layar: abu sangat muda. */
@Composable
fun Modifier.screenBackground(): Modifier = this.background(C.Bg)
