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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.tub.dinas.ui.theme.C

/** Navigasi bawah: ikon emoji + label; tab aktif biru. selected = -1 bila tidak ada yang aktif. */
@Composable
fun CleanNav(items: List<Pair<String, String>>, selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().background(Color.White)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(C.Line))
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(vertical = 6.dp)) {
            items.forEachIndexed { i, (icon, label) ->
                val on = i == selected
                Column(Modifier.weight(1f).clickable { Sfx.click(); onSelect(i) }.padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.clip(RoundedCornerShape(14.dp)).background(if (on) C.BrandSoft else Color.Transparent).padding(horizontal = 18.dp, vertical = 3.dp)) { Text(icon, fontSize = 20.sp) }
                    Text(label, fontSize = 12.sp, maxLines = 1, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, color = if (on) C.Brand else C.Muted)
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
    Column(modifier.background(Color.White, shape).border(1.dp, C.Line, shape).padding(14.dp)) {
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
    val shape = RoundedCornerShape(18.dp)
    Column(modifier.clip(shape).background(Color.White, shape).border(1.dp, C.Line, shape).clickable { Sfx.menu(); onClick() }.padding(14.dp)) {
        Box(Modifier.size(44.dp).background(tint.copy(alpha = 0.14f), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) { Text(icon, fontSize = 22.sp) }
        Spacer(Modifier.height(10.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
        Text(subtitle, color = C.Muted, fontSize = 13.sp, lineHeight = 18.sp, maxLines = 2)
    }
}

/** Tombol ikon bulat kecil (emoji). */
@Composable
fun RoundIcon(icon: String, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clip(CircleShape).background(Color.White).border(1.dp, C.Line, CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) { Text(icon, fontSize = 20.sp) }
}
