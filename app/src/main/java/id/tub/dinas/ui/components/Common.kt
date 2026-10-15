package id.tub.dinas.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import id.tub.dinas.R
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.tub.dinas.domain.Lens
import id.tub.dinas.domain.Status
import id.tub.dinas.ui.theme.C
import id.tub.dinas.ui.theme.ThemeState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shadow

@Composable
fun Header(title: String, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    val sky = ThemeState.sky
    Row(
        Modifier.fillMaxWidth().background(C.HeaderBg).statusBarsPadding().padding(horizontal = 8.dp, vertical = if (sky) 10.dp else 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) TextButton(onClick = onBack) { Text("←", color = Color.White, fontSize = 24.sp) }
        else Spacer(Modifier.width(10.dp))
        Text(
            title, color = Color.White, maxLines = 1, modifier = Modifier.weight(1f),
            style = if (sky) MaterialTheme.typography.titleLarge.copy(shadow = Shadow(Color(0x99000F30), Offset(0f, 3f), 8f)) else MaterialTheme.typography.titleMedium,
        )
        actions()
    }
}

@Composable
fun HeaderAction(label: String, onClick: () -> Unit) =
    TextButton(onClick = onClick) { Text(label, color = if (ThemeState.sky) Color.White else C.Amber, fontWeight = FontWeight.Bold) }

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) =
    Button(onClick, modifier, enabled = enabled, colors = ButtonDefaults.buttonColors(containerColor = C.Ink, contentColor = Color.White), shape = RoundedCornerShape(10.dp)) { Text(text) }

@Composable
fun AmberButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) =
    Button(onClick, modifier, enabled = enabled, colors = ButtonDefaults.buttonColors(containerColor = C.Amber, contentColor = C.Ink), shape = RoundedCornerShape(10.dp)) { Text(text, fontWeight = FontWeight.Bold) }

@Composable
fun SoftButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, danger: Boolean = false, onClick: () -> Unit) =
    OutlinedButton(onClick, modifier, enabled = enabled, shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, if (danger) C.Danger else C.Line),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = if (danger) C.Danger else C.Ink)) { Text(text) }

@Composable
fun Panel(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(if (ThemeState.sky) 20.dp else 14.dp)
    var m = modifier.fillMaxWidth()
    m = if (ThemeState.sky) m
        .background(Brush.verticalGradient(listOf(Color(0xCCFFFFFF), Color(0x99E6F2FF))), shape)
        .border(1.5.dp, Brush.linearGradient(listOf(Color.White, Color(0xAA7FE3FF), Color.White)), shape)
    else m.background(C.Card, shape)
    Column((if (onClick != null) m.clickable(onClick = onClick) else m).padding(14.dp), content = content)
}

@Composable
fun Label(text: String) = Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = C.Muted, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))

@Composable
fun Field(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, minLines: Int = 1, singleLine: Boolean = false) =
    OutlinedTextField(value, onChange, modifier.fillMaxWidth(), label = { Text(label) }, minLines = minLines, singleLine = singleLine,
        shape = RoundedCornerShape(10.dp),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = C.Ink, unfocusedBorderColor = C.Line, focusedLabelColor = C.Ink, cursorColor = C.Ink))

@Composable
fun Chip(text: String, selected: Boolean, color: Color = C.Ink, bg: Color = Color.White, onClick: (() -> Unit)? = null) {
    val shape = RoundedCornerShape(20.dp)
    val sky = ThemeState.sky
    var m = Modifier.clip(shape)
    m = when {
        selected && sky -> m.background(Brush.verticalGradient(listOf(Color(0xFF4FB4FF), Color(0xFF1F6FE0))), shape).border(1.5.dp, Color(0xFFBFF0FF), shape)
        selected -> m.background(color, shape)
        sky -> m.background(Color(0xB3FFFFFF), shape).border(1.dp, Color(0xCCFFFFFF), shape)
        else -> m.background(bg, shape).border(1.dp, C.Line, shape)
    }
    Text(text, color = if (selected) Color.White else if (sky) C.Ink else color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        modifier = (if (onClick != null) m.clickable(onClick = onClick) else m).padding(horizontal = 12.dp, vertical = 6.dp))
}

