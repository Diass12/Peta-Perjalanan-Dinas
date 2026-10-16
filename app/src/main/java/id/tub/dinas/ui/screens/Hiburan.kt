package id.tub.dinas.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.tub.dinas.MainViewModel
import id.tub.dinas.news.Gutenberg
import id.tub.dinas.news.Karya
import id.tub.dinas.news.NewsFeed
import id.tub.dinas.news.Wikisource
import id.tub.dinas.ui.components.*
import id.tub.dinas.ui.components.Text
import id.tub.dinas.ui.theme.C
import kotlinx.coroutines.launch

private data class Video(val judul: String, val url: String)

private fun parseVideos(s: String): List<Video> = s.lines().mapNotNull { l ->
    val p = l.split('|', limit = 2)
    if (p.size == 2 && videoOk(p[1].trim())) Video(p[0].trim().ifEmpty { "Video" }, p[1].trim()) else null
}

private fun videoOk(u: String): Boolean {
    if (!u.startsWith("https://")) return false
    val h = NewsFeed.host(u).lowercase()
    return h == "youtu.be" || h == "youtube.com" || h.endsWith(".youtube.com")
}

/** Hiburan yang legal: novel domain publik (Wikisource), tautan YouTube pilihan sendiri, dan komik resmi yang gratis dibaca. */
@Composable
fun HiburanTab(vm: MainViewModel) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var inggris by remember { mutableStateOf(false) }
    var q by remember { mutableStateOf("novel") }
    var hasil by remember { mutableStateOf<List<Karya>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    var raw by remember { mutableStateOf(vm.settings.videoLinks) }
    val videos = parseVideos(raw)
    var tambah by remember { mutableStateOf(false) }
    var repoRaw by remember { mutableStateOf(vm.settings.repoLinks) }
    val repos = NewsFeed.parseExtra(repoRaw)
    var tambahRepo by remember { mutableStateOf(false) }
    fun bukaMihon(url: String) {
        // Mihon & turunannya menerima tautan add-repo; bila tidak terpasang, tautannya dibuka di peramban.
        val enc = Uri.encode(url)
        val coba = listOf("mihon://add-repo?url=$enc", "tachiyomi://add-repo?url=$enc", url)
        for (u in coba) { if (runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) return }
        vm.toast("Tidak ada aplikasi yang bisa membuka tautan ini")
    }
    fun salin(url: String) {
        val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("repositori", url)); vm.toast("Tautan disalin")
    }
    fun buka(url: String) { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
    fun cari() {
        scope.launch {
            loading = true; err = null
            try { hasil = if (inggris) Gutenberg.cari(q) else Wikisource.cari(q) } catch (e: Exception) { hasil = emptyList(); err = "Tidak bisa memuat ${if (inggris) "Project Gutenberg" else "Wikisource"}: ${e.message ?: e.javaClass.simpleName}. Periksa internet lalu coba lagi." }
            loading = false
        }
    }
    LaunchedEffect(Unit) { cari() }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { SectionTitle("Novel (domain publik)") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Bahasa Indonesia", !inggris) { if (inggris) { inggris = false; q = "novel"; cari() } }
                Chip("English", inggris) { if (!inggris) { inggris = true; q = ""; cari() } }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(q, { q = it }, if (inggris) "Search books (e.g. Dickens, mystery)" else "Cari karya (mis. novel, hikayat, Balai Pustaka)", Modifier.weight(1f), singleLine = true)
                PrimaryButton("Cari") { cari() }
            }
            Text(if (inggris) "Sumber: Project Gutenberg — buku bahasa Inggris domain publik. Ketuk untuk membaca atau mengunduh di situsnya." else "Sumber: Wikisource bahasa Indonesia — karya yang hak ciptanya sudah habis atau berlisensi terbuka. Ketuk untuk membaca di situsnya.", color = C.Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        }
        if (loading) item { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = C.Brand) } }
        err?.let { m -> item { Panel { Text(m, color = C.Danger) } } }
        if (!loading && err == null && hasil.isEmpty()) item { Empty("Tidak ada karya yang cocok. Coba kata lain.") }
        items(hasil, key = { it.link }) { k ->
            Panel(onClick = { buka(k.link) }) {
                Text(k.judul, style = MaterialTheme.typography.titleMedium)
                if (k.ringkas.isNotEmpty()) Text(k.ringkas, color = C.Muted, fontSize = 14.sp, maxLines = 3, modifier = Modifier.padding(top = 2.dp))
                Text("Baca ↗", color = C.Soft, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
            }
        }
        item { SoftButton(if (inggris) "Jelajahi Project Gutenberg ↗" else "Jelajahi Wikisource ↗", Modifier.fillMaxWidth()) { buka(if (inggris) Gutenberg.BERANDA else Wikisource.BERANDA) } }

        item { SectionTitle("Video (YouTube)") }
        if (videos.isEmpty()) item { Text("Simpan tautan video atau kanal YouTube favorit Anda di sini, lalu buka dengan satu ketukan.", color = C.Muted) }
        items(videos, key = { it.url }) { v ->
            Panel(onClick = { buka(v.url) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("▶ ${v.judul}", style = MaterialTheme.typography.titleMedium, maxLines = 2)
                        Text(NewsFeed.host(v.url), color = C.Muted, fontSize = 12.sp)
                    }
                    TextButton(onClick = {
                        raw = raw.lines().filterNot { it.endsWith("|" + v.url) }.joinToString("\n"); vm.settings.videoLinks = raw
                    }) { Text("Hapus", color = C.Danger) }
                }
            }
        }
        item { SoftButton("＋ Tambah tautan YouTube", Modifier.fillMaxWidth()) { tambah = true } }

        item { SectionTitle("Repositori saya (diisi sendiri)") }
        item { Text("Tempat menyimpan alamat repositori ekstensi (mis. untuk Mihon) atau tautan lain. Aplikasi ini tidak menyertakan repositori apa pun; isi dan tanggung jawab sumbernya ada pada Anda.", color = C.Muted, fontSize = 12.sp) }
        items(repos, key = { it.url }) { r ->
            Panel {
                Text(r.nama, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                Text(r.url, color = C.Muted, fontSize = 12.sp, maxLines = 2)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                    SoftButton("Buka di Mihon", Modifier.weight(1f)) { bukaMihon(r.url) }
                    SoftButton("Salin", Modifier.weight(1f)) { salin(r.url) }
                    SoftButton("Hapus", Modifier.weight(1f), danger = true) { repoRaw = repoRaw.lines().filterNot { it.endsWith("|" + r.url) }.joinToString("\n"); vm.settings.repoLinks = repoRaw }
                }
            }
        }
        item { SoftButton("＋ Tambah repositori / tautan", Modifier.fillMaxWidth()) { tambahRepo = true } }

        item { SectionTitle("Komik resmi (gratis dibaca)") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SoftButton("WEBTOON Indonesia ↗", Modifier.weight(1f)) { buka("https://www.webtoons.com/id/") }
                SoftButton("MANGA Plus ↗", Modifier.weight(1f)) { buka("https://mangaplus.shueisha.co.jp/") }
            }
            Text("Tautan ke layanan resmi penerbit. Aplikasi ini tidak menyediakan atau memindai komik dari situs tanpa izin penerbit.", color = C.Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
    if (tambahRepo) {
        var nama by remember { mutableStateOf("") }; var url by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { tambahRepo = false }, title = { Text("Tambah repositori / tautan") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(nama, { nama = it }, "Nama", singleLine = true)
                Field(url, { url = it.trim() }, "Alamat (https://…)", singleLine = true)
            } },
            confirmButton = { TextButton(enabled = url.startsWith("https://") && url.length > 12, onClick = {
                repoRaw = (repoRaw.trim() + "\n" + nama.replace("|", " ").trim() + "|" + url).trim(); vm.settings.repoLinks = repoRaw; tambahRepo = false
            }) { Text("Tambah") } },
            dismissButton = { TextButton(onClick = { tambahRepo = false }) { Text("Batal") } })
    }
    if (tambah) {
        var judul by remember { mutableStateOf("") }; var url by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { tambah = false }, title = { Text("Tambah tautan YouTube") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(judul, { judul = it }, "Judul", singleLine = true)
                Field(url, { url = it.trim() }, "Tautan (https://youtube.com/… atau https://youtu.be/…)", singleLine = true)
            } },
            confirmButton = { TextButton(enabled = videoOk(url), onClick = {
                raw = (raw.trim() + "\n" + judul.replace("|", " ").trim() + "|" + url).trim(); vm.settings.videoLinks = raw; tambah = false
            }) { Text("Tambah") } },
            dismissButton = { TextButton(onClick = { tambah = false }) { Text("Batal") } })
    }
}
