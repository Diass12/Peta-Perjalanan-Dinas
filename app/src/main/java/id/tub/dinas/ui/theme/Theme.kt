package id.tub.dinas.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import id.tub.dinas.R
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
    val HeaderBg: Color get() = if (ThemeState.sky) Color.Transparent else Color(0xFF12202B)
    val Ink = Color(0xFF12202B)
    val Amber = Color(0xFFE9A23B)
    val Line = Color(0xFFDDD8CC)
    val Muted: Color get() = if (ThemeState.sky) Color(0xFF28425E) else Color(0xFF5B6B78)
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


/** Latar layar: gambar langit pada tema Langit (dipotong memenuhi layar), krem pada tema Klasik. */
@Composable
fun Modifier.screenBackground(): Modifier {
    if (!ThemeState.sky) return this.background(Color(0xFFF6F4EE))
    val img = ImageBitmap.imageResource(R.drawable.bg_langit)
    return this.background(Color(0xFF7DC4F0)).drawBehind {
        val s = maxOf(size.width / img.width, size.height / img.height)
        val sw = minOf(img.width, (size.width / s).toInt()); val sh = minOf(img.height, (size.height / s).toInt())
        drawImage(
            img,
            srcOffset = IntOffset((img.width - sw) / 2, 0),
            srcSize = IntSize(sw, sh),
            dstSize = IntSize(size.width.toInt(), size.height.toInt()),
        )
    }
}
