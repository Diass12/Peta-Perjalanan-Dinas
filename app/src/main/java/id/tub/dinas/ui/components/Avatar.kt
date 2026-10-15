package id.tub.dinas.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.tub.dinas.R
import id.tub.dinas.ui.theme.C

/** Karakter yang bisa dipilih sebagai avatar. */
val AVATARS: List<Pair<Int, String>> = listOf(
    R.drawable.av_1 to "Surveyor Muda",
    R.drawable.av_2 to "Akira",
    R.drawable.av_3 to "Teknisi",
    R.drawable.av_4 to "Tim Audit",
    R.drawable.av_5 to "Kai",
    R.drawable.av_6 to "Navigator",
    R.drawable.av_7 to "Auditor Senior",
)

@Composable
fun Avatar(index: Int, size: Dp, modifier: Modifier = Modifier, ring: Color = Color(0xFFFFD166)) {
    val i = index.coerceIn(0, AVATARS.lastIndex)
    Image(painterResource(AVATARS[i].first), AVATARS[i].second,
        modifier.size(size).clip(CircleShape).border(2.dp, ring, CircleShape), contentScale = ContentScale.Crop)
}

/** Pemilih avatar: deretan lingkaran, yang terpilih diberi cincin emas. */
@Composable
fun AvatarPicker(selected: Int, onSelect: (Int) -> Unit) {
    @OptIn(ExperimentalLayoutApi::class)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AVATARS.forEachIndexed { i, (res, name) ->
            Column(Modifier.clickable { onSelect(i) }, horizontalAlignment = Alignment.CenterHorizontally) {
                Image(painterResource(res), name,
                    Modifier.size(68.dp).clip(CircleShape).border(if (i == selected) 3.dp else 1.dp, if (i == selected) Color(0xFFFFD166) else C.Line, CircleShape),
                    contentScale = ContentScale.Crop)
                Text(name, fontSize = 10.sp, maxLines = 1, fontWeight = if (i == selected) FontWeight.Bold else FontWeight.Normal, color = C.Muted)
            }
        }
    }
}
