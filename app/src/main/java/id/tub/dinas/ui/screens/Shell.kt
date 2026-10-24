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
import kotlinx.coroutines.launch

/** Bila belum ada dinas, aksi cepat membuat satu otomatis (bernama tanggal hari ini) supaya pengguna tidak tersangkut. */
private suspend fun buatDinasOtomatis(vm: MainViewModel): String {
    val now = System.currentTimeMillis()
    val nama = "Dinas " + java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale("id", "ID")).format(java.util.Date(now))
    val id = vm.repo.createDinas(nama, now, null, "")
    vm.settings.lastDinasId = id
    return id
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
            // Kartu profil navy
            Column(Modifier.fillMaxWidth().background(C.Grad, RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)).statusBarsPadding().padding(start = 20.dp, end = 16.dp, top = 16.dp, bottom = 22.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(52.dp).background(Color.White.copy(alpha = 0.16f), CircleShape).clickable { vm.push(Screen.Profil) }, contentAlignment = Alignment.Center) {
                        Text(ThemeState.playerName.take(1).uppercase().ifEmpty { "A" }, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f).clickable { vm.push(Screen.Profil) }) {
                        Text(ThemeState.playerName, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text(vm.settings.jabatan, color = Color(0xCCFFFFFF), fontSize = 14.sp)
                    }
                    Box(Modifier.size(44.dp).background(Color.White.copy(alpha = 0.16f), CircleShape).clickable { vm.push(Screen.Settings) }, contentAlignment = Alignment.Center) { Text("⚙️", fontSize = 20.sp) }
                }
                Spacer(Modifier.height(18.dp))
                Text(java.text.SimpleDateFormat("EEEE, d MMMM yyyy", java.util.Locale("id", "ID")).format(java.util.Date()), color = Color(0xB3FFFFFF), fontSize = 13.sp)
                Spacer(Modifier.height(2.dp))
                Text("Mau dinas ke mana?", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold, lineHeight = 32.sp)
                Spacer(Modifier.height(12.dp))
                SearchBar(cari, { cari = it }, "Cari dinas…")
            }
        }
        // Kartu utama: dinas aktif
        if (aktif != null && cari.isBlank()) item {
            val counts by remember(aktif.id) { vm.repo.observeCounts(aktif.id) }.collectAsState(0 to 0)
            val period = listOf(fmtDate(aktif.startMs), fmtDate(aktif.endMs)).filter { it.isNotEmpty() }.joinToString(" – ")
            val shape = RoundedCornerShape(24.dp)
            Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(shape).background(Brush.linearGradient(listOf(C.Navy2, Color(0xFF12202B))), shape)
                .clickable { vm.tab = DetailTab.PETA; vm.push(Screen.Detail(aktif.id)) }.padding(20.dp)) {
                Text("DINAS AKTIF", color = C.Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
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
                Text("Sisa anggaran perjalanan", style = MaterialTheme.typography.labelSmall, color = C.Muted)
                if (berjalan.isEmpty()) Text("Belum ada uang muka berjalan.", color = C.Muted, modifier = Modifier.padding(top = 6.dp))
                else {
                    val sisa = dana - bon
                    Text(id.tub.dinas.um.rupiah(sisa), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = if (sisa < 0) C.Danger else C.Ink)
                    Spacer(Modifier.height(6.dp))
                    SegBar(if (dana <= 0) 0f else (bon / dana).toFloat().coerceIn(0f, 1f), if (sisa < 0) C.Danger else C.Ok)
                    Text("Terpakai ${id.tub.dinas.um.rupiah(bon)} dari ${id.tub.dinas.um.rupiah(dana)}", color = C.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        // Aksi cepat
        item {
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PrimaryButton("📍 Kunci GPS", Modifier.weight(1f)) {
                    vm.work { val did = aktif?.id ?: buatDinasOtomatis(vm); val id = vm.repo.saveTitik(did, null, "", "", null); vm.aktifId = did; vm.mainTab = 1; vm.editor = Editor.Titik(id) }
                }
                SoftButton("📷 Kamera audit", Modifier.weight(1f)) {
                    vm.work { val did = aktif?.id ?: buatDinasOtomatis(vm); val id = vm.repo.newTemuan(did, null); vm.aktifId = did; vm.mainTab = 2; vm.editor = Editor.Temuan(id, true) }
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
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
                if (reg.isNotEmpty() || um.isNotEmpty()) Text(
                    listOfNotNull(if (reg.isNotEmpty()) "${reg.size} aset · $ditemukan ditemukan" else null, if (um.isNotEmpty()) "${um.size} pengajuan · ${berjalan.size} berjalan" else null).joinToString("   |   "),
                    color = C.Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
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
                Text(d.name, style = MaterialTheme.typography.titleMedium)
                val period = listOf(fmtDate(d.startMs), fmtDate(d.endMs)).filter { it.isNotEmpty() }.joinToString(" – ")
                if (period.isNotEmpty()) Text(period, color = C.Muted, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Chip("📍 ${counts.first} titik", false)
                    Chip("📝 ${counts.second} temuan", false)
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

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(Modifier.statusBarsPadding().padding(top = 10.dp)) {
                Text("Audit & Dokumen", style = MaterialTheme.typography.titleLarge)
                Text("Temuan lapangan, verifikasi aset, dan administrasi biaya", color = C.Muted, fontSize = 13.sp)
            }
        }
        item {
            Panel {
                Text("Temuan lapangan", style = MaterialTheme.typography.titleMedium)
                Text(s?.dinas?.name ?: "Belum ada dinas aktif", color = C.Muted, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                if (s != null) {
                    DinasPickerInline(vm, s.dinas.id)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TagPill("VERIFIED", ok, C.Ok) { vm.tab = DetailTab.TEMUAN; vm.push(Screen.Detail(s.dinas.id)) }
                        TagPill("PENDING", pend, C.Amber) { vm.tab = DetailTab.TEMUAN; vm.push(Screen.Detail(s.dinas.id)) }
                        TagPill("DISCREPANCY", sel, C.Danger) { vm.tab = DetailTab.TEMUAN; vm.push(Screen.Detail(s.dinas.id)) }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PrimaryButton("📷 Temuan baru", Modifier.weight(1f)) { vm.work { val id = vm.repo.newTemuan(s.dinas.id, null); vm.editor = Editor.Temuan(id, true) } }
                        SoftButton("Buka daftar", Modifier.weight(1f)) { vm.tab = DetailTab.TEMUAN; vm.push(Screen.Detail(s.dinas.id)) }
                    }
                    Text("Kategori: Aset Korporat · Teknis Operasional · Administrasi (ketuk temuan untuk memilih).", color = C.Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                } else Text("Buat dinas dari Beranda untuk mulai mencatat temuan.", color = C.Muted)
            }
        }
        item {
            Panel(onClick = { vm.push(Screen.Aset) }) {
                Text("Verifikasi aset", style = MaterialTheme.typography.titleMedium)
                Text("${reg.size} aset terdaftar", color = C.Muted, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TagPill("VERIFIED", aOk, C.Ok) { vm.push(Screen.Aset) }
                    TagPill("PENDING", aPend, C.Amber) { vm.push(Screen.Aset) }
                    TagPill("DISCREPANCY", aSel, C.Danger) { vm.push(Screen.Aset) }
                }
            }
        }
        item {
            Panel(onClick = { vm.push(Screen.UangMuka) }) {
                Text("Expense Ledger", style = MaterialTheme.typography.titleMedium)
                if (um.isEmpty()) Text("Belum ada uang muka. Ketuk untuk membuat pengajuan dan mengunggah bon.", color = C.Muted, modifier = Modifier.padding(top = 4.dp))
                else {
                    Text("${id.tub.dinas.um.rupiah(bon)} dari ${id.tub.dinas.um.rupiah(dana)} terpakai", color = C.Muted, fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    SegBar(if (dana <= 0) 0f else (bon / dana).toFloat().coerceIn(0f, 1f), if (bon > dana) C.Danger else C.Ok)
                    Spacer(Modifier.height(8.dp))
                    val top = kat.firstOrNull()?.value ?: 1.0
                    kat.take(6).forEach { (k, v) ->
                        Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(k.label, Modifier.width(96.dp), fontSize = 13.sp)
                            Box(Modifier.weight(1f).height(8.dp).background(C.Chip, RoundedCornerShape(4.dp))) { Box(Modifier.fillMaxHeight().fillMaxWidth((v / top).toFloat().coerceIn(0.03f, 1f)).background(C.Soft, RoundedCornerShape(4.dp))) }
                            Text(id.tub.dinas.um.rupiah(v), Modifier.width(96.dp), fontSize = 12.sp, color = C.Muted, textAlign = androidx.compose.ui.text.style.TextAlign.End)
                        }
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MenuTile("🧳", C.Brand, "Dokumen & Tiket", "Surat tugas, tiket, ID", Modifier.weight(1f).fillMaxHeight()) { vm.push(Screen.Dokumen) }
                MenuTile("🖼️", C.Soft, "Galeri Lapangan", "Foto audit + GPS", Modifier.weight(1f).fillMaxHeight()) { vm.push(Screen.Galeri) }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MenuTile("🗓️", C.Amber, "Jadwal", "Audit, meeting, tenggat", Modifier.weight(1f).fillMaxHeight()) { vm.push(Screen.Jadwal) }
                MenuTile("📊", C.Ok, "Ringkasan", "Tindak lanjut & statistik", Modifier.weight(1f).fillMaxHeight()) { if (s != null) { vm.tab = DetailTab.RINGKASAN; vm.push(Screen.Detail(s.dinas.id)) } else vm.toast("Pilih atau buat dinas dulu") }
            }
        }
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

@Composable
fun WawasanTab(vm: MainViewModel) {
    var mode by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 10.dp)) {
            Text("Wawasan & Berita", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("📰 Berita", mode == 0) { mode = 0 }
                Chip("🎭 Hiburan", mode == 1) { mode = 1 }
            }
        }
        Box(Modifier.weight(1f)) { if (mode == 0) BeritaTab(vm) else HiburanTab(vm) }
    }
}

@Composable
fun BeritaTab(vm: MainViewModel) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var extra by remember { mutableStateOf(vm.settings.newsExtra) }
    val sumber: List<Sumber> = NewsFeed.DEFAULT + NewsFeed.parseExtra(extra)
    var pilih by remember { mutableIntStateOf(0) }
    val idx = pilih.coerceIn(0, sumber.lastIndex)
    var items by remember { mutableStateOf<List<Berita>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    var tambah by remember { mutableStateOf(false) }
    fun muat() {
        val src = sumber[idx]
        scope.launch {
            loading = true; err = null
            try { items = NewsFeed.ambil(src) } catch (e: Exception) { items = emptyList(); err = "Tidak bisa memuat ${src.nama}: ${e.message ?: e.javaClass.simpleName}. Periksa internet lalu coba lagi." }
            loading = false
        }
    }
    LaunchedEffect(idx, extra) { muat() }
    fun buka(url: String) { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Column(Modifier.padding(top = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Berita", style = MaterialTheme.typography.titleLarge)
                        Text("Ringkasan dari sumber publik; baca lengkap di situs aslinya", color = C.Muted, fontSize = 13.sp)
                    }
                    HeaderAction("Muat ulang") { muat() }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    sumber.forEachIndexed { i, x -> Chip(x.nama, i == idx) { pilih = i } }
                    Chip("＋ Sumber RSS", false) { tambah = true }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SoftButton("☁️ Cuaca BMKG", Modifier.weight(1f)) { buka(NewsFeed.BMKG_CUACA) }
                SoftButton("🌋 Gempa BMKG", Modifier.weight(1f)) { buka(NewsFeed.BMKG_GEMPA_HALAMAN) }
            }
        }
        if (loading) item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = C.Brand) } }
        err?.let { m -> item { Panel { Text(m, color = C.Danger) } } }
        if (!loading && err == null && items.isEmpty()) item { Empty("Tidak ada kabar dari sumber ini.") }
        items(items, key = { it.link }) { b ->
            Panel(onClick = { buka(b.link) }) {
                Text(b.judul, style = MaterialTheme.typography.titleMedium)
                if (b.ringkas.isNotEmpty()) Text(b.ringkas, color = C.Muted, fontSize = 14.sp, maxLines = 4, modifier = Modifier.padding(top = 4.dp))
                Spacer(Modifier.height(6.dp))
                Text(listOf(b.sumber, b.waktu).filter { it.isNotBlank() }.joinToString(" · "), color = C.Muted, fontSize = 12.sp)
                Text("Baca selengkapnya ↗", color = C.Soft, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
            }
        }
        item { Text("Sumber: ${sumber[idx].nama} (${NewsFeed.host(sumber[idx].url)}). Hak cipta tetap pada penerbit asli.", color = C.Muted, fontSize = 11.sp) }
    }
    if (tambah) {
        var nama by remember { mutableStateOf("") }; var url by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { tambah = false }, title = { Text("Tambah sumber RSS") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(nama, { nama = it }, "Nama (opsional)", singleLine = true)
                Field(url, { url = it.trim() }, "Alamat RSS (https://…)", singleLine = true)
                Text("Hanya alamat https yang diterima. Hapus sumber tambahan lewat Pengaturan.", color = C.Muted, fontSize = 12.sp)
            } },
            confirmButton = { TextButton(enabled = url.startsWith("https://") && url.length > 12, onClick = {
                extra = (extra.trim() + "\n" + nama.replace("|", " ").trim() + "|" + url).trim(); vm.settings.newsExtra = extra; tambah = false; pilih = NewsFeed.DEFAULT.size + NewsFeed.parseExtra(extra).lastIndex
            }) { Text("Tambah") } },
            dismissButton = { TextButton(onClick = { tambah = false }) { Text("Batal") } })
    }
}
