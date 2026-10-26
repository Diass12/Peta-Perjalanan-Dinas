package id.tub.dinas.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Brush
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
    /** true = mode gelap (hitam kebiruan, gradasi ungu-jingga) seperti Aset Scan. */
    var dark by mutableStateOf(false)
}

object C {
    private fun p(l: Long, d: Long) = Color(if (ThemeState.dark) d else l)
    /** Biru lembut untuk aksen sekunder. */
    val Soft get() = p(0xFF2F5D8A, 0xFF8AB4FF)
    val Navy2 get() = p(0xFF1F3342, 0xFF3B2D8F)
    val Bg get() = p(0xFFF2F7FE, 0xFF0E0D22)
    /** Latar padat untuk dialog layar penuh. */
    val Solid get() = p(0xFFF2F7FE, 0xFF0E0D22)
    val Card get() = p(0xFFFFFFFF, 0xFF1A1936)
    /** Permukaan kartu/bilah/dialog (putih di mode terang). */
    val Surface get() = p(0xFFFFFFFF, 0xFF1A1936)
    val HeaderBg get() = p(0xFFFFFFFF, 0xFF14132E)
    val Ink get() = p(0xFF12202B, 0xFFF3F2FF)
    val Brand get() = p(0xFF12202B, 0xFF7C5CFA)
    val BrandSoft get() = p(0xFFF8EBD2, 0xFF2A2655)
    val Amber get() = p(0xFFD97706, 0xFFFBBF24)
    val Line get() = p(0xFFD9E4F5, 0xFF2E2C54)
    val Muted get() = p(0xFF5B6B78, 0xFFA9A7CC)
    val Danger get() = p(0xFFDC2626, 0xFFF87171)
    val Ok get() = p(0xFF059669, 0xFF34D399)
    val OkSoft get() = p(0xFFD1FAE5, 0xFF12382F)
    val Chip get() = p(0xFFE8F0FC, 0xFF26244C)
    /** Aksen emas-oranye untuk tombol utama (CTA) dan penanda aktif. */
    val Accent get() = p(0xFFE9A23B, 0xFFFBBF24)
    val AccentSoft get() = p(0xFFFBEFD7, 0xFF3A2E12)
    /** Warna kategori: wisata, kuliner, hotel, foto. */
    val Wisata = Color(0xFF0F766E)
    val Kuliner = Color(0xFFB45309)
    val Hotel = Color(0xFF4F46E5)
    val Foto = Color(0xFFBE185D)
    val PinDok = Color(0xFFE9C23B)
    val PinLap = Color(0xFF14532D)
    /** Gradasi aksen: ungu → merah muda → jingga (mode gelap); biru tua polos (mode terang). */
    val Grad: Brush get() = if (ThemeState.dark) Brush.horizontalGradient(listOf(Color(0xFF6C4DF6), Color(0xFFC94FE0), Color(0xFFF08A5D))) else Brush.horizontalGradient(listOf(Color(0xFF12202B), Color(0xFF1F3342)))

    fun lens(l: Lens): Color = if (ThemeState.dark) when (l) { Lens.ASET -> Color(0xFFFBBF24); Lens.OPS -> Color(0xFF5EEAD4); Lens.ADM -> Color(0xFFA5B4FC) }
        else when (l) { Lens.ASET -> Color(0xFFB45309); Lens.OPS -> Color(0xFF0F766E); Lens.ADM -> Color(0xFF4F46E5) }
    fun lensBg(l: Lens): Color = if (ThemeState.dark) when (l) { Lens.ASET -> Color(0xFF3A2E12); Lens.OPS -> Color(0xFF123A38); Lens.ADM -> Color(0xFF2B2A5E) }
        else when (l) { Lens.ASET -> Color(0xFFFBEBD9); Lens.OPS -> Color(0xFFD8F0EC); Lens.ADM -> Color(0xFFE6E5FB) }
}

private fun scheme() = if (ThemeState.dark) darkColorScheme(
    primary = C.Brand, onPrimary = Color.White, secondary = C.Brand, onSecondary = Color.White,
    background = C.Bg, onBackground = C.Ink, surface = C.Surface, onSurface = C.Ink,
    surfaceVariant = C.Chip, onSurfaceVariant = C.Muted, outline = C.Line, error = C.Danger,
    surfaceContainerHigh = C.Surface, surfaceContainerHighest = C.Chip, surfaceContainer = C.Surface, surfaceContainerLow = C.Surface,
    primaryContainer = C.BrandSoft, onPrimaryContainer = C.Ink, secondaryContainer = C.BrandSoft, onSecondaryContainer = C.Ink,
    inverseSurface = C.Ink, inverseOnSurface = C.Bg,
) else lightColorScheme(
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
    small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(22.dp), extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun DinasTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = scheme(), typography = type, shapes = shapes) {
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides C.Ink, content = content)
}

/** Latar layar: abu sangat muda (terang) atau gradasi hitam kebiruan dengan cahaya ungu samar (gelap). */
@Composable
fun Modifier.screenBackground(): Modifier = if (ThemeState.dark)
    this.background(Brush.verticalGradient(listOf(Color(0xFF1A1340), Color(0xFF0E0D22), Color(0xFF0B0A1C))))
else this.background(C.Bg)
