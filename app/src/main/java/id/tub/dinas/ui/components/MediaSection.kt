package id.tub.dinas.ui.components

import android.Manifest
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import id.tub.dinas.MainViewModel
import id.tub.dinas.domain.MediaKind
import id.tub.dinas.domain.MediaModel
import id.tub.dinas.domain.OwnerType
import id.tub.dinas.ui.theme.C
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private fun stampName(prefix: String, ext: String) = "$prefix ${SimpleDateFormat("d MMM HH.mm", Locale("id", "ID")).format(Date())}.$ext"
private fun mmss(sec: Int) = String.format(Locale.US, "%02d:%02d", sec / 60, sec % 60)
private fun sizeText(b: Long) = if (b >= 1024 * 1024) String.format(Locale.US, "%.1f MB", b / 1048576.0) else "${b / 1024} KB"

/** Video dan rekaman suara: rekam langsung atau ambil dari galeri/berkas. Semuanya disimpan terenkripsi. */
@Composable
fun MediaSection(vm: MainViewModel, dinasId: String, type: OwnerType, ownerId: String, items: List<MediaModel>) {
    val ctx = LocalContext.current
    var vidPath by rememberSaveable { mutableStateOf<String?>(null) }
    var delId by remember { mutableStateOf<String?>(null) }
    var playing by remember { mutableStateOf<MediaModel?>(null) }
    var recording by remember { mutableStateOf(false) }

    val vidPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(5)) { uris ->
        uris.forEach { vm.addMediaUri(dinasId, type, ownerId, MediaKind.VIDEO, it) }
    }
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { vm.addMediaUri(dinasId, type, ownerId, MediaKind.AUDIO, it) }
    }
    val videoCam = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()) { ok ->
        val p = vidPath; vidPath = null
        if (p != null) { val f = File(p); if (ok && f.length() > 0) vm.addMediaFile(dinasId, type, ownerId, MediaKind.VIDEO, f, stampName("Video", "mp4"), "mp4") else f.delete() }
    }
    fun launchVideo() {
        val dir = File(ctx.cacheDir, "media").apply { mkdirs() }
        val f = File(dir, "vid_${UUID.randomUUID()}.mp4")
        vidPath = f.absolutePath
        videoCam.launch(FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f))
    }
    val camPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) launchVideo() else vm.toast("Izin kamera ditolak. Pakai \"Pilih video\" untuk mengambil dari galeri.")
    }
    val micPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) recording = true else vm.toast("Izin mikrofon ditolak. Pakai \"Pilih audio\" untuk mengambil berkas rekaman.")
    }

    Panel {
        Text("VIDEO & SUARA (${items.size})", style = MaterialTheme.typography.labelSmall, color = C.Muted)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton("🎬 Rekam video") {
                if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) launchVideo()
                else camPerm.launch(Manifest.permission.CAMERA)
            }
            PrimaryButton("🎙 Rekam suara") {
                if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) recording = true
                else micPerm.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SoftButton("Pilih video") { vidPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) }
            SoftButton("Pilih audio") { audioPicker.launch(arrayOf("audio/*")) }
        }
        items.forEach { m ->
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (m.kind == MediaKind.VIDEO) "🎬" else "🎙", fontSize = 24.sp)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(m.name, maxLines = 1, fontWeight = FontWeight.Bold)
                    Text("${m.kind.label} · ${sizeText(m.sizeBytes)}", color = C.Muted, style = MaterialTheme.typography.bodyMedium)
                }
                TextButton({ playing = m }) { Text("Putar") }
                TextButton({ delId = m.id }) { Text("Hapus", color = C.Danger) }
            }
        }
    }

    if (recording) AudioRecorderDialog(
        onDone = { f -> recording = false; vm.addMediaFile(dinasId, type, ownerId, MediaKind.AUDIO, f, stampName("Rekaman", "m4a"), "m4a") },
        onCancel = { recording = false },
    )
    playing?.let { MediaPlayerDialog(vm, it) { playing = null } }
    delId?.let { id -> Confirm("Hapus berkas?", "Video atau rekaman ini dihapus permanen dari aplikasi.", onConfirm = { vm.work { vm.repo.deleteMedia(id) } }, onDismiss = { delId = null }) }
}

