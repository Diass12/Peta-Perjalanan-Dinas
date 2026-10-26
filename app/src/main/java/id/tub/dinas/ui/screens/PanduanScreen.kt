package id.tub.dinas.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.tub.dinas.MainViewModel
import id.tub.dinas.domain.DinasModel
import id.tub.dinas.panduan.FotoLatar
import id.tub.dinas.panduan.PanduanWeb
import id.tub.dinas.ui.DetailTab
import id.tub.dinas.ui.Screen
import id.tub.dinas.ui.components.*

private val BIRU = Color(0xFF1E6FE8)
private val NAVY = Color(0xFF0F2A55)
private val LATAR = Color(0xFFF3F8FF)
private val CONTOH = listOf("Bali", "Aceh", "Ternate", "Batam", "Labuan Bajo", "Halmahera", "Yogyakarta", "Lombok", "Pontianak", "Banjarmasin")

private fun buka(ctx: Context, url: String) { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
private fun mapsCari(q: String) = "https://www.google.com/maps/search/?api=1&query=" + Uri.encode(q)

@Composable
fun PanduanTab(vm: MainViewModel, aktif: DinasModel?) {
    var q by rememberSaveable { mutableStateOf("") }
    var kirim by rememberSaveable { mutableStateOf("") }
    var fav by remember { mutableStateOf(vm.settings.panduanFav) }
    fun ubahFav(id: String) { fav = if (id in fav) fav - id else fav + id; vm.settings.panduanFav = fav }
    BackHandler(enabled = kirim.isNotEmpty()) { kirim = ""; q = "" }
    fun cari(t: String) { val x = t.trim(); if (x.length >= 2) { q = x; kirim = x } }

    LazyColumn(Modifier.fillMaxSize().background(LATAR), contentPadding = PaddingValues(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { HeaderLangit(q, { q = it }, { cari(q) }) }
        if (kirim.isEmpty()) {
            item { Info("Ketik nama kota atau tempat tujuan, lalu ketuk panah. Hasilnya diambil dari internet.") }
            item {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Coba cari", fontWeight = FontWeight.Bold, color = NAVY, fontSize = 16.sp)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { CONTOH.forEach { Chip(it, false) { cari(it) } } }
                }
            }
            val favQ = fav.filter { it.startsWith("q:") }.map { it.removePrefix("q:") }
            if (favQ.isNotEmpty()) item {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("♥ Destinasi tersimpan", fontWeight = FontWeight.Bold, color = NAVY, fontSize = 16.sp)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { favQ.forEach { Chip("♥ $it", true) { cari(it) } } }
                }
            }
        } else {
            item { Info("Hasil untuk “$kirim”. Data wisata, kuliner, dan hotel dilihat langsung di situs tujuan.") }
            item { Hasil(vm, aktif, kirim, "q:" + kirim.lowercase() in fav) { ubahFav("q:" + kirim.lowercase()) } }
        }
    }
}

