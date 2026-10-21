package id.tub.dinas.ui.screens

import id.tub.dinas.ui.components.Text
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.tub.dinas.MainViewModel
import id.tub.dinas.domain.DinasSnapshot
import id.tub.dinas.ui.Screen
import id.tub.dinas.ui.components.*
import id.tub.dinas.ui.theme.C
import id.tub.dinas.ui.theme.ThemeState

data class Statistik(val dinas: Int = 0, val hari: Int = 0, val km: Double = 0.0, val gps: Int = 0, val foto: Int = 0, val temuan: Int = 0, val selesai: Int = 0) {
    val poin: Int get() = dinas * 10 + gps * 2 + foto + temuan * 3 + selesai * 2
    val level: String get() = when { poin >= 400 -> "Lead Auditor"; poin >= 150 -> "Senior Auditor"; poin >= 40 -> "Auditor"; else -> "Junior Auditor" }
    val berikutnya: Pair<String, Int>? get() = when { poin >= 400 -> null; poin >= 150 -> "Lead Auditor" to 400; poin >= 40 -> "Senior Auditor" to 150; else -> "Auditor" to 40 }
}

fun hitungStatistik(snaps: List<DinasSnapshot>): Statistik {
    var hari = 0; var km = 0.0
    for (s in snaps) {
        val a = s.dinas.startMs; val b = s.dinas.endMs
        if (a != null) hari += if (b != null && b >= a) (((b - a) / 86_400_000L).toInt() + 1) else 1
        val g = s.titik.sortedBy { it.order }.mapNotNull { it.geo }
        for (i in 1 until g.size) km += km(g[i - 1], g[i])
    }
    return Statistik(snaps.size, hari, km, snaps.sumOf { s -> s.titik.count { it.geo != null } + s.temuan.count { it.geo != null } },
        snaps.sumOf { it.foto.size }, snaps.sumOf { it.temuan.size }, snaps.sumOf { s -> s.temuan.count { it.status == id.tub.dinas.domain.Status.SELESAI } })
}

@Composable
fun rememberSnapshots(vm: MainViewModel): State<List<DinasSnapshot>?> {
    val list by vm.dinasList.collectAsState()
    val out = remember { mutableStateOf<List<DinasSnapshot>?>(null) }
    LaunchedEffect(list) { out.value = list.mapNotNull { vm.repo.snapshot(it.id) } }
    return out
}

@Composable
fun ProfilScreen(vm: MainViewModel) {
    val snaps by rememberSnapshots(vm)
    val st = remember(snaps) { hitungStatistik(snaps.orEmpty()) }
    val reg by vm.aset.register.collectAsState()
    val diaudit = reg.count { it.state != id.tub.dinas.domain.AuditState.PENDING }
    var nama by remember { mutableStateOf(ThemeState.playerName) }
    var jabatan by remember { mutableStateOf(vm.settings.jabatan) }
    Column(Modifier.fillMaxSize().background(C.Bg)) {
        Header("Profil", onBack = { vm.pop() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.fillMaxWidth().background(C.Grad, RoundedCornerShape(20.dp)).padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(56.dp).background(Color.White.copy(alpha = 0.16f), CircleShape), contentAlignment = Alignment.Center) {
                        Text(nama.take(1).uppercase().ifEmpty { "A" }, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(nama.ifEmpty { "Auditor" }, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("${st.level} · $jabatan", color = Color(0xCCFFFFFF), fontSize = 14.sp)
                    }
                }
                Spacer(Modifier.height(14.dp))
                val nx = st.berikutnya
                if (nx == null) Text("Level tertinggi tercapai.", color = Color(0xCCFFFFFF), fontSize = 13.sp)
                else {
                    Box(Modifier.fillMaxWidth().height(8.dp).background(Color.White.copy(alpha = 0.18f), RoundedCornerShape(4.dp))) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth((st.poin.toFloat() / nx.second).coerceIn(0.03f, 1f)).background(Color(0xFF34D399), RoundedCornerShape(4.dp)))
                    }
                    Text("${st.poin} / ${nx.second} poin menuju ${nx.first}", color = Color(0xCCFFFFFF), fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                }
            }
            Panel {
                Text("Statistik perjalanan", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                val rows = listOf(
                    "Total jarak tempuh" to String.format(java.util.Locale("id", "ID"), "%.1f km", st.km),
                    "Total waktu dinas" to "${st.hari} hari ( ${st.dinas} dinas )",
                    "Titik GPS terkunci" to "${st.gps}",
                    "Foto audit terkumpul" to "${st.foto}",
                    "Temuan dicatat" to "${st.temuan} (${st.selesai} verified)",
                    "Aset teraudit" to "$diaudit dari ${reg.size}",
                )
                rows.forEachIndexed { i, (k, v) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) { Text(k, Modifier.weight(1f), color = C.Muted); Text(v, fontWeight = FontWeight.SemiBold) }
                    if (i < rows.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(C.Line))
                }
                if (snaps == null) Text("Menghitung…", color = C.Muted, fontSize = 12.sp)
            }
            Panel {
                Text("Lencana prestasi", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                val badges = listOf(
                    Triple("🧭", "Dinas pertama", st.dinas >= 1),
                    Triple("🗺️", "5 dinas", st.dinas >= 5),
                    Triple("📍", "25 titik GPS", st.gps >= 25),
                    Triple("🛣️", "100 km", st.km >= 100.0),
                    Triple("📸", "100 foto", st.foto >= 100),
                    Triple("✅", "10 temuan verified", st.selesai >= 10),
                    Triple("📦", "50 aset teraudit", diaudit >= 50),
                    Triple("🏅", "Senior Auditor", st.poin >= 150),
                )
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    badges.forEach { (ic, t, on) ->
                        Column(Modifier.width(92.dp).background(if (on) C.OkSoft else C.Chip, RoundedCornerShape(14.dp)).padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(ic, fontSize = 26.sp, modifier = Modifier.then(if (on) Modifier else Modifier.background(Color.Transparent)), color = if (on) Color.Unspecified else Color.Gray)
                            Text(t, fontSize = 11.sp, color = if (on) C.Ok else C.Muted, fontWeight = FontWeight.SemiBold, maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                }
                Text("Lencana terbuka otomatis dari data Anda; tidak ada data yang dikirim ke mana pun.", color = C.Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            }
            Panel {
                Text("Identitas", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Field(nama, { nama = it.take(32); ThemeState.playerName = nama; vm.settings.playerName = nama }, "Nama lengkap", singleLine = true)
                Spacer(Modifier.height(8.dp))
                Field(jabatan, { jabatan = it.take(40); vm.settings.jabatan = jabatan }, "Jabatan", singleLine = true)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SoftButton("🧳 Dokumen", Modifier.weight(1f)) { vm.push(Screen.Dokumen) }
                SoftButton("🖼️ Galeri", Modifier.weight(1f)) { vm.push(Screen.Galeri) }
                SoftButton("⚙️ Pengaturan", Modifier.weight(1f)) { vm.push(Screen.Settings) }
            }
        }
    }
}