@Composable
private fun AudioRecorderDialog(onDone: (File) -> Unit, onCancel: () -> Unit) {
    val ctx = LocalContext.current
    val file = remember { File(File(ctx.cacheDir, "media").apply { mkdirs() }, "rec_${UUID.randomUUID()}.m4a") }
    var secs by remember { mutableIntStateOf(0) }
    var failed by remember { mutableStateOf<String?>(null) }
    var rec by remember { mutableStateOf<MediaRecorder?>(null) }

    fun finish(save: Boolean) {
        val r = rec; rec = null
        var ok = r != null
        try { r?.stop() } catch (_: RuntimeException) { ok = false }
        runCatching { r?.release() }
        if (save && ok && file.length() > 0) onDone(file) else { file.delete(); onCancel() }
    }

    DisposableEffect(Unit) {
        try {
            val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(ctx) else @Suppress("DEPRECATION") MediaRecorder()
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(96_000); r.setAudioSamplingRate(44_100)
            r.setOutputFile(file.absolutePath)
            r.prepare(); r.start()
            rec = r
        } catch (e: Exception) { failed = "Perekam tidak dapat dimulai: ${e.message ?: "mikrofon dipakai aplikasi lain?"}" }
        onDispose {
            val r = rec; rec = null
            if (r != null) { runCatching { r.stop() }; runCatching { r.release() }; file.delete() }
        }
    }
    LaunchedEffect(Unit) { while (true) { delay(1000); secs++ } }

    AlertDialog(
        onDismissRequest = { finish(false) },
        title = { Text(if (failed == null) "Merekam suara…" else "Gagal merekam") },
        text = { Text(failed ?: mmss(secs), fontSize = if (failed == null) 36.sp else 14.sp, fontWeight = FontWeight.Bold) },
        confirmButton = { if (failed == null) TextButton({ finish(true) }) { Text("Berhenti & simpan", fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton({ if (failed != null) onCancel() else finish(false) }) { Text(if (failed != null) "Tutup" else "Batal") } },
    )
}

@Composable
private fun MediaPlayerDialog(vm: MainViewModel, m: MediaModel, onClose: () -> Unit) {
    var file by remember { mutableStateOf<File?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    val ext = m.path.removeSuffix(".enc").substringAfterLast('.', "bin")
    LaunchedEffect(m.id) {
        try { file = withContext(Dispatchers.IO) { vm.repo.mediaTempFile(m.path, ".$ext") } }
        catch (e: Exception) { err = "Tidak dapat dibuka: ${e.message ?: "berkas rusak"}" }
    }
    DisposableEffect(m.id) { onDispose { file?.delete() } }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth().padding(12.dp), shape = RoundedCornerShape(16.dp), color = Color(0xFF0B2545)) {
            Column(Modifier.padding(14.dp)) {
                Text(m.name, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 2)
                Spacer(Modifier.height(8.dp))
                val f = file
                when {
                    err != null -> Text(err!!, color = Color(0xFFFFB4AB))
                    f == null -> Text("Menyiapkan (mendekripsi)…", color = Color.White)
                    m.kind == MediaKind.VIDEO -> AndroidView(
                        factory = { c ->
                            VideoView(c).apply {
                                setVideoPath(f.path)
                                setMediaController(MediaController(c).also { it.setAnchorView(this) })
                                setOnPreparedListener { start() }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(320.dp),
                        onRelease = { it.stopPlayback() },
                    )
                    else -> AudioPlayer(f)
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClose, Modifier.align(Alignment.End)) { Text("Tutup", color = Color(0xFFFFD166), fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun AudioPlayer(f: File) {
    var playing by remember { mutableStateOf(false) }
    var pos by remember { mutableIntStateOf(0) }
    var dur by remember { mutableIntStateOf(0) }
    val mp = remember(f) {
        MediaPlayer().apply { setDataSource(f.path); prepare() }
    }
    DisposableEffect(mp) { onDispose { runCatching { mp.stop() }; runCatching { mp.release() } } }
    LaunchedEffect(mp) {
        dur = mp.duration / 1000
        mp.setOnCompletionListener { playing = false; pos = 0 }
        while (true) { delay(300); if (playing) pos = mp.currentPosition / 1000 }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        PrimaryButton(if (playing) "⏸ Jeda" else "▶ Putar") {
            if (playing) { mp.pause(); playing = false } else { mp.start(); playing = true }
        }
        Spacer(Modifier.width(12.dp))
        Text("${mmss(pos)} / ${mmss(dur)}", color = Color.White, fontSize = 16.sp)
    }
}
