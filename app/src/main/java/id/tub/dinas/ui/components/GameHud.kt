package id.tub.dinas.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.tub.dinas.MainViewModel
import id.tub.dinas.ui.theme.ThemeState

class PlayerInfo(val lv: Int, val title: String, val pts: Int, val from: Int, val to: Int) {
    val fraction: Float get() = (pts - from).toFloat() / (to - from)
}

@Composable
fun rememberPlayer(vm: MainViewModel): PlayerInfo {
    val xp by remember { vm.repo.observeXp() }.collectAsState(Triple(0, 0, 0))
    val pts = xp.first * 10 + xp.second * 20 + xp.third * 50
    val lv = Math.floor(Math.sqrt(pts / 25.0)).toInt() + 1
    val title = when { lv >= 20 -> "Veteran Lapangan"; lv >= 10 -> "Auditor Lapangan"; lv >= 5 -> "Surveyor"; else -> "Pemula Lapangan" }
    return PlayerInfo(lv, title, pts, 25 * (lv - 1) * (lv - 1), 25 * lv * lv)
}

private val Blue = Color(0xFF2563EB)
private val Orange = Color(0xFFF59E0B)

/** Header ala desain: avatar + lencana level, nama + perisai emas, jabatan, dan tombol bulat di kanan. */
@Composable
fun GameHeader(p: PlayerInfo, trailing: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .background(Brush.horizontalGradient(listOf(Color(0xFF1D4ED8), Color(0xFF38A3F5))), RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
            .statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(contentAlignment = Alignment.BottomCenter) {
            Avatar(ThemeState.avatar, 52.dp, Modifier.padding(bottom = 8.dp), ring = Color.White)
            Text("Lv.${p.lv}", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.background(Color(0xFF0B2545), RoundedCornerShape(10.dp)).border(1.dp, Color(0xFFFFD166), RoundedCornerShape(10.dp)).padding(horizontal = 6.dp, vertical = 1.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(ThemeState.playerName, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(6.dp))
                Pic(id.tub.dinas.R.drawable.ic_lencana, 20.dp)
            }
            Text(p.title, color = Color(0xFFDCEBFF), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(3.dp))
            XpBar(p.fraction, Modifier.fillMaxWidth(0.7f).height(8.dp))
        }
        Box(Modifier.size(44.dp).clip(CircleShape).background(Color(0x33FFFFFF)).border(1.dp, Color(0x66FFFFFF), CircleShape), contentAlignment = Alignment.Center) { trailing() }
    }
}

/** Navigasi bawah putih: tab aktif biru dan tebal. selected = -1 bila tidak ada yang aktif. */
@Composable
fun WhiteNav(items: List<Pair<Int, String>>, selected: Int, onSelect: (Int) -> Unit) {
    val shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    Row(Modifier.fillMaxWidth().background(Color.White, shape).border(1.dp, Color(0x22000000), shape).navigationBarsPadding().padding(vertical = 8.dp)) {
        items.forEachIndexed { i, (icon, label) ->
            val on = i == selected
            Column(Modifier.weight(1f).clickable { Sfx.click(); onSelect(i) }.padding(vertical = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Pic(icon, if (on) 34.dp else 28.dp, if (on) Modifier else Modifier.alpha(0.55f))
                Text(label, fontSize = 11.sp, maxLines = 1, fontWeight = if (on) FontWeight.ExtraBold else FontWeight.Medium, color = if (on) Blue else Color(0xFF94A3B8))
            }
        }
    }
}

/** Kartu statistik: label kecil, nilai "x/y", dan bar. */
@Composable
fun StatCard(label: String, value: Int, max: Int, orange: Boolean, modifier: Modifier = Modifier) {
    val c = if (orange) Orange else Blue
    Column(modifier.background(Color.White, RoundedCornerShape(22.dp)).padding(14.dp)) {
        Text(label, color = Color(0xFF64748B), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
        Row(verticalAlignment = Alignment.Bottom) {
            Text("$value", color = Color(0xFF0F172A), fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
            Text("/$max", color = Color(0xFF94A3B8), fontSize = 14.sp, modifier = Modifier.padding(bottom = 4.dp, start = 2.dp))
        }
        Spacer(Modifier.height(6.dp))
        val f = if (max <= 0) 0f else (value.toFloat() / max).coerceIn(0f, 1f)
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFFE2E8F0))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(f).background(c, RoundedCornerShape(4.dp)))
        }
    }
}