@Composable
private fun HeaderLangit(q: String, onQ: (String) -> Unit, onCari: () -> Unit) {
    val bentuk = RoundedCornerShape(bottomStart = 36.dp, bottomEnd = 36.dp)
    Box(Modifier.fillMaxWidth().clip(bentuk)) {
        Canvas(Modifier.matchParentSize()) {
            val w = size.width; val h = size.height
            drawRect(Brush.verticalGradient(listOf(Color(0xFF2B7BE4), Color(0xFF6DB8FF), Color(0xFFBFE3FF))))
            val gunung = Path().apply { moveTo(w * 0.42f, h * 0.78f); lineTo(w * 0.75f, h * 0.30f); lineTo(w * 1.1f, h * 0.78f); close() }
            drawPath(gunung, Color(0x995B86B5))
            drawRect(Brush.verticalGradient(listOf(Color(0xFF3AA6E8), Color(0xFF2178C8))), Offset(0f, h * 0.70f), androidx.compose.ui.geometry.Size(w, h * 0.30f))
            drawCircle(Color(0xFF2E8B57), radius = w * 0.22f, center = Offset(w * 0.02f, h * 0.88f))
            drawCircle(Color(0xFF3FA66A), radius = w * 0.14f, center = Offset(w * 0.22f, h * 0.95f))
            drawCircle(Color(0x33FFFFFF), radius = w * 0.10f, center = Offset(w * 0.85f, h * 0.12f))
        }
        Text("✈️", fontSize = 26.sp, modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 70.dp, end = 36.dp))
        Column(Modifier.statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 26.dp)) {
            Text("☀️ Jelajahi. Rencanakan. Abadikan.", color = Color.White, fontSize = 14.sp)
            Spacer(Modifier.height(6.dp))
            Text("Mau pergi ke", color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 40.sp)
            Text("mana?", color = Color(0xFFFFD43B), fontSize = 46.sp, fontStyle = FontStyle.Italic, fontFamily = FontFamily.Cursive, fontWeight = FontWeight.Bold, lineHeight = 48.sp)
            Box(Modifier.width(120.dp).height(5.dp).clip(CircleShape).background(Color(0xFFFFD43B)))
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth().height(60.dp).clip(CircleShape).background(Color.White).padding(start = 18.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("🔍", fontSize = 20.sp)
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (q.isEmpty()) Text("Cari kota atau tempat tujuan…", color = Color(0xFF8A9BB5), fontSize = 16.sp)
                    BasicTextField(q, onQ, singleLine = true, textStyle = TextStyle(color = NAVY, fontSize = 17.sp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { onCari() }), modifier = Modifier.fillMaxWidth())
                }
                Box(Modifier.size(48.dp).clip(CircleShape).background(BIRU).clickable { Sfx.menu(); onCari() }, contentAlignment = Alignment.Center) { Text("→", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun Info(teks: String) {
    Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0xFFE6F0FD)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Color.White), contentAlignment = Alignment.Center) { Text("📍", fontSize = 22.sp) }
        Spacer(Modifier.width(12.dp))
        Text(teks, color = NAVY, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.weight(1f))
        Text("🗺️", fontSize = 32.sp, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun Tile(ikon: String, judul: String, sub: String, a: Color, b: Color, tanda: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.clip(RoundedCornerShape(22.dp)).background(Brush.linearGradient(listOf(a, b))).clickable { Sfx.menu(); onClick() }.padding(16.dp)) {
        Column {
            Text(ikon, fontSize = 40.sp)
            Spacer(Modifier.height(10.dp))
            Text(judul, color = NAVY, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 22.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(sub, color = Color(0xFF5B6B85), fontSize = 12.sp, lineHeight = 16.sp, maxLines = 2)
        }
        Text("›", color = tanda, fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterEnd))
    }
}

@Composable
private fun Hasil(vm: MainViewModel, aktif: DinasModel?, q: String, simpan: Boolean, ubahSimpan: () -> Unit) {
    val ctx = LocalContext.current
    var minta by remember(q) { mutableStateOf(false) }
    var gagal by remember(q) { mutableStateOf(false) }
    var selesai by remember(q) { mutableStateOf(false) }
    val ringkas by produceState<PanduanWeb.Ringkas?>(null, q, minta) {
        value = null; gagal = false; selesai = false
        if (minta) {
            value = try { PanduanWeb.ringkas(q) } catch (e: java.io.IOException) { gagal = true; null } catch (e: RuntimeException) { gagal = true; null }
            selesai = true
        }
    }
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Brush.horizontalGradient(listOf(Color(0xFF1E6FE8), Color(0xFF4A9BF5)))).clickable { Sfx.menu(); minta = true }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(54.dp).clip(CircleShape).border(2.dp, Color.White, CircleShape), contentAlignment = Alignment.Center) { Text("🌐", fontSize = 28.sp) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Cari “$q” di internet", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 2)
                Text("Ringkasan dan foto dari Wikipedia", color = Color(0xE6FFFFFF), fontSize = 13.sp)
            }
            Text("›", color = Color.White, fontSize = 30.sp)
        }
        if (minta) {
            val h = ringkas
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color.White).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    h != null -> {
                        FotoLatar("cari:" + q.lowercase(), q, q, BIRU, Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(16.dp))) { f ->
                            if (f == null) Text("🌐", fontSize = 40.sp, modifier = Modifier.align(Alignment.Center))
                            else Text("📷 ${f.kredit} · Wikimedia Commons", color = Color(0xE6FFFFFF), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.align(Alignment.BottomStart).clickable { buka(ctx, "https://commons.wikimedia.org/wiki/File:" + Uri.encode(f.berkas)) }.padding(8.dp))
                        }
                        Text(h.judul, color = NAVY, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text(h.isi, color = NAVY, lineHeight = 22.sp)
                        Text("Sumber: Wikipedia (CC BY-SA). Cek ulang jam buka, tiket, dan aturan sebelum berangkat.", color = Color(0xFF5B6B85), fontSize = 11.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SoftButton(if (simpan) "♥ Tersimpan" else "♡ Simpan", Modifier.weight(1f)) { ubahSimpan() }
                            PrimaryButton("＋ Jadikan dinas", Modifier.weight(1f)) {
                                vm.work { val id = vm.repo.createDinas("Dinas $q", System.currentTimeMillis(), null, ""); vm.aktifId = id; vm.settings.lastDinasId = id; vm.tab = DetailTab.PETA; vm.push(Screen.Detail(id)) }
                            }
                        }
                        if (aktif != null) SoftButton("＋ Titik di ${aktif.name}", Modifier.fillMaxWidth()) { vm.work { vm.repo.saveTitik(aktif.id, null, q, "Panduan", null); vm.toast("Ditambahkan ke ${aktif.name}") } }
                    }
                    gagal -> {
                        Text("Tidak tersambung atau terlalu lama menunggu. Coba lagi, atau pakai tombol di bawah.", color = Color(0xFF5B6B85))
                        SoftButton("Coba lagi", Modifier.fillMaxWidth()) { minta = false; minta = true }
                    }
                    selesai -> Text("Tidak ditemukan di Wikipedia. Pakai tombol di bawah untuk mencari lewat Maps.", color = Color(0xFF5B6B85))
                    else -> Text("Mencari…", color = Color(0xFF5B6B85))
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile("🗺️", "Wisata di $q", "Tempat wisata, alam, dan lainnya", Color(0xFFE3EFFE), Color(0xFFCFE2FB), BIRU, Modifier.weight(1f).heightIn(min = 170.dp)) { buka(ctx, mapsCari("tempat wisata di $q")) }
            Tile("🍲", "Kuliner di $q", "Rekomendasi makanan khas dan favorit", Color(0xFFFFF1E2), Color(0xFFFFE2C2), Color(0xFFE08A2E), Modifier.weight(1f).heightIn(min = 170.dp)) { buka(ctx, mapsCari("makanan khas di $q")) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile("🏨", "Hotel di $q", "Penginapan nyaman untuk perjalananmu", Color(0xFFEDE6FB), Color(0xFFDCCFF6), Color(0xFF6B4CC4), Modifier.weight(1f).heightIn(min = 170.dp)) { buka(ctx, mapsCari("hotel di $q")) }
            Tile("🎫", "Cari di Traveloka", "Pesawat, hotel, tiket, dan aktivitas", Color(0xFFDDF6EA), Color(0xFFC4EEDB), Color(0xFF1E9E6A), Modifier.weight(1f).heightIn(min = 170.dp)) { buka(ctx, "https://www.traveloka.com/id-id/hotel") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile("🛏️", "Booking.com", "Cari penginapan di $q", Color(0xFFE3EFFE), Color(0xFFD3E4FB), BIRU, Modifier.weight(1f).heightIn(min = 120.dp)) { buka(ctx, "https://www.booking.com/searchresults.html?ss=" + Uri.encode(q)) }
            Tile("📖", "Panduan Wikivoyage", "Panduan wisata dari komunitas", Color(0xFFFFF7D6), Color(0xFFFFEDA8), Color(0xFFC79A00), Modifier.weight(1f).heightIn(min = 120.dp)) { buka(ctx, "https://id.wikivoyage.org/w/index.php?search=" + Uri.encode(q)) }
        }
    }
}
