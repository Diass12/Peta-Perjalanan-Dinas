package id.tub.dinas.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.tub.dinas.MainViewModel
import id.tub.dinas.domain.DinasModel
import id.tub.dinas.panduan.Kota
import id.tub.dinas.panduan.Kuliner
import id.tub.dinas.panduan.PanduanData
import id.tub.dinas.panduan.Wisata
import id.tub.dinas.ui.DetailTab
import id.tub.dinas.ui.Screen
import id.tub.dinas.ui.components.*
import id.tub.dinas.ui.theme.C

private val BAGIAN = listOf("Ringkasan", "Wisata", "Kuliner", "Hotel", "Spot foto")
private val WARNA = listOf(Color(0xFF1F3342), Color(0xFF0F766E), Color(0xFFB45309), Color(0xFF4F46E5), Color(0xFFBE185D), Color(0xFF475569))

private fun buka(ctx: Context, url: String) { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
private fun mapsCari(q: String) = "https://www.google.com/maps/search/?api=1&query=" + Uri.encode(q)

/** Samakan ejaan: "jogja", "jogjakarta", "yogya" dianggap "yogyakarta". */
private fun norm(s: String) = s.lowercase().replace(Regex("\\bjogja(karta)?\\b|\\byogya\\b"), "yogyakarta").trim()

private fun warnaKota(k: Kota) = WARNA[PanduanData.kota.indexOf(k).coerceAtLeast(0) % WARNA.size]

@Composable
private fun KartuMini(ikon: String, judul: String, sub: String, tint: Color, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(Modifier.width(158.dp).clip(shape).background(Brush.linearGradient(listOf(tint, Color(0xFF12202B))), shape).clickable { Sfx.menu(); onClick() }.padding(14.dp)) {
        Text(ikon, fontSize = 30.sp)
        Spacer(Modifier.height(18.dp))
        Text(judul, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 2)
        Text(sub, color = Color(0xCCFFFFFF), fontSize = 12.sp, maxLines = 2)
    }
}

@Composable
fun PanduanTab(vm: MainViewModel, aktif: DinasModel?) {
    var q by rememberSaveable { mutableStateOf("") }
    var kotaId by rememberSaveable { mutableStateOf<String?>(null) }
    var bagian by rememberSaveable { mutableIntStateOf(0) }
    var fav by remember { mutableStateOf(vm.settings.panduanFav) }
    fun ubahFav(id: String) { fav = if (id in fav) fav - id else fav + id; vm.settings.panduanFav = fav }
    androidx.activity.compose.BackHandler(enabled = kotaId != null) { kotaId = null }
    val kota = PanduanData.kota.firstOrNull { it.id == kotaId }
    if (kota == null) PanduanBeranda(q, { q = it }, fav, { id, b -> kotaId = id; bagian = b; q = "" }, ::ubahFav)
    else PanduanKota(vm, aktif, kota, bagian, { bagian = it }, { kotaId = null }, fav, ::ubahFav)
}

@Composable
private fun PanduanBeranda(q: String, onQ: (String) -> Unit, fav: Set<String>, bukaKota: (String, Int) -> Unit, ubahFav: (String) -> Unit) {
    val kunci = norm(q)
    val cari = kunci.length >= 2
    val hotel = cari && kunci.contains("hotel")
    val sisa = kunci.replace("hotel", " ").replace(Regex("\\bdi\\b"), " ").trim()
    val kotaCocok = PanduanData.kota.filter { k -> if (hotel && sisa.isEmpty()) true else listOf(k.nama, k.wilayah, k.tagline).any { it.lowercase().contains(if (hotel) sisa else kunci) } }
    val wisata = if (cari && !hotel) PanduanData.kota.flatMap { k -> k.wisata.filter { w -> (listOf(w.nama, w.kategori, w.deskripsi) + w.tag).any { it.lowercase().contains(kunci) } }.map { k to it } } else emptyList()
    val kuliner = if (cari && !hotel) PanduanData.kota.flatMap { k -> k.kuliner.filter { u -> listOf(u.nama, u.kategori, u.deskripsi).any { it.lowercase().contains(kunci) } }.map { k to it } } else emptyList()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(Modifier.fillMaxWidth().background(C.Grad, RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)).statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 22.dp)) {
                Text("Jelajahi. Rencanakan. Abadikan.", color = C.Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Spacer(Modifier.height(4.dp))
                Text("Mau pergi ke mana?", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold, lineHeight = 32.sp)
                Spacer(Modifier.height(12.dp))
                SearchBar(q, onQ, "Cari kota, wisata, kuliner, hotel…")
            }
        }
        if (cari) {
            if (kotaCocok.isEmpty() && wisata.isEmpty() && kuliner.isEmpty()) item { Empty("Belum ada panduan untuk “${q.trim()}”. Data awal baru mencakup 6 kota: Bali, Bandung, Yogyakarta, Jakarta, Lombok, dan Labuan Bajo.") }
            if (kotaCocok.isNotEmpty()) {
                item { SectionTitle(if (hotel) "🏨 Hotel di…" else "🧭 Destinasi (${kotaCocok.size})", Modifier.padding(horizontal = 16.dp)) }
                items(kotaCocok, key = { "k" + it.id }) { k ->
                    Panel(Modifier.padding(horizontal = 16.dp), onClick = { bukaKota(k.id, if (hotel) 3 else 0) }) {
                        Text("${k.ikon} ${k.nama}", style = MaterialTheme.typography.titleMedium)
                        Text(if (hotel) "Cari hotel di ${k.nama} lewat Traveloka, Google Maps, atau Booking.com" else k.tagline, color = C.Muted, fontSize = 13.sp)
                    }
                }
            }
            if (wisata.isNotEmpty()) {
                item { SectionTitle("🏞️ Wisata (${wisata.size})", Modifier.padding(horizontal = 16.dp)) }
                items(wisata.take(10), key = { "w" + it.first.id + it.second.id }) { (k, w) ->
                    Panel(Modifier.padding(horizontal = 16.dp), onClick = { bukaKota(k.id, 1) }) {
                        Text(w.nama, style = MaterialTheme.typography.titleMedium)
                        Text("${k.nama} · ${w.kategori}", color = C.Muted, fontSize = 13.sp)
                    }
                }
            }
            if (kuliner.isNotEmpty()) {
                item { SectionTitle("🍜 Kuliner (${kuliner.size})", Modifier.padding(horizontal = 16.dp)) }
                items(kuliner.take(10), key = { "u" + it.first.id + it.second.id }) { (k, u) ->
                    Panel(Modifier.padding(horizontal = 16.dp), onClick = { bukaKota(k.id, 2) }) {
                        Text(u.nama, style = MaterialTheme.typography.titleMedium)
                        Text("${k.nama} · ${u.kategori}", color = C.Muted, fontSize = 13.sp)
                    }
                }
            }
        } else {
            item { SectionTitle("Destinasi populer", Modifier.padding(horizontal = 16.dp)) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(PanduanData.kota, key = { it.id }) { k -> KartuMini(k.ikon, k.nama, k.tagline, warnaKota(k)) { bukaKota(k.id, 0) } }
                }
            }
            item { SectionTitle("Kuliner wajib coba", Modifier.padding(horizontal = 16.dp)) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(PanduanData.kota, key = { "u" + it.id }) { k -> val u = k.kuliner.first(); KartuMini("🍜", u.nama, "${k.nama} · ${u.kategori}", C.Kuliner) { bukaKota(k.id, 2) } }
                }
            }
            item { SectionTitle("Wisata pilihan", Modifier.padding(horizontal = 16.dp)) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(PanduanData.kota, key = { "w" + it.id }) { k -> val w = k.wisata.first(); KartuMini("🏞️", w.nama, "${k.nama} · ${w.kategori}", C.Wisata) { bukaKota(k.id, 1) } }
                }
            }
            item { SectionTitle("Spot foto", Modifier.padding(horizontal = 16.dp)) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(PanduanData.kota.flatMap { k -> k.wisata.filter { it.foto.isNotBlank() }.take(1).map { k to it } }, key = { "f" + it.first.id }) { (k, w) -> KartuMini("📸", w.nama, k.nama, C.Foto) { bukaKota(k.id, 4) } }
                }
            }
            val favKota = PanduanData.kota.filter { "k:" + it.id in fav }
            if (favKota.isNotEmpty()) {
                item { SectionTitle("♥ Destinasi favorit", Modifier.padding(horizontal = 16.dp)) }
                items(favKota, key = { "fk" + it.id }) { k ->
                    Panel(Modifier.padding(horizontal = 16.dp), onClick = { bukaKota(k.id, 0) }) { Text("${k.ikon} ${k.nama}", style = MaterialTheme.typography.titleMedium); Text(k.tagline, color = C.Muted, fontSize = 13.sp) }
                }
            }
            item { Text(PanduanData.SUMBER, color = C.Muted, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 16.dp)) }
        }
    }
}

