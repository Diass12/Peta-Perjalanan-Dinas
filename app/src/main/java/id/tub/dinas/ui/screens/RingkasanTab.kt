package id.tub.dinas.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.tub.dinas.domain.DinasSnapshot
import id.tub.dinas.domain.Lens
import id.tub.dinas.domain.SummaryCalc
import id.tub.dinas.ui.components.*
import id.tub.dinas.ui.theme.C

@Composable
fun RingkasanTab(s: DinasSnapshot) {
    val st = SummaryCalc.calc(s.temuan)
    val list = s.temuan.sortedBy { it.order }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Panel {
                Text(SummaryCalc.sentence(st, s.titik.size), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Tile("${st.total}", "Temuan", Modifier.weight(1f))
                    Tile("${s.titik.size}", "Titik", Modifier.weight(1f))
                    Tile("${s.foto.size}", "Foto", Modifier.weight(1f))
                    Tile("${st.done}", "Selesai", Modifier.weight(1f))
                }
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Lens.values().forEach { l -> Text("${l.label}: ${st.perLens[l] ?: 0}", color = C.lens(l), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.background(C.lensBg(l), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp)) }
                }
            }
        }
        item {
            Panel {
                Text("PROGRES TINDAK LANJUT", style = MaterialTheme.typography.labelSmall, color = C.Muted)
                Spacer(Modifier.height(6.dp))
                val tot = maxOf(st.total, 1).toFloat()
                BarRow("Selesai", st.done, st.total, st.done / tot, Color(0xFF22C55E))
                BarRow("Proses", st.inProgress, st.total, st.inProgress / tot, Color(0xFFFBBF24))
                BarRow("Belum", st.notStarted, st.total, st.notStarted / tot, Color(0xFF4FA3FF))
                Spacer(Modifier.height(10.dp))
                Text("CAKUPAN LENSA", style = MaterialTheme.typography.labelSmall, color = C.Muted)
                Spacer(Modifier.height(6.dp))
                Lens.values().forEach { l -> BarRow(l.label, st.perLens[l] ?: 0, st.total, (st.perLens[l] ?: 0) / tot, C.lens(l)) }
            }
        }
        itemsIndexed(list, key = { _, t -> t.id }) { i, t ->
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}. ${t.title}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    StatusPill(t.status)
                }
                Lens.values().forEach { l ->
                    val txt = SummaryCalc.lensText(t, l)
                    if (txt != null) {
                        Column(Modifier.padding(top = 8.dp).fillMaxWidth().background(C.lensBg(l), RoundedCornerShape(8.dp)).padding(10.dp)) {
                            Text(l.label.uppercase(), color = C.lens(l), style = MaterialTheme.typography.labelSmall)
                            Text(txt, color = if (t.lens[l].isNullOrBlank()) C.Muted else C.Ink)
                        }
                    }
                }
                if (t.activeLenses.isEmpty()) Text("Belum ada lensa dipilih.", color = C.Muted, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

@Composable
private fun Tile(value: String, label: String, modifier: Modifier) {
    Column(modifier.background(C.Bg, RoundedCornerShape(10.dp)).padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(label, color = C.Muted, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun BarRow(label: String, n: Int, total: Int, frac: Float, color: Color) {
    Column(Modifier.padding(bottom = 8.dp)) {
        Row { Text(label, Modifier.weight(1f), fontWeight = FontWeight.SemiBold); Text("$n / $total", color = C.Muted) }
        Spacer(Modifier.height(3.dp))
        SegBar(frac, color)
    }
}
