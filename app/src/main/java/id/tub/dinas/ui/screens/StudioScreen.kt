package id.tub.dinas.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.tub.dinas.MainViewModel
import id.tub.dinas.media.EncryptedImage
import id.tub.dinas.media.Studio
import id.tub.dinas.media.loadEncryptedBitmap
import id.tub.dinas.ui.Screen
import id.tub.dinas.ui.components.*
import id.tub.dinas.ui.theme.C
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/** Studio Foto: filter + bingkai untuk satu foto, atau kolase 2 sampai 4 foto dari dinas yang sama. Hasil disimpan sebagai foto BARU; asli tetap utuh. */
@Composable
fun StudioScreen(vm: MainViewModel, s: Screen.Studio) {
    val snap by remember(s.dinasId) { vm.repo.observeSnapshot(s.dinasId) }.collectAsState(initial = null)
    val semua = snap?.foto?.sortedWith(compareBy({ it.ownerId }, { it.order })).orEmpty()
    val awal = snap?.foto?.filter { it.ownerId == s.ownerId }?.sortedBy { it.order }?.getOrNull(s.index)
    var mode by remember { mutableIntStateOf(0) }
    var filterI by remember { mutableIntStateOf(0) }
    var bingkaiI by remember { mutableIntStateOf(0) }
    var teks by remember { mutableStateOf("") }
    var pilih by remember { mutableStateOf(listOf<String>()) }
    var layI by remember { mutableIntStateOf(0) }
    var latarI by remember { mutableIntStateOf(1) }
    var simpan by remember { mutableStateOf(false) }
    LaunchedEffect(awal?.id) { if (awal != null && pilih.isEmpty()) pilih = listOf(awal.id) }
    var editId by remember { mutableStateOf<String?>(null) }
    val fotoEdit = semua.firstOrNull { it.id == editId } ?: awal ?: semua.firstOrNull()

    // Pratinjau (resolusi kecil, dihitung di thread latar)
    val pratinjau by produceState<Bitmap?>(null, fotoEdit?.id, mode, filterI, bingkaiI, teks, pilih, layI, latarI) {
        value = withContext(Dispatchers.Default) { buat(vm, semua, fotoEdit?.path, mode, filterI, bingkaiI, teks, pilih, layI, latarI, 900, 900) }
    }

    Column(Modifier.fillMaxSize().background(C.Bg)) {
        Header("Studio Foto", onBack = { vm.pop() })
        if (snap == null) { Empty("Memuat…"); return@Column }
        if (semua.isEmpty()) { Empty("Belum ada foto di dinas ini."); return@Column }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("🎨 Edit foto", mode == 0) { mode = 0 }
            Chip("🧩 Kolase", mode == 1) { mode = 1; if (pilih.size < 2) pilih = (pilih + semua.map { it.id }).distinct().take(2) }
        }
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFF12202B)), contentAlignment = Alignment.Center) {
            pratinjau?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) } ?: Text("Memproses…", color = Color.White)
        }
        Column(Modifier.fillMaxWidth().heightIn(max = 280.dp).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (mode == 0) {
                Text("Filter", color = C.Muted, fontSize = 12.sp)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { Studio.FILTER.forEachIndexed { i, f -> Chip(f.nama, filterI == i) { filterI = i } } }
                Text("Bingkai", color = C.Muted, fontSize = 12.sp)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { Studio.BINGKAI.forEachIndexed { i, n -> Chip(n, bingkaiI == i) { bingkaiI = i } } }
                if (bingkaiI == 3) Field(teks, { teks = it.take(40) }, "Tulisan di Polaroid (mis. Tobelo, Okt 2026)")
                if (semua.size > 1) {
                    Text("Foto", color = C.Muted, fontSize = 12.sp)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        semua.forEach { f -> Mini(vm, f.path, f.id == fotoEdit?.id) { editId = f.id } }
                    }
                }
            } else {
                Text("Pilih 2 sampai 4 foto (${pilih.size} dipilih)", color = C.Muted, fontSize = 12.sp)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    semua.forEach { f -> Mini(vm, f.path, f.id in pilih) { pilih = if (f.id in pilih) (if (pilih.size > 2) pilih - f.id else pilih) else if (pilih.size < 4) pilih + f.id else pilih } }
                }
                Text("Tata letak", color = C.Muted, fontSize = 12.sp)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { Studio.layouts(pilih.size.coerceIn(2, 4)).forEachIndexed { i, l -> Chip(l.nama, layI == i) { layI = i } } }
                Text("Warna latar", color = C.Muted, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Studio.LATAR.forEachIndexed { i, l -> Chip(l.first, latarI == i) { latarI = i } } }
            }
            PrimaryButton(if (simpan) "Menyimpan…" else "💾 Simpan sebagai foto baru", Modifier.fillMaxWidth()) {
                if (simpan || fotoEdit == null) return@PrimaryButton
                simpan = true
                vm.work(onDone = { simpan = false }) {
                    val bmp = withContext(Dispatchers.Default) { buat(vm, semua, fotoEdit.path, mode, filterI, bingkaiI, teks, pilih, layI, latarI, 2400, 2000) }
                    if (bmp == null) { vm.toast("Gagal membuat gambar"); return@work }
                    val jpeg = withContext(Dispatchers.Default) { ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }.toByteArray() }
                    vm.repo.addFoto(s.dinasId, fotoEdit.ownerType, fotoEdit.ownerId, jpeg, bmp.width, bmp.height, System.currentTimeMillis())
                    vm.toast("Tersimpan sebagai foto baru. Foto asli tidak berubah.")
                }
            }
        }
    }
}

@Composable
private fun Mini(vm: MainViewModel, path: String, aktif: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    EncryptedImage(vm.repo.vault, path, Modifier.size(64.dp).clip(shape).border(if (aktif) 3.dp else 1.dp, if (aktif) C.Accent else C.Line, shape).clickable(onClick = onClick), maxSide = 200)
}

/** Membuat gambar hasil. [sisiMuat] = sisi terpanjang saat memuat foto; [sisiKolase] = sisi kolase. */
private suspend fun buat(vm: MainViewModel, semua: List<id.tub.dinas.domain.FotoModel>, path: String?, mode: Int, filterI: Int, bingkaiI: Int, teks: String,
                         pilih: List<String>, layI: Int, latarI: Int, sisiMuat: Int, sisiKolase: Int): Bitmap? {
    return if (mode == 0) {
        val b = loadEncryptedBitmap(vm.repo.vault, path ?: return null, sisiMuat) ?: return null
        Studio.bingkai(Studio.filter(b, Studio.FILTER[filterI]), bingkaiI, teks)
    } else {
        val bs = pilih.mapNotNull { id -> semua.firstOrNull { it.id == id }?.let { loadEncryptedBitmap(vm.repo.vault, it.path, sisiMuat) } }
        if (bs.size < 2) return null
        val lay = Studio.layouts(bs.size.coerceIn(2, 4)).let { it[layI.coerceIn(0, it.lastIndex)] }
        Studio.kolase(bs, lay, Studio.LATAR[latarI].second, sisiKolase)
    }
}
