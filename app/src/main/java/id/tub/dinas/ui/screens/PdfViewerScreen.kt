package id.tub.dinas.ui.screens

import android.graphics.Bitmap
import android.graphics.Color as AColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import id.tub.dinas.MainViewModel
import id.tub.dinas.ui.Screen
import id.tub.dinas.ui.components.*
import id.tub.dinas.ui.theme.C
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** Penampil PDF: dekripsi ke berkas sementara di cache, render per halaman, hapus saat ditutup. */
@Composable
fun PdfViewerScreen(vm: MainViewModel, s: Screen.Pdf) {
    var state by remember(s.path) { mutableStateOf<Triple<File, PdfRenderer, ParcelFileDescriptor>?>(null) }
    var error by remember(s.path) { mutableStateOf<String?>(null) }
    val mutex = remember { Mutex() }
    var zoom by remember { mutableFloatStateOf(1f) }

    LaunchedEffect(s.path) {
        try {
            state = withContext(Dispatchers.IO) {
                val f = vm.repo.pdfTempFile(s.path)
                val pfd = ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
                Triple(f, PdfRenderer(pfd), pfd)
            }
        } catch (e: Exception) { error = "PDF tidak dapat dibuka: ${e.message ?: "berkas rusak"}" }
    }
    DisposableEffect(s.path) {
        onDispose {
            state?.let { (f, r, p) -> runCatching { r.close() }; runCatching { p.close() }; f.delete() }
        }
    }

    Column(Modifier.fillMaxSize().background(C.Bg)) {
        Header(s.name, onBack = { vm.pop() }, actions = {
            HeaderAction("−") { zoom = (zoom - 0.25f).coerceAtLeast(1f) }
            HeaderAction("+") { zoom = (zoom + 0.25f).coerceAtMost(3f) }
        })
        val st = state
        when {
            error != null -> Empty(error!!)
            st == null -> Empty("Memuat PDF…")
            else -> {
                val renderer = st.second
                val screenW = LocalConfiguration.current.screenWidthDp
                val widthDp = (screenW * zoom).dp
                val widthPx = with(LocalDensity.current) { widthDp.roundToPx() }.coerceAtMost(2400)
                Box(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
                    LazyColumn(Modifier.width(widthDp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items((0 until renderer.pageCount).toList()) { page ->
                            var bmp by remember(page, widthPx) { mutableStateOf<Bitmap?>(null) }
                            LaunchedEffect(page, widthPx) {
                                bmp = withContext(Dispatchers.IO) {
                                    mutex.withLock {
                                        runCatching {
                                            renderer.openPage(page).use { p ->
                                                val h = (widthPx.toFloat() / p.width * p.height).toInt().coerceAtLeast(1)
                                                Bitmap.createBitmap(widthPx, h, Bitmap.Config.ARGB_8888).also {
                                                    it.eraseColor(AColor.WHITE)
                                                    p.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                                }
                                            }
                                        }.getOrNull()
                                    }
                                }
                            }
                            bmp?.let { Image(it.asImageBitmap(), "Halaman ${page + 1}", Modifier.fillMaxWidth()) }
                                ?: Box(Modifier.fillMaxWidth().height(300.dp).background(androidx.compose.ui.graphics.Color.White))
                        }
                    }
                }
            }
        }
    }
}
