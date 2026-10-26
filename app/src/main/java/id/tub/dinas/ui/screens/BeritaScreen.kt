package id.tub.dinas.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.tub.dinas.MainViewModel
import id.tub.dinas.news.Berita
import id.tub.dinas.news.GambarUrl
import id.tub.dinas.news.NewsFeed
import id.tub.dinas.news.Sumber
import id.tub.dinas.ui.components.PillSearch
import id.tub.dinas.ui.components.Sfx
import id.tub.dinas.ui.components.Text
import id.tub.dinas.ui.theme.C
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

private val Merah = Color(0xFFD71920)
private val Biru = Color(0xFF1E6FE8)
private val KATEGORI = listOf("Semua", "Terkini", "Nasional", "Ekonomi", "Teknologi", "Internasional", "Olahraga", "Hiburan", "Lainnya")
private val WARNA = mapOf("Terkini" to Color(0xFFD71920), "Nasional" to Color(0xFF1E6FE8), "Ekonomi" to Color(0xFF0E9F6E), "Teknologi" to Color(0xFF7C3AED),
    "Internasional" to Color(0xFFEA580C), "Olahraga" to Color(0xFF0891B2), "Hiburan" to Color(0xFFDB2777), "Gempa" to Color(0xFFB91C1C))

private fun buka(ctx: android.content.Context, url: String) {
    if (url.startsWith("https://") || url.startsWith("http://")) runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

@Composable
fun WawasanTab(vm: MainViewModel) {
    var mode by rememberSaveable { mutableStateOf(0) }
    Column(Modifier.fillMaxSize().background(C.Bg)) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ModeChip("📰 Berita", mode == 0) { mode = 0 }
            ModeChip("🎭 Hiburan", mode == 1) { mode = 1 }
        }
        if (mode == 0) BeritaTab(vm) else HiburanTab(vm)
    }
}

@Composable
private fun ModeChip(t: String, on: Boolean, f: () -> Unit) {
    Box(Modifier.clip(CircleShape).background(if (on) Biru else C.Chip).clickable { Sfx.click(); f() }.padding(horizontal = 14.dp, vertical = 7.dp)) {
        Text(t, color = if (on) Color.White else C.Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun BeritaTab(vm: MainViewModel) {
    val ctx = LocalContext.current
    var extra by remember { mutableStateOf(vm.settings.newsExtra) }
    val sumberExtra = remember(extra) { NewsFeed.parseExtra(extra) }
    val semua = remember(sumberExtra) { NewsFeed.DEFAULT + sumberExtra }
    var kategori by rememberSaveable { mutableStateOf("Semua") }
    var penerbit by rememberSaveable { mutableStateOf("Semua") }
    var cari by rememberSaveable { mutableStateOf("") }
    var muat by remember { mutableIntStateOf(0) }
    var memuat by remember { mutableStateOf(true) }
    var daftar by remember { mutableStateOf<List<Berita>>(emptyList()) }
    var gempa by remember { mutableStateOf<Berita?>(null) }
    var gagal by remember { mutableIntStateOf(0) }
    var dialog by remember { mutableStateOf(false) }

    // Muat sumber sesuai pilihan; sumber yang gagal dilewati agar sisanya tetap tampil.
    LaunchedEffect(muat, kategori, penerbit, semua) {
        memuat = true
        val pilih = semua.filter {
            (kategori == "Semua" || it.kategori == kategori) && (penerbit == "Semua" || it.penerbit == penerbit) &&
                (kategori != "Semua" || penerbit != "Semua" || it.utama || it.kategori == "Lainnya")
        }
        var g = 0
        val hasil = coroutineScope {
            pilih.map { s -> async { runCatching { NewsFeed.ambil(s) }.getOrElse { g++; emptyList() } } }.awaitAll()
        }.flatten().distinctBy { it.link }.sortedByDescending { it.ms }
        daftar = hasil; gagal = g; memuat = false
    }
    LaunchedEffect(muat) { gempa = runCatching { NewsFeed.ambil(NewsFeed.GEMPA).firstOrNull() }.getOrNull() }

    val tampil = remember(daftar, cari) { if (cari.isBlank()) daftar else daftar.filter { it.judul.contains(cari, true) || it.penerbit.contains(cari, true) } }
    val hero = tampil.firstOrNull { it.gambar.isNotBlank() } ?: tampil.firstOrNull()
    val lain = tampil.filter { it !== hero }
    val penerbitAda = remember(semua) { listOf("Semua") + semua.map { it.penerbit }.distinct() }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("▰", color = Merah, fontSize = 22.sp, fontWeight = FontWeight.Black)
                        Text("▰", color = Color(0xFF12305F), fontSize = 22.sp, fontWeight = FontWeight.Black)
                        Spacer(Modifier.width(8.dp))
                        Text("Insights", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = C.Ink)
                    }
                    Text("Informasi untuk Operasi di Lapangan", color = C.Muted, fontSize = 13.sp)
                }
                Box(Modifier.size(44.dp).clip(CircleShape).background(Color.White).clickable { dialog = true }, contentAlignment = Alignment.Center) { Text("＋", fontSize = 20.sp, color = Biru, fontWeight = FontWeight.Bold) }
            }
        }
        item { PillSearch(cari, { cari = it }, "Cari berita…", Modifier.height(50.dp)) }
        item {
            val t = gempa ?: tampil.firstOrNull()
            if (t != null) Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White).clickable { buka(ctx, t.link) }, verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.background(Merah).padding(horizontal = 12.dp, vertical = 12.dp)) { Text("TERKINI", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold) }
                Text(t.judul, Modifier.weight(1f).padding(horizontal = 10.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, color = C.Ink, fontWeight = FontWeight.Medium)
            }
        }
        if (hero != null) item {
            GambarUrl(hero.gambar, Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(20.dp)).clickable { buka(ctx, hero.link) }, Color(0xFF12305F), scrim = true) {
                Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                    Tag(hero.kategori.ifEmpty { "Berita" })
                    Spacer(Modifier.height(6.dp))
                    Text(hero.judul, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis, lineHeight = 23.sp)
                    Text(hero.penerbit + (if (hero.waktu.isNotBlank()) " • " + hero.waktu else ""), color = Color(0xFFE5E7EB), fontSize = 12.sp)
                }
            }
        }
        item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { KATEGORI.forEach { k -> KChip(k, k == kategori, true) { kategori = k } } } }
        item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { penerbitAda.forEach { k -> KChip(k, k == penerbit, false) { penerbit = k } } } }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(4.dp).height(20.dp).background(Merah))
                Text("Berita terbaru", Modifier.padding(start = 8.dp).weight(1f), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = C.Ink)
                Text(if (memuat) "Memuat…" else "Muat ulang", color = Biru, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable(enabled = !memuat) { muat++ })
            }
        }
        if (!memuat && tampil.isEmpty()) item { Text(if (gagal > 0) "Berita belum bisa dimuat. Periksa sambungan internet lalu coba muat ulang." else "Tidak ada berita yang cocok.", color = C.Muted, fontSize = 14.sp) }
        items(lain.size.coerceAtMost(60)) { i -> BarisBerita(lain[i]) { buka(ctx, lain[i].link) } }
        if (gagal > 0 && tampil.isNotEmpty()) item { Text("$gagal sumber tidak dapat dimuat saat ini.", color = C.Muted, fontSize = 12.sp) }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(4.dp).height(20.dp).background(Merah))
                Text("Info penting", Modifier.padding(start = 8.dp), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = C.Ink)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                InfoTile("🌤️", Color(0xFFDBEAFE), "BMKG Cuaca", "Prakiraan cuaca", Modifier.weight(1f)) { buka(ctx, NewsFeed.BMKG_CUACA) }
                InfoTile("🌋", Color(0xFFFEE2E2), "BMKG Gempa", gempa?.judul ?: "Gempa terkini", Modifier.weight(1f)) { buka(ctx, NewsFeed.BMKG_GEMPA_HALAMAN) }
            }
        }
        item { Text("Berita dimuat dari RSS publik masing-masing penerbit; hak cipta milik penerbit. Ketuk berita untuk membaca di situs aslinya.", color = C.Muted, fontSize = 11.sp, lineHeight = 15.sp) }
    }

    if (dialog) {
        var teks by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { dialog = false }, title = { Text("＋ Sumber RSS") },
            text = { Column {
                Text("Satu per baris: nama|alamat https. Contoh: Kompas|https://www.kompas.com/rss", color = C.Muted, fontSize = 12.sp)
                OutlinedTextField(teks, { teks = it }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("nama|https://…") })
            } },
            confirmButton = { TextButton(onClick = {
                val baru = NewsFeed.parseExtra(teks)
                if (baru.isNotEmpty()) { extra = (extra.trim() + "\n" + teks.trim()).trim(); vm.settings.newsExtra = extra }
                dialog = false
            }) { Text("Simpan") } },
            dismissButton = { TextButton(onClick = { dialog = false }) { Text("Batal") } })
    }
}

