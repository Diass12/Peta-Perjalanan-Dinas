package id.tub.dinas.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.tub.dinas.ui.theme.C

/** Navigasi bawah: ikon emoji + label; tab aktif biru dengan garis bawah. selected = -1 bila tidak ada yang aktif. */
@Composable
fun CleanNav(items: List<Pair<String, String>>, selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().background(C.Surface)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(C.Line))
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(top = 6.dp, bottom = 4.dp)) {
            items.forEachIndexed { i, (icon, label) ->
                val on = i == selected
                Column(Modifier.weight(1f).clickable { Sfx.click(); onSelect(i) }.padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(icon, fontSize = 22.sp, modifier = Modifier.alpha(if (on) 1f else 0.6f))
                    Text(label, fontSize = 12.sp, maxLines = 1, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, color = if (on) Color(0xFF1E6FE8) else C.Muted)
                    Box(Modifier.padding(top = 3.dp).width(30.dp).height(3.dp).clip(CircleShape).background(if (on) Color(0xFF1E6FE8) else Color.Transparent))
                }
            }
        }
    }
}

/** Kartu statistik: label kecil, nilai "x/y", dan bar. */
@Composable
fun StatCard(label: String, value: Int, max: Int, orange: Boolean, modifier: Modifier = Modifier) {
    val c = if (orange) C.Amber else C.Brand
    val shape = RoundedCornerShape(16.dp)
    Column(modifier.background(C.Surface, shape).border(1.dp, C.Line, shape).padding(14.dp)) {
        Text(label, color = C.Muted, style = MaterialTheme.typography.labelSmall)
        Row(verticalAlignment = Alignment.Bottom) {
            Text("$value", color = C.Ink, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text("/$max", color = C.Muted, fontSize = 15.sp, modifier = Modifier.padding(bottom = 4.dp, start = 2.dp))
        }
        Spacer(Modifier.height(6.dp))
        SegBar(if (max <= 0) 0f else value.toFloat() / max, c)
    }
}

/** Ubin menu: ikon emoji di kotak berwarna, judul, dan keterangan singkat. */
@Composable
fun MenuTile(icon: String, tint: Color, title: String, subtitle: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(modifier.clip(shape).background(C.Surface, shape).border(1.dp, C.Line, shape).clickable { Sfx.menu(); onClick() }.padding(14.dp)) {
        Box(Modifier.size(44.dp).background(tint.copy(alpha = 0.14f), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) { Text(icon, fontSize = 22.sp) }
        Spacer(Modifier.height(10.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
        Text(subtitle, color = C.Muted, fontSize = 13.sp, lineHeight = 18.sp, maxLines = 2)
    }
}

/** Tombol ikon bulat kecil (emoji). */
@Composable
fun RoundIcon(icon: String, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clip(CircleShape).background(C.Surface).border(1.dp, C.Line, CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) { Text(icon, fontSize = 20.sp) }
}

/** Aksi cepat bulat: ikon emoji di lingkaran pastel, label di bawah. */
@Composable
fun QuickAction(icon: String, label: String, tint: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).clickable { Sfx.menu(); onClick() }.padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(58.dp).background(Brush.linearGradient(listOf(tint.copy(alpha = 0.10f), tint.copy(alpha = 0.26f))), CircleShape).border(1.dp, Color.White, CircleShape), contentAlignment = Alignment.Center) { Text(icon, fontSize = 26.sp) }
        Spacer(Modifier.height(6.dp))
        Text(label, fontSize = 12.sp, maxLines = 1, fontWeight = FontWeight.SemiBold, color = C.Ink)
    }
}

/** Latar pemandangan langit, gunung, dan laut untuk header beranda. */
@Composable
fun Pemandangan(modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier) {
        val w = size.width; val h = size.height
        drawRect(Brush.verticalGradient(listOf(Color(0xFF2B7BE4), Color(0xFF6DB8FF), Color(0xFFBFE3FF))))
        drawCircle(Color(0x33FFFFFF), radius = w * 0.10f, center = Offset(w * 0.85f, h * 0.14f))
        val gunung = Path().apply { moveTo(w * 0.40f, h * 0.80f); lineTo(w * 0.76f, h * 0.26f); lineTo(w * 1.12f, h * 0.80f); close() }
        drawPath(gunung, Color(0x995B86B5))
        drawRect(Brush.verticalGradient(listOf(Color(0xFF3AA6E8), Color(0xFF2178C8))), Offset(0f, h * 0.72f), Size(w, h * 0.28f))
        drawCircle(Color(0xFF2E8B57), radius = w * 0.20f, center = Offset(w * 0.00f, h * 0.92f))
        drawCircle(Color(0xFF3FA66A), radius = w * 0.12f, center = Offset(w * 0.20f, h * 1.00f))
    }
}

/** Kolom cari bulat dengan tombol panah biru. */
@Composable
fun PillSearch(value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier, onGo: () -> Unit = {}) {
    Row(modifier.fillMaxWidth().height(58.dp).clip(CircleShape).background(Color.White).padding(start = 18.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("🔍", fontSize = 20.sp)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(hint, color = Color(0xFF8A9BB5), fontSize = 16.sp)
            androidx.compose.foundation.text.BasicTextField(value, onChange, singleLine = true, textStyle = androidx.compose.ui.text.TextStyle(color = Color(0xFF0F2A55), fontSize = 17.sp),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search), keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { onGo() }), modifier = Modifier.fillMaxWidth())
        }
        Box(Modifier.size(46.dp).clip(CircleShape).background(Color(0xFF1E6FE8)).clickable { onGo() }, contentAlignment = Alignment.Center) { Text("→", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold) }
    }
}

/** Kolom cari besar bergaya travel: ikon kaca pembesar, sudut 18 dp. */
@Composable
fun SearchBar(value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier) {
    androidx.compose.material3.OutlinedTextField(value, onChange, modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(18.dp),
        placeholder = { Text(hint, color = C.Muted, fontSize = 15.sp) }, leadingIcon = { Text("🔍", fontSize = 18.sp) },
        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(focusedBorderColor = C.Accent, unfocusedBorderColor = C.Line, cursorColor = C.Accent,
            focusedContainerColor = C.Surface, unfocusedContainerColor = C.Surface, focusedTextColor = C.Ink, unfocusedTextColor = C.Ink))
}
