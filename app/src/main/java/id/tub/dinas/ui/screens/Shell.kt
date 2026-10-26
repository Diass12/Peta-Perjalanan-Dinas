package id.tub.dinas.ui.screens

import id.tub.dinas.ui.components.Text
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
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
import id.tub.dinas.domain.*
import id.tub.dinas.news.Berita
import id.tub.dinas.news.NewsFeed
import id.tub.dinas.news.Sumber
import id.tub.dinas.ui.DetailTab
import id.tub.dinas.ui.Editor
import id.tub.dinas.ui.Screen
import id.tub.dinas.ui.fmtDate
import id.tub.dinas.ui.components.*
import id.tub.dinas.ui.theme.C
import id.tub.dinas.ui.theme.ThemeState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Bila belum ada dinas, aksi cepat membuat satu otomatis (bernama tanggal hari ini) supaya pengguna tidak tersangkut. */
suspend fun buatDinasOtomatis(vm: MainViewModel): String {
    val now = System.currentTimeMillis()
    val nama = "Dinas " + java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale("id", "ID")).format(java.util.Date(now))
    val id = vm.repo.createDinas(nama, now, null, "")
    vm.settings.lastDinasId = id
    return id
}

/** Menjalankan aksi dari widget layar utama: sama seperti kartu Kunci GPS / Kamera audit di Beranda. */
suspend fun jalankanAksiWidget(vm: MainViewModel, aksi: String) {
    while (vm.pop()) { }
    val dinas = vm.repo.observeDinas().first()
    val did = dinas.firstOrNull { it.id == (vm.aktifId ?: vm.settings.lastDinasId) }?.id ?: dinas.firstOrNull()?.id ?: buatDinasOtomatis(vm)
    if (aksi == "gps") { val id = vm.repo.saveTitik(did, null, "", "", null); vm.aktifId = did; vm.mainTab = 1; vm.editor = Editor.Titik(id) }
    else { val id = vm.repo.newTemuan(did, null); vm.aktifId = did; vm.mainTab = 2; vm.editor = Editor.Temuan(id, true) }
}

/** Cangkang aplikasi: 5 menu bawah — Beranda, Peta & GPS, Audit & Dokumen, Wawasan, Panduan Travel. */
@Composable
fun ShellScreen(vm: MainViewModel) {
    val list by vm.dinasList.collectAsState()
    val aktif = list.firstOrNull { it.id == vm.aktifId } ?: list.firstOrNull()
    val snap by remember(aktif?.id) { aktif?.let { vm.repo.observeSnapshot(it.id) } ?: kotlinx.coroutines.flow.flowOf(null) }.collectAsState(initial = null)
    val s = snap?.takeIf { it.dinas.id == aktif?.id }
    Column(Modifier.fillMaxSize().background(C.Bg)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (vm.mainTab) {
                0 -> BerandaTab(vm, aktif, s)
                1 -> PetaGpsTab(vm, s)
                2 -> AuditHubTab(vm, s)
                3 -> WawasanTab(vm)
                else -> PanduanTab(vm, aktif)
            }
        }
        CleanNav(listOf("🏠" to "Beranda", "🗺️" to "Peta & GPS", "📋" to "Audit", "📰" to "Wawasan", "🧭" to "Panduan"), vm.mainTab) { vm.mainTab = it }
    }
    val ed = vm.editor
    if (ed != null && s != null) EditorHost(vm, s)
}

@Composable
private fun DinasPicker(vm: MainViewModel, aktifId: String?) {
    val list by vm.dinasList.collectAsState()
    if (list.isEmpty()) return
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        list.forEach { d -> Chip(d.name, d.id == aktifId) { vm.aktifId = d.id; vm.settings.lastDinasId = d.id } }
    }
}

// ---------------------------------------------------------------- BERANDA