@Composable
private fun Tag(t: String) {
    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(WARNA[t] ?: Biru).padding(horizontal = 8.dp, vertical = 3.dp)) { Text(t, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun KChip(t: String, on: Boolean, merah: Boolean, f: () -> Unit) {
    val warna = if (merah) Merah else Biru
    Box(Modifier.clip(CircleShape).background(if (on) warna else Color.White).clickable { Sfx.click(); f() }.padding(horizontal = 14.dp, vertical = 8.dp)) {
        Text(t, color = if (on) Color.White else Color(0xFF334155), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun BarisBerita(b: Berita, f: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).clickable { f() }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        GambarUrl(b.gambar, Modifier.size(width = 100.dp, height = 84.dp).clip(RoundedCornerShape(12.dp)), Color(0xFFDCE6F5), false) { ada -> if (!ada) Text("📰", Modifier.align(Alignment.Center), fontSize = 30.sp) }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            if (b.kategori.isNotBlank()) Tag(b.kategori)
            Text(b.judul, Modifier.padding(top = 4.dp), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F2A55), maxLines = 3, overflow = TextOverflow.Ellipsis, lineHeight = 19.sp)
            Text(b.penerbit + (if (b.waktu.isNotBlank()) " • " + b.waktu else ""), color = C.Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
        }
        Text("›", color = C.Muted, fontSize = 22.sp, modifier = Modifier.padding(start = 6.dp))
    }
}

@Composable
private fun InfoTile(ikon: String, tint: Color, judul: String, ket: String, modifier: Modifier, f: () -> Unit) {
    Row(modifier.clip(RoundedCornerShape(16.dp)).background(Color.White).clickable { f() }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).background(tint, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) { Text(ikon, fontSize = 22.sp) }
        Column(Modifier.padding(start = 10.dp)) {
            Text(judul, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF0F2A55))
            Text(ket, color = C.Muted, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
