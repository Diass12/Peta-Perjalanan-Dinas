package id.tub.dinas.ui.components

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import id.tub.dinas.MainViewModel
import id.tub.dinas.media.loadEncryptedBitmap

/** Pratinjau foto layar penuh: cubit untuk zoom, ketuk dua kali untuk reset, tombol ‹ › untuk berpindah. */
@Composable
fun ZoomViewer(vm: MainViewModel, paths: List<String>, start: Int, onClose: () -> Unit) {
    if (paths.isEmpty()) { onClose(); return }
    var idx by remember { mutableIntStateOf(start.coerceIn(0, paths.lastIndex)) }
    val path = paths[idx.coerceIn(0, paths.lastIndex)]
    var bmp by remember(path) { mutableStateOf<Bitmap?>(null) }
    var scale by remember(path) { mutableFloatStateOf(1f) }
    var off by remember(path) { mutableStateOf(Offset.Zero) }
    LaunchedEffect(path) { bmp = loadEncryptedBitmap(vm.repo.vault, path, 2400) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        BackHandler(onBack = onClose)
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            Box(Modifier.fillMaxSize().pointerInput(path) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 6f)
                    off = if (scale == 1f) Offset.Zero else off + pan
                }
            }.pointerInput(path) { detectTapGestures(onDoubleTap = { scale = if (scale > 1f) 1f else 2.5f; off = Offset.Zero }) }, contentAlignment = Alignment.Center) {
                bmp?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize().graphicsLayer(scaleX = scale, scaleY = scale, translationX = off.x, translationY = off.y), contentScale = ContentScale.Fit) }
                    ?: Text("Memuat…", color = Color.White)
            }
            Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onClose) { Text("✕ Tutup", color = Color.White) }
                Spacer(Modifier.weight(1f))
                Text("${idx + 1} / ${paths.size}", color = Color.White)
                Spacer(Modifier.width(8.dp))
            }
            if (paths.size > 1) Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(enabled = idx > 0, onClick = { idx -= 1 }) { Text("‹ Sebelumnya", color = Color.White) }
                TextButton(enabled = idx < paths.lastIndex, onClick = { idx += 1 }) { Text("Berikutnya ›", color = Color.White) }
            }
        }
    }
}