@Composable
fun LensTag(l: Lens) = Row(Modifier.background(C.lensBg(l), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
    Pic(lensIcon(l), 16.dp); Spacer(Modifier.width(4.dp))
    Text(l.label, color = C.lens(l), fontSize = 11.sp, fontWeight = FontWeight.Bold)
}

/** Ikon bergambar. */
@Composable
fun Pic(@DrawableRes id: Int, size: Dp = 24.dp, modifier: Modifier = Modifier) = Image(painterResource(id), null, modifier.size(size))

fun lensIcon(l: Lens): Int = when (l) { Lens.ASET -> R.drawable.ic_lensa_aset; Lens.OPS -> R.drawable.ic_lensa_ops; Lens.ADM -> R.drawable.ic_lensa_adm }
fun statusIcon(s: Status): Int = when (s) { Status.SELESAI -> R.drawable.ic_selesai; Status.PROSES -> R.drawable.ic_proses; Status.BELUM -> R.drawable.ic_belum }

/** Tombol dengan ikon bergambar di kiri teks. */
@Composable
fun IconTextButton(@DrawableRes icon: Int, text: String, primary: Boolean = true, onClick: () -> Unit) {
    val content: @Composable RowScope.() -> Unit = { Pic(icon, 22.dp); Spacer(Modifier.width(6.dp)); Text(text) }
    if (primary) Button(onClick, colors = ButtonDefaults.buttonColors(containerColor = C.Ink, contentColor = Color.White), shape = RoundedCornerShape(10.dp), content = content)
    else OutlinedButton(onClick, shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, C.Line), colors = ButtonDefaults.outlinedButtonColors(contentColor = C.Ink), content = content)
}

@Composable
fun StatusPill(s: Status) {
    if (ThemeState.sky) {
        val c = when (s) { Status.SELESAI -> Color(0xFF4ADE80); Status.PROSES -> Color(0xFFFBBF24); Status.BELUM -> Color(0xFFCBD5E1) }
        val shape = RoundedCornerShape(8.dp)
        Row(Modifier.background(Color(0xCC0B2545), shape).border(1.dp, c.copy(alpha = 0.8f), shape).padding(horizontal = 6.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Pic(statusIcon(s), 16.dp); Spacer(Modifier.width(4.dp))
            Text("[${s.label.uppercase()}]", color = c, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        return
    }
    val (fg, bg) = when (s) { Status.SELESAI -> Color(0xFF0F766E) to Color(0xFFD8F0EC); Status.PROSES -> Color(0xFFB45309) to Color(0xFFFBEBD9); Status.BELUM -> C.Muted to Color(0xFFECE9E0) }
    Text(s.label, color = fg, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.background(bg, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp))
}

/** Bar progres bersegmen ala game. */
@Composable
fun SegBar(fraction: Float, color: Color, modifier: Modifier = Modifier, segments: Int = 20) {
    val filled = Math.round(fraction.coerceIn(0f, 1f) * segments)
    Row(modifier.fillMaxWidth().background(Color(0xCC0B2545), RoundedCornerShape(8.dp)).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for (i in 0 until segments) {
            Box(Modifier.weight(1f).height(12.dp).background(if (i < filled) color else Color(0x33FFFFFF), RoundedCornerShape(3.dp)))
        }
    }
}

/** Navigasi bawah: ikon emoji + label, tab aktif menyala. */
@Composable
fun BottomNav(items: List<Pair<Int, String>>, selected: Int, onSelect: (Int) -> Unit) {
    val sky = ThemeState.sky
    val bar = if (sky) Modifier.background(Brush.verticalGradient(listOf(Color(0xF01B4F9C), Color(0xFF0B2545))))
        .border(1.dp, Color(0x886FD8FF), RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
    else Modifier.background(Color.White)
    Row(Modifier.fillMaxWidth().then(bar).navigationBarsPadding().padding(vertical = 6.dp)) {
        items.forEachIndexed { i, (icon, label) ->
            val on = i == selected
            Column(Modifier.weight(1f).clickable { onSelect(i) }.padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(if (on && sky) Modifier.background(Brush.radialGradient(listOf(Color(0xAA60D8FF), Color.Transparent)), RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 2.dp) else Modifier.padding(horizontal = 14.dp, vertical = 2.dp)) {
                    Pic(icon, if (on) 38.dp else 30.dp, if (on) Modifier else Modifier.alpha(0.85f))
                }
                Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                    color = if (sky) (if (on) Color(0xFFFFD166) else Color.White) else (if (on) C.Ink else C.Muted))
            }
        }
    }
}

@Composable
fun Confirm(title: String, text: String, confirmLabel: String = "Hapus", onConfirm: () -> Unit, onDismiss: () -> Unit) =
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(text) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirmLabel, color = C.Danger, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } })

@Composable
fun Empty(text: String) = Text(text, color = if (ThemeState.sky) Color.White else C.Muted, modifier = Modifier.fillMaxWidth().padding(28.dp), fontSize = 14.sp)