/** Kartu aksi besar di beranda: ikon dalam lingkaran, judul, keterangan, dan panah. [gelap] = biru tua. */
@Composable
private fun AksiKartu(ikon: String, judul: String, sub: String, gelap: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    val teks = if (gelap) Color.White else C.Ink
    Row(modifier.clip(shape).then(if (gelap) Modifier.background(Brush.linearGradient(listOf(Color(0xFF1D4FA8), Color(0xFF173C82)))) else Modifier.background(Color(0xFFEAF1FD)).border(1.dp, C.Line, shape))
        .clickable { Sfx.menu(); onClick() }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(46.dp).background(if (gelap) Color(0xFF2F86E6) else Color(0xFFD6E5FB), CircleShape), contentAlignment = Alignment.Center) { Text(ikon, fontSize = 22.sp) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(judul, color = teks, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
            Text(sub, color = if (gelap) Color(0xCCFFFFFF) else C.Muted, fontSize = 12.sp, lineHeight = 15.sp, maxLines = 2)
        }
        Text("›", color = teks, fontSize = 22.sp)
    }
}

@Composable
fun BerandaTab(vm: MainViewModel, aktif: DinasModel?, s: DinasSnapshot?) {
    val list by vm.dinasList.collectAsState()
    var creating by remember { mutableStateOf(false) }
    var cari by rememberSaveable { mutableStateOf("") }
    val q = cari.trim()
    val tampil = list.filter { q.isBlank() || it.name.contains(q, ignoreCase = true) || it.note.contains(q, ignoreCase = true) }
    var semuaTitik by remember { mutableStateOf<List<id.tub.dinas.data.local.TitikEntity>>(emptyList()) }
    var semuaTemuan by remember { mutableStateOf<List<id.tub.dinas.data.local.TemuanEntity>>(emptyList()) }
    LaunchedEffect(q.isNotBlank()) { if (q.isNotBlank()) { semuaTitik = vm.repo.semuaTitik(); semuaTemuan = vm.repo.semuaTemuan() } }
    val reg by vm.aset.register.collectAsState()
    val um by vm.um.bundles.collectAsState()
    val jd by vm.jadwal.daftar.collectAsState()
    val hariIni = id.tub.dinas.um.todayIso()
    val depan = jd.filter { !it.selesai && it.tanggal >= hariIni }
    val next = depan.firstOrNull()
    val ditemukan = reg.count { it.state == AuditState.FOUND }
    val berjalan = um.filter { it.um.status != UmStatus.SELESAI }
    val dana = berjalan.sumOf { it.dana }; val bon = berjalan.sumOf { it.totalBon }
    val namaDinas = list.associate { it.id to it.name }
    val hTitik = if (q.length >= 2) semuaTitik.filter { it.title.contains(q, true) || it.note.contains(q, true) } else emptyList()
    val hTemuan = if (q.length >= 2) semuaTemuan.filter { it.title.contains(q, true) || it.code.contains(q, true) || it.description.contains(q, true) } else emptyList()
    val hAset = if (q.length >= 2) reg.filter { it.name.contains(q, true) || it.code.contains(q, true) || it.location.contains(q, true) } else emptyList()
    val hJadwal = if (q.length >= 2) jd.filter { it.judul.contains(q, true) || it.lokasi.contains(q, true) } else emptyList()
    val adaHasil = tampil.isNotEmpty() || hTitik.isNotEmpty() || hTemuan.isNotEmpty() || hAset.isNotEmpty() || hJadwal.isNotEmpty()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            // Header pemandangan
            val bentuk = RoundedCornerShape(bottomStart = 36.dp, bottomEnd = 36.dp)
            Box(Modifier.fillMaxWidth().clip(bentuk)) {
                Pemandangan(Modifier.matchParentSize())
                Column(Modifier.statusBarsPadding().padding(start = 20.dp, end = 16.dp, top = 16.dp, bottom = 22.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(56.dp).background(Color(0xFF1B3358), CircleShape).border(3.dp, Color.White.copy(alpha = 0.85f), CircleShape).clickable { vm.push(Screen.Profil) }, contentAlignment = Alignment.Center) {
                            Text(ThemeState.playerName.take(1).uppercase().ifEmpty { "A" }, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f).clickable { vm.push(Screen.Profil) }) {
                            Text(ThemeState.playerName, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            Text(vm.settings.jabatan, color = Color(0xE6FFFFFF), fontSize = 15.sp)
                        }
                        Box(Modifier.size(46.dp).background(Color.White, CircleShape).clickable { vm.push(Screen.Settings) }, contentAlignment = Alignment.Center) { Text("⚙️", fontSize = 22.sp) }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text("📅 " + java.text.SimpleDateFormat("EEEE, d MMMM yyyy", java.util.Locale("id", "ID")).format(java.util.Date()), color = Color.White, fontSize = 14.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("Mau dinas ke mana?", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 36.sp)
                    Text("Rencanakan perjalanan, catat temuan, kelola anggaran dengan mudah.", color = Color(0xF2FFFFFF), fontSize = 14.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 2.dp, end = 60.dp))
                    Spacer(Modifier.height(16.dp))
                    PillSearch(cari, { cari = it }, "Cari dinas…")
                }
            }
        }
        // Kartu utama: dinas aktif
        if (aktif != null && cari.isBlank()) item {
            val counts by remember(aktif.id) { vm.repo.observeCounts(aktif.id) }.collectAsState(0 to 0)
            val period = listOf(fmtDate(aktif.startMs), fmtDate(aktif.endMs)).filter { it.isNotEmpty() }.joinToString(" – ")
            val shape = RoundedCornerShape(24.dp)
            Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(shape).background(Brush.linearGradient(listOf(Color(0xFF1A4FB0), Color(0xFF2F86E6))), shape)
                .clickable { vm.tab = DetailTab.PETA; vm.push(Screen.Detail(aktif.id)) }.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("📅  DINAS AKTIF", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.weight(1f))
                    Text("✈️", fontSize = 26.sp)
                }
                Spacer(Modifier.height(6.dp))
                Text(aktif.name, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 2, lineHeight = 28.sp)
                if (period.isNotEmpty()) Text(period, color = Color(0xCCFFFFFF), fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp))
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("📍 ${counts.first} titik", color = Color.White, fontSize = 13.sp, modifier = Modifier.background(Color.White.copy(alpha = 0.14f), RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 5.dp))
                    Text("📝 ${counts.second} temuan", color = Color.White, fontSize = 13.sp, modifier = Modifier.background(Color.White.copy(alpha = 0.14f), RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 5.dp))
                    Spacer(Modifier.weight(1f))
                    Text("Buka ›", color = Color(0xFF12202B), fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.background(C.Accent, RoundedCornerShape(14.dp)).padding(horizontal = 16.dp, vertical = 8.dp))
                }
            }
        }
        if (q.isBlank()) {
        // Sisa anggaran
        item {
            Panel(Modifier.padding(horizontal = 16.dp), onClick = { vm.push(Screen.UangMuka) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(64.dp).background(Color(0xFFDDF6EA), RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) { Text("👛", fontSize = 32.sp) }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Sisa anggaran semua pengajuan berjalan", color = C.Muted, fontSize = 14.sp)
                        if (berjalan.isEmpty()) Text("Belum ada uang muka berjalan.", color = C.Muted, modifier = Modifier.padding(top = 6.dp))
                        else {
                            val sisa = dana - bon
                            Text(id.tub.dinas.um.rupiah(sisa), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = if (sisa < 0) C.Danger else C.Ink)
                            Spacer(Modifier.height(6.dp))
                            SegBar(if (dana <= 0) 0f else (bon / dana).toFloat().coerceIn(0f, 1f), if (sisa < 0) C.Danger else C.Ok)
                            Text("Terpakai ${id.tub.dinas.um.rupiah(bon)} dari ${id.tub.dinas.um.rupiah(dana)}", color = C.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                    Text("›", color = C.Muted, fontSize = 28.sp, modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
        // Aksi cepat
        item {
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AksiKartu("📍", "Kunci GPS", "Mulai tracking lokasi", true, Modifier.weight(1f)) {
                    vm.work { val did = aktif?.id ?: buatDinasOtomatis(vm); val id = vm.repo.saveTitik(did, null, "", "", null); vm.aktifId = did; vm.mainTab = 1; vm.editor = Editor.Titik(id) }
                }
                AksiKartu("📷", "Kamera audit", "Ambil foto / video", false, Modifier.weight(1f)) {
                    vm.work { val did = aktif?.id ?: buatDinasOtomatis(vm); val id = vm.repo.newTemuan(did, null); vm.aktifId = did; vm.mainTab = 2; vm.editor = Editor.Temuan(id, true) }
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(C.Surface).padding(horizontal = 8.dp, vertical = 12.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    QuickAction("📦", "Aset", C.lens(Lens.ASET), Modifier.weight(1f)) { vm.push(Screen.Aset) }
                    QuickAction("💵", "Uang Muka", C.Ok, Modifier.weight(1f)) { vm.push(Screen.UangMuka) }
                    QuickAction("🗓️", "Jadwal", C.Soft, Modifier.weight(1f)) { vm.push(Screen.Jadwal) }
                    QuickAction("➕", "Dinas baru", C.Accent, Modifier.weight(1f)) { creating = true }
                }
                Row(Modifier.fillMaxWidth()) {
                    QuickAction("🖼️", "Galeri", C.Foto, Modifier.weight(1f)) { vm.push(Screen.Galeri) }
                    QuickAction("🧳", "Dokumen", C.Hotel, Modifier.weight(1f)) { vm.push(Screen.Dokumen) }
                    QuickAction("🗺️", "Peta", C.Wisata, Modifier.weight(1f)) { vm.mainTab = 1 }
                    QuickAction("🧭", "Panduan", C.Kuliner, Modifier.weight(1f)) { vm.mainTab = 4 }
                }
                if (reg.isNotEmpty() || um.isNotEmpty()) Row(Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.Chip).clickable { vm.push(Screen.Aset) }.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("📋", fontSize = 18.sp)
                    Text(listOfNotNull(if (reg.isNotEmpty()) "${reg.size} aset · $ditemukan ditemukan" else null, if (um.isNotEmpty()) "${um.size} pengajuan · ${berjalan.size} berjalan" else null).joinToString("   |   "),
                        color = C.Muted, fontSize = 12.sp, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
                    Text("›", color = C.Muted, fontSize = 22.sp)
                }
            }
        }
        if (next != null) item {
            Panel(Modifier.padding(horizontal = 16.dp), onClick = { vm.push(Screen.Jadwal) }) {
                Text("Jadwal terdekat", style = MaterialTheme.typography.labelSmall, color = C.Muted)
                Spacer(Modifier.height(4.dp))
                Text(next.judul, style = MaterialTheme.typography.titleMedium)
                Text(listOf(id.tub.dinas.um.fmtTgl(next.tanggal), next.jamMulai, next.lokasi).filter { it.isNotBlank() }.joinToString(" · "), color = C.Muted, style = MaterialTheme.typography.bodyMedium)
            }
        }
        }
        if (q.isNotBlank()) {
            if (!adaHasil) item {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Empty("Belum ada yang cocok dengan “$q”. Kalau ini tujuan dinas baru, buat dinasnya sekarang.")
                    PrimaryButton("➕ Buat dinas “$q”", Modifier.fillMaxWidth()) {
                        vm.work { val id = vm.repo.createDinas(q, System.currentTimeMillis(), null, ""); vm.aktifId = id; vm.settings.lastDinasId = id; vm.tab = DetailTab.PETA; vm.push(Screen.Detail(id)) }
                    }
                }
            }
            if (hTitik.isNotEmpty()) {
                item { SectionTitle("📍 Titik (${hTitik.size})", Modifier.padding(horizontal = 16.dp)) }
                items(hTitik.take(8), key = { "t" + it.id }) { t ->
                    Panel(Modifier.padding(horizontal = 16.dp), onClick = { vm.aktifId = t.dinasId; vm.tab = DetailTab.PETA; vm.push(Screen.Detail(t.dinasId)) }) {
                        Text(t.title.ifBlank { "(tanpa judul)" }, style = MaterialTheme.typography.titleMedium)
                        Text(listOfNotNull(namaDinas[t.dinasId], t.note.takeIf { it.isNotBlank() }).joinToString(" · "), color = C.Muted, fontSize = 13.sp, maxLines = 2)
                    }
                }
            }
            if (hTemuan.isNotEmpty()) {
                item { SectionTitle("📝 Temuan (${hTemuan.size})", Modifier.padding(horizontal = 16.dp)) }
                items(hTemuan.take(8), key = { "m" + it.id }) { t ->
                    Panel(Modifier.padding(horizontal = 16.dp), onClick = { vm.aktifId = t.dinasId; vm.tab = DetailTab.TEMUAN; vm.push(Screen.Detail(t.dinasId)) }) {
                        Text(listOf(t.code, t.title).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "(tanpa judul)" }, style = MaterialTheme.typography.titleMedium)
                        Text(namaDinas[t.dinasId].orEmpty(), color = C.Muted, fontSize = 13.sp)
                    }
                }
            }
            if (hAset.isNotEmpty()) {
                item { SectionTitle("📦 Aset (${hAset.size})", Modifier.padding(horizontal = 16.dp)) }
                items(hAset.take(8), key = { "a" + it.id }) { a ->
                    Panel(Modifier.padding(horizontal = 16.dp), onClick = { vm.push(Screen.Aset) }) {
                        Text(a.name.ifBlank { a.code }, style = MaterialTheme.typography.titleMedium)
                        Text(listOf(a.code, a.location).filter { it.isNotBlank() }.joinToString(" · "), color = C.Muted, fontSize = 13.sp)
                    }
                }
            }
            if (hJadwal.isNotEmpty()) {
                item { SectionTitle("🗓️ Jadwal (${hJadwal.size})", Modifier.padding(horizontal = 16.dp)) }
                items(hJadwal.take(8), key = { "j" + it.id }) { j ->
                    Panel(Modifier.padding(horizontal = 16.dp), onClick = { vm.push(Screen.Jadwal) }) {
                        Text(j.judul, style = MaterialTheme.typography.titleMedium)
                        Text(listOf(id.tub.dinas.um.fmtTgl(j.tanggal), j.lokasi).filter { it.isNotBlank() }.joinToString(" · "), color = C.Muted, fontSize = 13.sp)
                    }
                }
            }
        }
        if (q.isBlank() || tampil.isNotEmpty()) item { SectionTitle(if (q.isBlank()) "Dinas" else "🧳 Dinas (${tampil.size})", Modifier.padding(horizontal = 16.dp)) }
        if (list.isEmpty() && q.isBlank()) item { Empty("Belum ada dinas. Ketuk “Dinas baru” untuk mulai mencatat titik dokumentasi dan temuan.") }
        items(tampil, key = { it.id }) { d ->
            val counts by remember(d.id) { vm.repo.observeCounts(d.id) }.collectAsState(0 to 0)
            Panel(Modifier.padding(horizontal = 16.dp), onClick = { vm.aktifId = d.id; vm.settings.lastDinasId = d.id; vm.tab = DetailTab.PETA; vm.push(Screen.Detail(d.id)) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(Color(0xFF2B7BE4), Color(0xFF3AA6E8)))), contentAlignment = Alignment.Center) { Text("🏔️", fontSize = 30.sp) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(d.name, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                        val period = listOf(fmtDate(d.startMs), fmtDate(d.endMs)).filter { it.isNotEmpty() }.joinToString(" – ")
                        if (period.isNotEmpty()) Text(period, color = C.Muted, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Chip("📍 ${counts.first} titik", false)
                            Chip("📝 ${counts.second} temuan", false)
                        }
                    }
                }
            }
        }
    }
    if (creating) DinasDialog(null, onDismiss = { creating = false }) { name, a, b, note ->
        vm.work { val id = vm.repo.createDinas(name, a, b, note); vm.aktifId = id; vm.settings.lastDinasId = id; vm.tab = DetailTab.PETA; vm.push(Screen.Detail(id)) }
    }
}

