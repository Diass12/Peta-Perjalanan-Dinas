package id.tub.dinas.ui.screens

import id.tub.dinas.ui.components.Text
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.tub.dinas.MainViewModel
import id.tub.dinas.domain.*
import id.tub.dinas.ui.components.*
import id.tub.dinas.ui.theme.C
import id.tub.dinas.um.*

private val Ok = Color(0xFF1B8A5A)

@Composable
fun JadwalScreen(vm: MainViewModel) {
    val c = vm.jadwal
    val all by c.daftar.collectAsState()
    val today = todayIso()
    val shown = all.filter { m ->
        (c.filterJenis == null || m.jenis == c.filterJenis) &&
            when (c.filterStatus) { 0 -> !m.selesai; 1 -> m.selesai; else -> true }
    }
    var req by remember { mutableStateOf<List<JadwalModel>?>(null) }
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) { uri ->
        val r = req; req = null; if (uri != null && r != null) c.ekspor(uri, r)
    }

    Column(Modifier.fillMaxSize().background(C.Bg)) {
        Header("Jadwal", onBack = { vm.pop() }) {
            if (all.isNotEmpty()) HeaderAction("Ekspor") { req = shown; create.launch("Rekap Jadwal $today.xlsx") }
        }
        Column(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip("Semua", c.filterJenis == null, onClick = { c.filterJenis = null })
                JadwalJenis.entries.forEach { j -> Chip(j.label, c.filterJenis == j, onClick = { c.filterJenis = j }) }
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip("Belum selesai", c.filterStatus == 0, onClick = { c.filterStatus = 0 })
                Chip("Selesai", c.filterStatus == 1, onClick = { c.filterStatus = 1 })
                Chip("Semua status", c.filterStatus == 2, onClick = { c.filterStatus = 2 })
            }
        }
        Box(Modifier.weight(1f)) {
            if (shown.isEmpty()) Empty(if (all.isEmpty()) "Belum ada jadwal. Ketuk “+ Jadwal baru” untuk mencatat audit, meeting, tenggat laporan, dan lainnya." else "Tidak ada jadwal untuk filter ini.")
            val hariIni = shown.filter { it.tanggal == today }
            val depan = shown.filter { it.tanggal > today }
            val lewat = shown.filter { it.tanggal < today }.reversed()
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                fun section(title: String, l: List<JadwalModel>) {
                    if (l.isEmpty()) return
                    item(key = "h_$title") { Label("$title (${l.size})") }
                    items(l, key = { it.id }) { m -> JadwalCard(vm, m, m.tanggal < today && !m.selesai) }
                }
                section("Hari ini", hariIni); section("Akan datang", depan); section("Sudah lewat", lewat)
            }
        }
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) { AmberButton("+ Jadwal baru", Modifier.fillMaxWidth()) { c.baru() } }
    }
    c.edit?.let { JadwalDialog(vm, it) }
}

@Composable
private fun JadwalCard(vm: MainViewModel, m: JadwalModel, telat: Boolean) {
    Panel(onClick = { vm.jadwal.edit = m }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Chip(m.jenis.label, true, color = when (m.jenis) { JadwalJenis.AUDIT -> Color(0xFF7A4FD1); JadwalJenis.DEADLINE -> C.Danger; JadwalJenis.DINAS -> Ok; else -> C.Ink })
            Spacer(Modifier.width(8.dp))
            Text(listOf(fmtTgl(m.tanggal), listOf(m.jamMulai, m.jamSelesai).filter { it.isNotEmpty() }.joinToString("–")).filter { it.isNotEmpty() }.joinToString(" · "),
                color = if (telat) C.Danger else C.Muted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Chip(if (m.selesai) "✓ Selesai" else "Tandai selesai", m.selesai, color = Ok, onClick = { vm.jadwal.tandai(m, !m.selesai) })
        }
        Spacer(Modifier.height(4.dp))
        Text(m.judul, style = MaterialTheme.typography.titleMedium)
        if (m.lokasi.isNotBlank()) Text("📍 ${m.lokasi}", color = C.Muted, style = MaterialTheme.typography.bodyMedium)
        if (m.peserta.isNotBlank()) Text("👥 ${m.peserta}", color = C.Muted, style = MaterialTheme.typography.bodyMedium)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun JadwalDialog(vm: MainViewModel, ex: JadwalModel) {
    val c = vm.jadwal
    var judul by remember(ex.id) { mutableStateOf(ex.judul) }
    var jenis by remember(ex.id) { mutableStateOf(ex.jenis) }
    var tgl by remember(ex.id) { mutableStateOf(ex.tanggal) }
    var mulai by remember(ex.id) { mutableStateOf(ex.jamMulai) }
    var akhir by remember(ex.id) { mutableStateOf(ex.jamSelesai) }
    var lok by remember(ex.id) { mutableStateOf(ex.lokasi) }
    var pes by remember(ex.id) { mutableStateOf(ex.peserta) }
    var cat by remember(ex.id) { mutableStateOf(ex.catatan) }
    var hapus by remember { mutableStateOf(false) }
    val ok = tgl.isNotEmpty() && judul.isNotBlank()
    fun model() = ex.copy(judul = judul.trim(), jenis = jenis, tanggal = tgl, jamMulai = mulai, jamSelesai = akhir, lokasi = lok.trim(), peserta = pes.trim(), catatan = cat.trim())
    AlertDialog(
        onDismissRequest = { c.edit = null },
        title = { Text(if (ex.id.isEmpty()) "Jadwal baru" else "Ubah jadwal") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    JadwalJenis.entries.forEach { j -> Chip(j.label, jenis == j, onClick = { jenis = j }) }
                }
                Field(judul, { judul = it }, "Agenda (mis. Audit aset site, Meeting bulanan)", singleLine = true)
                DateField("Tanggal", tgl, { tgl = it }, clearable = false)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TimeField("Mulai", mulai, { mulai = it }, Modifier.weight(1f))
                    TimeField("Selesai", akhir, { akhir = it }, Modifier.weight(1f))
                }
                Field(lok, { lok = it }, "Lokasi / tautan meeting", singleLine = true)
                Field(pes, { pes = it }, "Peserta", singleLine = true)
                Field(cat, { cat = it }, "Catatan", minLines = 2)
                if (ex.id.isNotEmpty()) {
                    SoftButton("Tambah ke aplikasi Kalender", Modifier.fillMaxWidth(), enabled = ok) { c.keKalender(model()) }
                    SoftButton("Hapus jadwal", Modifier.fillMaxWidth(), danger = true) { hapus = true }
                }
            }
        },
        confirmButton = { TextButton(enabled = ok, onClick = { c.simpan(model()) }) { Text("Simpan") } },
        dismissButton = { TextButton(onClick = { c.edit = null }) { Text("Batal") } },
    )
    if (hapus) Confirm("Hapus jadwal?", "“${ex.judul}” dihapus permanen.", onConfirm = { hapus = false; c.hapus(ex.id) }, onDismiss = { hapus = false })
}
