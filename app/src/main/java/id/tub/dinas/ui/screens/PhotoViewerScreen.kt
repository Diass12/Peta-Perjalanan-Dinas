package id.tub.dinas.ui.screens

import id.tub.dinas.ui.components.Text
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import id.tub.dinas.MainViewModel
import id.tub.dinas.domain.OwnerType
import id.tub.dinas.media.loadEncryptedBitmap
import id.tub.dinas.ui.Screen
import id.tub.dinas.ui.components.Header
import id.tub.dinas.ui.components.HeaderAction
import id.tub.dinas.ui.theme.C

@Composable
fun PhotoViewerScreen(vm: MainViewModel, s: Screen.Photo) {
    val snap by remember(s.dinasId) { vm.repo.observeSnapshot(s.dinasId) }.collectAsState(initial = null)
    val fotos = snap?.foto?.filter { it.ownerId == s.ownerId }?.sortedBy { it.order } ?: emptyList()
    var idx by remember { mutableIntStateOf(s.index) }
    if (fotos.isEmpty()) { Column(Modifier.fillMaxSize().background(Color.Black)) { Header("Foto", onBack = { vm.pop() }) }; return }
    val i = idx.coerceIn(0, fotos.lastIndex)
    val f = fotos[i]
    var bmp by remember(f.path) { mutableStateOf<Bitmap?>(null) }
    var scale by remember(f.path) { mutableFloatStateOf(1f) }
    var off by remember(f.path) { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    LaunchedEffect(f.path) { bmp = loadEncryptedBitmap(vm.repo.vault, f.path, 2400) }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Header("Foto ${i + 1} / ${fotos.size}", onBack = { vm.pop() }, actions = {
            HeaderAction("🎨") { vm.push(Screen.Studio(s.dinasId, s.ownerId, i)) }
            if (i > 0) HeaderAction("‹") { idx = i - 1 }
            if (i < fotos.lastIndex) HeaderAction("›") { idx = i + 1 }
        })
        Box(Modifier.weight(1f).fillMaxWidth().pointerInput(f.path) {
            detectTransformGestures { _, pan, zoom, _ ->
                scale = (scale * zoom).coerceIn(1f, 6f)
                off = if (scale == 1f) androidx.compose.ui.geometry.Offset.Zero else off + pan
            }
        }, contentAlignment = Alignment.Center) {
            bmp?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize().graphicsLayer(scaleX = scale, scaleY = scale, translationX = off.x, translationY = off.y), contentScale = ContentScale.Fit) }
                ?: Text("Memuat…", color = Color.White)
        }
        if (f.caption.isNotBlank()) Text(f.caption, color = Color.White, modifier = Modifier.fillMaxWidth().background(Color(0xFF12202B)).padding(14.dp))
    }
}
