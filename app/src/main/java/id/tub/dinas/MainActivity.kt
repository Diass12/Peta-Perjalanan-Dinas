package id.tub.dinas

import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import id.tub.dinas.security.AppLock
import id.tub.dinas.ui.Screen
import id.tub.dinas.ui.components.AmberButton
import id.tub.dinas.ui.screens.*
import id.tub.dinas.ui.theme.C
import id.tub.dinas.ui.theme.DinasTheme
import id.tub.dinas.ui.theme.ThemeState
import id.tub.dinas.ui.theme.screenBackground
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import id.tub.dinas.media.ImageCache

class MainActivity : FragmentActivity() {
    private val vm: MainViewModel by viewModels()
    private var stoppedAt = 0L
    private var prompting = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        applySecureFlag()
        setContent {
            DinasTheme {
                Root(vm, onUnlock = { requestUnlock() })
            }
        }
    }

    private fun applySecureFlag() {
        // Layar rekaman/tangkapan diblokir saat terkunci, atau selalu bila diaktifkan di Pengaturan.
        if (vm.locked || vm.settings.secureAlways) window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    override fun onStart() {
        super.onStart()
        if (stoppedAt > 0 && vm.settings.lockEnabled && AppLock.available(this)) {
            val away = (SystemClock.elapsedRealtime() - stoppedAt) / 1000
            if (away >= vm.settings.lockTimeoutSec) { vm.locked = true; ImageCache.clear() }
        }
        applySecureFlag()
        if (vm.locked) requestUnlock()
    }

    override fun onPause() {
        super.onPause()
        if (vm.settings.lockEnabled) window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
    }

    override fun onResume() {
        super.onResume()
        applySecureFlag()
    }

    override fun onStop() {
        super.onStop()
        stoppedAt = SystemClock.elapsedRealtime()
        vm.tracker.stop()
    }

    private fun requestUnlock() {
        if (prompting) return
        if (!AppLock.available(this)) { vm.locked = false; applySecureFlag(); return }
        prompting = true
        AppLock.prompt(this,
            onSuccess = { prompting = false; vm.locked = false; applySecureFlag() },
            onError = { prompting = false })
    }
}

@Composable
private fun Root(vm: MainViewModel, onUnlock: () -> Unit) {
    if (vm.locked) { LockScreen(onUnlock); return }
    LaunchedEffect(Unit) { ThemeState.sky = vm.settings.skyTheme }
    SkyBackground {
    BackHandler(enabled = vm.stack.size > 1 || vm.editor != null) {
        if (vm.editor != null) vm.editor = null else vm.pop()
    }
    when (val s = vm.current) {
        Screen.Home -> HomeScreen(vm)
        is Screen.Detail -> DetailScreen(vm, s.dinasId)
        is Screen.Photo -> PhotoViewerScreen(vm, s)
        is Screen.Pdf -> PdfViewerScreen(vm, s)
        Screen.Settings -> SettingsScreen(vm)
        is Screen.Present -> PresentationScreen(vm, s.dinasId)
    }
    }
}

@Composable
private fun SkyBackground(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().screenBackground()) { content() }
}

@Composable
fun LockScreen(onUnlock: () -> Unit) {
    LaunchedEffect(Unit) { onUnlock() }
    Column(Modifier.fillMaxSize().background(C.Ink).padding(32.dp), Arrangement.Center, Alignment.CenterHorizontally) {
        Text("📍", fontSize = 48.sp)
        Spacer(Modifier.height(12.dp))
        Text("Dokumentasi Dinas", color = Color.White, fontSize = 22.sp)
        Text("Aplikasi terkunci", color = Color(0xFFB8C2CB), fontSize = 14.sp)
        Spacer(Modifier.height(24.dp))
        AmberButton("Buka kunci", onClick = onUnlock)
    }
}
