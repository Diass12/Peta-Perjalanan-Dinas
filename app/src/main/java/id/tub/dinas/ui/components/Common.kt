package id.tub.dinas.ui.components

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

@Composable
fun Header(title: String, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().background(C.HeaderBg).statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) TextButton(onClick = onBack) { Text("←", color = Color.White, fontSize = 22.sp) }
        else Spacer(Modifier.width(8.dp))
        Text(title, color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 1, modifier = Modifier.weight(1f))
        actions()
    }
}

@Composable
fun HeaderAction(label: String, onClick: () -> Unit) =
    TextButton(onClick = onClick) { Text(label, color = C.Amber, fontWeight = FontWeight.Bold) }

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
    val m = modifier.fillMaxWidth().background(C.Card, RoundedCornerShape(14.dp)).then(if (ThemeState.sky) Modifier.border(1.dp, Color(0x99FFFFFF), RoundedCornerShape(14.dp)) else Modifier)
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
    val m = Modifier.background(if (selected) color else bg, RoundedCornerShape(20.dp))
    Text(text, color = if (selected) Color.White else color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        modifier = (if (onClick != null) m.clickable(onClick = onClick) else m).padding(horizontal = 12.dp, vertical = 6.dp))
}

@Composable
fun LensTag(l: Lens) = Text(l.label, color = C.lens(l), fontSize = 11.sp, fontWeight = FontWeight.Bold,
    modifier = Modifier.background(C.lensBg(l), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp))

@Composable
fun StatusPill(s: Status) {
    val (fg, bg) = when (s) { Status.SELESAI -> Color(0xFF0F766E) to Color(0xFFD8F0EC); Status.PROSES -> Color(0xFFB45309) to Color(0xFFFBEBD9); Status.BELUM -> C.Muted to Color(0xFFECE9E0) }
    Text(s.label, color = fg, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.background(bg, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp))
}

@Composable
fun Confirm(title: String, text: String, confirmLabel: String = "Hapus", onConfirm: () -> Unit, onDismiss: () -> Unit) =
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(text) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirmLabel, color = C.Danger, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } })

@Composable
fun Empty(text: String) = Text(text, color = C.Muted, modifier = Modifier.fillMaxWidth().padding(28.dp), fontSize = 14.sp)