@Composable
private fun PanduanKota(vm: MainViewModel, aktif: DinasModel?, k: Kota, bagian: Int, setBagian: (Int) -> Unit, kembali: () -> Unit, fav: Set<String>, ubahFav: (String) -> Unit) {
    val ctx = LocalContext.current
    val tint = warnaKota(k)
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(tint, Color(0xFF12202B))), RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)).statusBarsPadding().padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("‹", color = Color.White, fontSize = 34.sp, modifier = Modifier.clickable(onClick = kembali).padding(horizontal = 14.dp))
                Text(k.wilayah, color = Color(0xCCFFFFFF), fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text(if ("k:" + k.id in fav) "♥" else "♡", color = C.Accent, fontSize = 28.sp, modifier = Modifier.clickable { ubahFav("k:" + k.id) }.padding(horizontal = 10.dp))
            }
            Column(Modifier.padding(start = 14.dp)) {
                Text("${k.ikon} ${k.nama}", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text(k.tagline, color = Color(0xE6FFFFFF), fontSize = 15.sp)
                Spacer(Modifier.height(12.dp))
                AmberButton("＋ Jadikan dinas", Modifier.fillMaxWidth()) {
                    vm.work {
                        val id = vm.repo.createDinas("Dinas ${k.nama}", System.currentTimeMillis(), null, "")
                        vm.aktifId = id; vm.settings.lastDinasId = id; vm.tab = DetailTab.PETA; vm.push(Screen.Detail(id))
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BAGIAN.forEachIndexed { i, l -> Chip(l, bagian == i) { setBagian(i) } }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when (bagian) {
                0 -> {
                    item { Panel { Text(k.ringkas, lineHeight = 22.sp) } }
                    item { Panel { Text("Waktu terbaik", style = MaterialTheme.typography.labelSmall, color = C.Muted); Text(k.waktu); Spacer(Modifier.height(8.dp)); Text("Saran lama perjalanan", style = MaterialTheme.typography.labelSmall, color = C.Muted); Text(k.durasi) } }
                    item { Panel { Text("${k.wisata.size} tempat wisata · ${k.kuliner.size} kuliner · ${k.wisata.count { it.foto.isNotBlank() }} spot foto", color = C.Muted, fontSize = 13.sp) } }
                    item { Text(PanduanData.SUMBER, color = C.Muted, fontSize = 11.sp) }
                }
                1 -> items(k.wisata, key = { "w" + it.id }) { w -> KartuWisata(vm, aktif, k, w, fav, ubahFav, ctx, false) }
                2 -> items(k.kuliner, key = { "u" + it.id }) { u -> KartuKuliner(k, u, fav, ubahFav, ctx) }
                3 -> {
                    item {
                        Panel {
                            Text("Hotel di ${k.nama}", style = MaterialTheme.typography.titleMedium)
                            Text("Rating, harga, dan ketersediaan kamar dilihat langsung di aplikasi tujuan. Aplikasi ini tidak menyimpan atau mengarang data hotel.", color = C.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                    item { PrimaryButton("🏨 Cari di Traveloka", Modifier.fillMaxWidth()) { buka(ctx, "https://www.traveloka.com/id-id/hotel") } }
                    item { SoftButton("📍 Hotel di ${k.nama} (Google Maps)", Modifier.fillMaxWidth()) { buka(ctx, mapsCari("hotel di ${k.nama}")) } }
                    item { SoftButton("🛏️ Cari di Booking.com", Modifier.fillMaxWidth()) { buka(ctx, "https://www.booking.com/searchresults.html?ss=" + Uri.encode(k.nama)) } }
                    item { Text("Di Traveloka, ketik “${k.nama}” pada kolom pencarian.", color = C.Muted, fontSize = 12.sp) }
                }
                else -> {
                    val foto = k.wisata.filter { it.foto.isNotBlank() }
                    if (foto.isEmpty()) item { Empty("Belum ada spot foto untuk kota ini.") }
                    items(foto, key = { "f" + it.id }) { w -> KartuWisata(vm, aktif, k, w, fav, ubahFav, ctx, true) }
                }
            }
        }
    }
}

@Composable
private fun KartuWisata(vm: MainViewModel, aktif: DinasModel?, k: Kota, w: Wisata, fav: Set<String>, ubahFav: (String) -> Unit, ctx: Context, fotoMode: Boolean) {
    val id = "w:${k.id}:${w.id}"
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(w.nama, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(if (id in fav) "♥" else "♡", color = C.Foto, fontSize = 26.sp, modifier = Modifier.clickable { ubahFav(id) }.padding(start = 8.dp))
        }
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip(w.kategori, true, C.Wisata)
            w.tag.take(2).forEach { Chip(it, false) }
        }
        Text(if (fotoMode) "📸 ${w.foto}" else w.deskripsi, modifier = Modifier.padding(top = 8.dp), lineHeight = 21.sp)
        if (!fotoMode) Text("⏱ ${w.durasi}", color = C.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SoftButton("📍 Maps", Modifier.weight(1f)) { buka(ctx, mapsCari("${w.nama} ${k.nama}")) }
            PrimaryButton("＋ Titik dinas", Modifier.weight(1f)) {
                if (aktif == null) vm.toast("Buat atau pilih dinas dulu di Beranda")
                else vm.work { vm.repo.saveTitik(aktif.id, null, w.nama, "${w.kategori} · ${k.nama}", null); vm.toast("Ditambahkan ke ${aktif.name}") }
            }
        }
    }
}

@Composable
private fun KartuKuliner(k: Kota, u: Kuliner, fav: Set<String>, ubahFav: (String) -> Unit, ctx: Context) {
    val id = "u:${k.id}:${u.id}"
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(u.nama, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(if (id in fav) "♥" else "♡", color = C.Foto, fontSize = 26.sp, modifier = Modifier.clickable { ubahFav(id) }.padding(start = 8.dp))
        }
        Row(Modifier.padding(top = 6.dp)) { Chip(u.kategori, true, C.Kuliner) }
        Text(u.deskripsi, modifier = Modifier.padding(top = 8.dp), lineHeight = 21.sp)
        Text("🕌 ${u.halal}", color = C.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
        SoftButton("🔎 Cari tempatnya di Maps", Modifier.fillMaxWidth().padding(top = 10.dp)) { buka(ctx, mapsCari("${u.nama} di ${k.nama}")) }
    }
}
