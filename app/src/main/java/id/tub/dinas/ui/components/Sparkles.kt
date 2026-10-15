package id.tub.dinas.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.sin

private class Spark(val x: Float, val y: Float, val r: Float, val speed: Float, val phase: Float)
private val sparks = run {
    val rnd = java.util.Random(7)
    List(22) { Spark(rnd.nextFloat(), rnd.nextFloat(), 1.5f + rnd.nextFloat() * 3.5f, 0.5f + rnd.nextFloat(), rnd.nextFloat()) }
}

/** Kilau cahaya melayang pelan di latar langit. Ringan: 22 titik, satu animasi. */
@Composable
fun Sparkles(modifier: Modifier = Modifier) {
    val t by rememberInfiniteTransition(label = "sparkles").animateFloat(0f, 1f, infiniteRepeatable(tween(24000, easing = LinearEasing)), label = "t")
    Canvas(modifier) {
        val d = density
        sparks.forEach { s ->
            val yy = ((s.y - t * s.speed) % 1f + 1f) % 1f
            val xx = s.x + 0.02f * sin((t * 2 * PI + s.phase * 6.28).toFloat())
            val a = (0.35f + 0.65f * (0.5f + 0.5f * sin((t * 8 * PI * s.speed + s.phase * 6.28).toFloat()))) * 0.85f
            val c = Offset(xx * size.width, yy * size.height)
            val r = s.r * d * 2.2f
            drawCircle(Brush.radialGradient(listOf(Color(0xFFFFFFFF).copy(alpha = a), Color(0xFFBDEBFF).copy(alpha = a * 0.35f), Color.Transparent), c, r), r, c)
        }
    }
}
