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
    Column(Modifier.fillMaxWidth().background(C.HeaderBg).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) Text("‹", color = C.Ink, fontSize = 32.sp, modifier = Modifier.clickable(onClick = onBack).padding(horizontal = 14.dp, vertical = 2.dp))
            else Spacer(Modifier.width(14.dp))
            Text(title, color = C.Ink, maxLines = 1, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            actions()
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(C.Line))
    }
}

@Composable
fun HeaderAction(label: String, onClick: () -> Unit) =
    TextButton(onClick = onClick) { Text(label, color = C.Brand, fontWeight = FontWeight.SemiBold, fontSize = 15.sp) }

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) =
    Button({ Sfx.click(); onClick() }, modifier.heightIn(min = 48.dp), enabled = enabled, colors = ButtonDefaults.buttonColors(containerColor = C.Brand, contentColor = Color.White), shape = RoundedCornerShape(12.dp)) { Text(text, fontWeight = FontWeight.SemiBold) }

/** Tombol utama (nama lama dipertahankan). */
@Composable
fun AmberButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) =
    Button({ Sfx.menu(); onClick() }, modifier.heightIn(min = 52.dp), enabled = enabled, colors = ButtonDefaults.buttonColors(containerColor = C.Brand, contentColor = Color.White), shape = RoundedCornerShape(14.dp)) { Text(text, fontWeight = FontWeight.Bold, fontSize = 16.sp) }

@Composable
fun SoftButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, danger: Boolean = false, onClick: () -> Unit) =
    OutlinedButton(onClick, modifier.heightIn(min = 46.dp), enabled = enabled, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, if (danger) C.Danger.copy(alpha = 0.5f) else C.Line),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = if (danger) C.Danger else C.Ink)) { Text(text) }

/** Kartu putih bersudut 16 dp dengan garis tipis. */
@Composable
fun Panel(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val m = modifier.fillMaxWidth().clip(shape).background(C.Card, shape).border(1.dp, C.Line, shape)
    Column((if (onClick != null) m.clickable(onClick = onClick) else m).padding(16.dp), content = content)
}

/** Judul bagian di atas sekelompok kartu. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) = Text(text, style = MaterialTheme.typography.titleMedium, color = C.Ink, modifier = modifier.padding(top = 8.dp, bottom = 2.dp))

@Composable
fun Label(text: String) = Text(text, style = MaterialTheme.typography.labelSmall, color = C.Muted, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))

@Composable
fun Field(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, minLines: Int = 1, singleLine: Boolean = false) =
    OutlinedTextField(value, onChange, modifier.fillMaxWidth(), label = { Text(label) }, minLines = minLines, singleLine = singleLine,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = C.Brand, unfocusedBorderColor = C.Line, focusedLabelColor = C.Brand, cursorColor = C.Brand,
            focusedContainerColor = Color.White, unfocusedContainerColor = Color.White))

@Composable
fun Chip(text: String, selected: Boolean, color: Color = C.Brand, bg: Color = C.Chip, onClick: (() -> Unit)? = null) {
    val shape = RoundedCornerShape(20.dp)
    val m = Modifier.clip(shape).background(if (selected) color else bg, shape)
    Text(text, color = if (selected) Color.White else C.Ink, fontSize = 13.sp, fontWeight = FontWeight.Medium,
        modifier = (if (onClick != null) m.clickable(onClick = onClick) else m).padding(horizontal = 14.dp, vertical = 8.dp))
}

@Composable
fun LensTag(l: Lens) = Row(Modifier.background(C.lensBg(l), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
    Pic(lensIcon(l), 16.dp); Spacer(Modifier.width(4.dp))
    Text(l.label, color = C.lens(l), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
}

/** Ikon bergambar. */
@Composable
fun Pic(@DrawableRes id: Int, size: Dp = 24.dp, modifier: Modifier = Modifier) = Image(painterResource(id), null, modifier.size(size))

fun lensIcon(l: Lens): Int = when (l) { Lens.ASET -> R.drawable.ic_lensa_aset; Lens.OPS -> R.drawable.ic_lensa_ops; Lens.ADM -> R.drawable.ic_lensa_adm }
fun statusIcon(s: Status): Int = when (s) { Status.SELESAI -> R.drawable.ic_selesai; Status.PROSES -> R.drawable.ic_proses; Status.BELUM -> R.drawable.ic_belum; Status.SELISIH -> R.drawable.ic_belum }

private fun emojiOf(@DrawableRes icon: Int): String = when (icon) { R.drawable.ic_kamera -> "📷"; R.drawable.ic_galeri -> "🖼️"; R.drawable.ic_gps -> "📍"; else -> "" }

/** Tombol dengan ikon di kiri teks. */
@Composable
fun IconTextButton(@DrawableRes icon: Int, text: String, primary: Boolean = true, onClick: () -> Unit) {
    val e = emojiOf(icon)
    val content: @Composable RowScope.() -> Unit = { if (e.isNotEmpty()) { Text(e, fontSize = 16.sp); Spacer(Modifier.width(6.dp)) }; Text(text) }
    val click = { Sfx.click(); onClick() }
    if (primary) Button(click, Modifier.heightIn(min = 46.dp), colors = ButtonDefaults.buttonColors(containerColor = C.Brand, contentColor = Color.White), shape = RoundedCornerShape(12.dp), content = content)
    else OutlinedButton(click, Modifier.heightIn(min = 46.dp), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, C.Line), colors = ButtonDefaults.outlinedButtonColors(contentColor = C.Ink), content = content)
}

@Composable
fun StatusPill(s: Status) {
    val (fg, bg) = when (s) { Status.SELESAI -> C.Ok to C.OkSoft; Status.PROSES -> Color(0xFFB45309) to Color(0xFFFEF3C7); Status.BELUM -> C.Muted to C.Chip; Status.SELISIH -> C.Danger to Color(0xFFFDE8E8) }
    Text(s.label, color = fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.background(bg, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 4.dp))
}

/** Bar progres tipis. */
@Composable
fun SegBar(fraction: Float, color: Color, modifier: Modifier = Modifier, segments: Int = 20) {
    Box(modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(C.Chip)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f)).background(color, RoundedCornerShape(4.dp)))
    }
}

@Composable
fun Confirm(title: String, text: String, confirmLabel: String = "Hapus", onConfirm: () -> Unit, onDismiss: () -> Unit) =
    AlertDialog(onDismissRequest = onDismiss, containerColor = Color.White, title = { Text(title) }, text = { Text(text) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirmLabel, color = C.Danger, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } })

@Composable
fun Empty(text: String) = Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
    Text(text, color = C.Muted, fontSize = 15.sp, lineHeight = 22.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
}

/** Bingkai foto: sudut membulat dan garis tipis. */
fun Modifier.itemFrame(radius: Dp = 10.dp): Modifier {
    val shape = RoundedCornerShape(radius.coerceAtLeast(10.dp))
    return this.clip(shape).border(1.dp, C.Line, shape)
}