// ---------------------------------------------------------------- PETA & GPS

@Composable
fun PetaGpsTab(vm: MainViewModel, s: DinasSnapshot?) {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().background(C.Surface).statusBarsPadding().padding(top = 10.dp)) {
            Text("Peta & GPS", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 16.dp))
            Text(s?.dinas?.name ?: "Belum ada dinas", color = C.Muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp))
            DinasPicker(vm, s?.dinas?.id)
            Box(Modifier.fillMaxWidth().height(1.dp).background(C.Line))
        }
        if (s == null) Empty("Buat dinas lebih dulu dari Beranda (“Dinas baru”), lalu kunci titik GPS di sini.")
        else Box(Modifier.weight(1f)) { PetaTab(vm, s) }
    }
}

// ---------------------------------------------------------------- AUDIT & DOKUMEN

private fun tag(st: Status) = when (st) { Status.SELESAI -> "VERIFIED"; Status.SELISIH -> "DISCREPANCY"; else -> "PENDING" }

@Composable
private fun TagPill(text: String, n: Int, color: Color, onClick: () -> Unit) {
    Column(Modifier.clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.10f)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text("$n", color = color, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("[$text]", color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun AuditHubTab(vm: MainViewModel, s: DinasSnapshot?) {
    val reg by vm.aset.register.collectAsState()
    val um by vm.um.bundles.collectAsState()
    val ok = s?.temuan?.count { tag(it.status) == "VERIFIED" } ?: 0
    val pend = s?.temuan?.count { tag(it.status) == "PENDING" } ?: 0
    val sel = s?.temuan?.count { tag(it.status) == "DISCREPANCY" } ?: 0
    val aOk = reg.count { it.state == AuditState.FOUND }
    val aPend = reg.count { it.state == AuditState.PENDING }
    val aSel = reg.count { it.state == AuditState.MISSING || it.state == AuditState.DAMAGED }
    val dana = um.sumOf { it.dana }; val bon = um.sumOf { it.totalBon }
    val kat = um.flatMap { it.perKategori().entries }.groupBy({ it.key }, { it.value }).mapValues { it.value.sum() }.entries.sortedByDescending { it.value }

    val nAset = reg.size
    val rp = id.tub.dinas.um.rupiah(bon)
    val pct = if (dana <= 0) 0.0 else bon / dana * 100
    fun ikonKat(l: String): String { val x = l.lowercase(); return when { "transport" in x || "taksi" in x || "bensin" in x -> "🚗"; "makan" in x || "dinner" in x || "lunch" in x || "breakfast" in x || "konsumsi" in x -> "🍽️"; "hotel" in x || "akomodasi" in x -> "🏨"; "saku" in x -> "💵"; else -> "🧾" } }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.statusBarsPadding().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Audit & Dokumen", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = C.Ink)
                    Text("Temuan lapangan, verifikasi aset, dan administrasi biaya", color = C.Muted, fontSize = 13.sp)
                }
                Box(Modifier.size(46.dp).background(Color(0xFFE3EFFE), CircleShape).clickable { vm.push(Screen.Profil) }, contentAlignment = Alignment.Center) { Text("👤", fontSize = 20.sp) }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(C.Surface).padding(horizontal = 10.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                @Composable
                fun Ring(ikon: String, bg: Color, angka: String, label: String, m: Modifier, klik: () -> Unit) = Row(m.clickable(onClick = klik), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).background(bg, CircleShape), contentAlignment = Alignment.Center) { Text(ikon, fontSize = 18.sp) }
                    Spacer(Modifier.width(6.dp))
                    Column { Text(angka, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = C.Ink, maxLines = 1); Text(label, color = C.Muted, fontSize = 11.sp, maxLines = 1) }
                }
                Ring("🗂️", Color(0xFFD9F5E8), "$nAset", "Aset", Modifier.weight(0.8f)) { vm.push(Screen.Aset) }
                Box(Modifier.width(1.dp).height(34.dp).background(C.Line))
                Ring("⚠️", Color(0xFFFDE3E3), "${aSel + sel}", "Discrepancy", Modifier.weight(1f).padding(start = 8.dp)) { vm.push(Screen.Aset) }
                Box(Modifier.width(1.dp).height(34.dp).background(C.Line))
                Ring("👛", Color(0xFFDCEBFD), rp, "Terpakai", Modifier.weight(1.3f).padding(start = 8.dp)) { vm.push(Screen.UangMuka) }
            }
        }
        item {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(48.dp).background(Color(0xFFD9F5E8), CircleShape), contentAlignment = Alignment.Center) { Text("📋", fontSize = 22.sp) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Temuan lapangan", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = C.Ink)
                        Text(s?.dinas?.name ?: "Belum ada dinas aktif", color = C.Muted, fontSize = 13.sp)
                    }
                }
                Spacer(Modifier.height(10.dp))
                if (s != null) {
                    DinasPickerInline(vm, s.dinas.id)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusBox("VERIFIED", ok, C.Ok, "✔", Modifier.weight(1f)) { vm.tab = DetailTab.TEMUAN; vm.push(Screen.Detail(s.dinas.id)) }
                        StatusBox("PENDING", pend, C.Amber, "🕒", Modifier.weight(1f)) { vm.tab = DetailTab.TEMUAN; vm.push(Screen.Detail(s.dinas.id)) }
                        StatusBox("DISCREPANCY", sel, C.Danger, "⚠", Modifier.weight(1f)) { vm.tab = DetailTab.TEMUAN; vm.push(Screen.Detail(s.dinas.id)) }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(16.dp)).background(Brush.horizontalGradient(listOf(Color(0xFF0F766E), Color(0xFF1E4FB0)))).clickable { vm.work { val id = vm.repo.newTemuan(s.dinas.id, null); vm.editor = Editor.Temuan(id, true) } }, contentAlignment = Alignment.Center) {
                            Text("＋  Temuan baru", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                        Box(Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(16.dp)).border(1.dp, C.Line, RoundedCornerShape(16.dp)).clickable { vm.tab = DetailTab.TEMUAN; vm.push(Screen.Detail(s.dinas.id)) }, contentAlignment = Alignment.Center) {
                            Text("Lihat semua  ›", color = C.Ink, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        }
                    }
                    Text("Kategori: Aset Korporat · Teknis Operasional · Administrasi (ketuk temuan untuk memilih).", color = C.Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                } else Text("Buat dinas dari Beranda untuk mulai mencatat temuan.", color = C.Muted)
            }
        }
        item {
            val shape = RoundedCornerShape(22.dp)
            Column(Modifier.fillMaxWidth().clip(shape).background(Brush.linearGradient(listOf(Color(0xFFFFFBF1), Color(0xFFFFF1D6)))).border(1.dp, C.Line, shape).clickable { vm.push(Screen.Aset) }.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(48.dp).background(Color(0xFFD9F5E8), CircleShape), contentAlignment = Alignment.Center) { Text("📦", fontSize = 22.sp) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Verifikasi aset", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF12202B))
                        Text("$nAset aset terdaftar", color = Color(0xFF5B6B78), fontSize = 13.sp)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFFE9E5DA))) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(if (nAset == 0) 0f else (aPend.toFloat() / nAset).coerceIn(0.03f, 1f)).background(Brush.horizontalGradient(listOf(Color(0xFFF59E0B), Color(0xFFFBBF24)))))
                    }
                    Text("$aPend PENDING", color = Color(0xFFD97706), fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, modifier = Modifier.padding(start = 12.dp))
                }
                Text("✔ $aOk terverifikasi   ·   ⚠ $aSel selisih", color = Color(0xFF5B6B78), fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            }
        }
        item {
            Panel(onClick = { vm.push(Screen.UangMuka) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(48.dp).background(Color(0xFFDCEBFD), CircleShape), contentAlignment = Alignment.Center) { Text("👛", fontSize = 22.sp) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Ringkasan biaya", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = C.Ink)
                        if (um.isNotEmpty()) Text("${id.tub.dinas.um.rupiah(bon)} dari ${id.tub.dinas.um.rupiah(dana)}", color = C.Muted, fontSize = 13.sp)
                    }
                    if (um.isNotEmpty()) Text(String.format(java.util.Locale("id", "ID"), "%.1f%% terpakai", pct), color = Color(0xFF0F766E), fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.background(Color(0xFFD9F5EE), RoundedCornerShape(14.dp)).padding(horizontal = 10.dp, vertical = 6.dp))
                }
                if (um.isEmpty()) Text("Belum ada uang muka. Ketuk untuk membuat pengajuan dan mengunggah bon.", color = C.Muted, modifier = Modifier.padding(top = 8.dp))
                else {
                    Spacer(Modifier.height(10.dp))
                    SegBar(if (dana <= 0) 0f else (bon / dana).toFloat().coerceIn(0f, 1f), if (bon > dana) C.Danger else Color(0xFF14B8A6))
                    Spacer(Modifier.height(8.dp))
                    val top = kat.firstOrNull()?.value ?: 1.0
                    kat.take(6).forEach { (k, v) ->
                        Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(ikonKat(k.label), fontSize = 18.sp, modifier = Modifier.width(30.dp))
                            Text(k.label, Modifier.width(80.dp), fontSize = 13.sp, maxLines = 1)
                            Box(Modifier.weight(1f).height(10.dp).background(C.Chip, RoundedCornerShape(5.dp))) { Box(Modifier.fillMaxHeight().fillMaxWidth((v / top).toFloat().coerceIn(0.03f, 1f)).background(Brush.horizontalGradient(listOf(Color(0xFF1E6FE8), Color(0xFF3B8EF0))), RoundedCornerShape(5.dp))) }
                            Text(id.tub.dinas.um.rupiah(v), Modifier.width(92.dp), fontSize = 12.sp, color = C.Muted, textAlign = androidx.compose.ui.text.style.TextAlign.End)
                        }
                    }
                }
            }
        }
        item { Text("Akses cepat", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = C.Ink, modifier = Modifier.padding(top = 4.dp)) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AksesTile("📄", "Dokumen", Color(0xFFEDE6FB), Color(0xFFDCD0F6), Modifier.weight(1f)) { vm.push(Screen.Dokumen) }
                AksesTile("🖼️", "Galeri Lapangan", Color(0xFFDDF6EA), Color(0xFFC9F0DD), Modifier.weight(1f)) { vm.push(Screen.Galeri) }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AksesTile("🗓️", "Jadwal", Color(0xFFFFF3DA), Color(0xFFFFE7B8), Modifier.weight(1f)) { vm.push(Screen.Jadwal) }
                AksesTile("📊", "Ringkasan", Color(0xFFFDE4E8), Color(0xFFFBD0D8), Modifier.weight(1f)) { if (s != null) { vm.tab = DetailTab.RINGKASAN; vm.push(Screen.Detail(s.dinas.id)) } else vm.toast("Pilih atau buat dinas dulu") }
            }
        }
    }
}

@Composable
private fun StatusBox(label: String, n: Int, color: Color, ikon: String, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.clip(RoundedCornerShape(16.dp)).background(color.copy(alpha = 0.12f)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp)) {
        Column {
            Text("$n", color = color, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
            Text(label, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
        Text(ikon, color = color.copy(alpha = 0.55f), fontSize = 20.sp, modifier = Modifier.align(Alignment.TopEnd))
    }
}

@Composable
private fun AksesTile(ikon: String, judul: String, a: Color, b: Color, modifier: Modifier, onClick: () -> Unit) {
    Row(modifier.clip(RoundedCornerShape(20.dp)).background(Brush.linearGradient(listOf(a, b))).clickable { Sfx.menu(); onClick() }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).background(Color.White.copy(alpha = 0.75f), CircleShape), contentAlignment = Alignment.Center) { Text(ikon, fontSize = 22.sp) }
        Spacer(Modifier.width(10.dp))
        Text(judul, color = Color(0xFF12202B), fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 2, modifier = Modifier.weight(1f))
        Text("›", color = Color(0xFF5B6B78), fontSize = 22.sp)
    }
}

@Composable
private fun DinasPickerInline(vm: MainViewModel, aktifId: String?) {
    val list by vm.dinasList.collectAsState()
    if (list.size < 2) return
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        list.forEach { d -> Chip(d.name, d.id == aktifId) { vm.aktifId = d.id; vm.settings.lastDinasId = d.id } }
    }
}

// ---------------------------------------------------------------- WAWASAN & BERITA
