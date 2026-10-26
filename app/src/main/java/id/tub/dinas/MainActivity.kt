package id.tub.dinas

import id.tub.dinas.ui.components.Text
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
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
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
    private val vm: MainViewModel by viewModels()
    private var stoppedAt = 0L
    private var prompting = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        applySecureFlag()
        handleShare(intent)
        id.tub.dinas.ui.theme.ThemeState.dark = vm.settings.dark
        id.tub.dinas.security.Lisensi.muat(this, vm.settings.licenseCode)
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch { id.tub.dinas.security.CekOnline.jalan(applicationContext, vm.settings) }
        id.tub.dinas.i18n.Tr.init(this)
        if (vm.settings.versiDilihat < versiKode()) id.tub.dinas.ui.screens.ApaBaru.tampil = true
        id.tub.dinas.i18n.Tr.lang = id.tub.dinas.i18n.AppLang.from(vm.settings.lang)
        setContent {
            val gelap = id.tub.dinas.ui.theme.ThemeState.dark
            androidx.compose.runtime.SideEffect {
                val c = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                c.isAppearanceLightStatusBars = !gelap; c.isAppearanceLightNavigationBars = !gelap
            }
            DinasTheme {
                Root(vm, onUnlock = { requestUnlock() })
                if (!vm.locked) id.tub.dinas.ui.screens.ApaBaruDialog(vm, versiKode(), versiNama())
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShare(intent)
    }

    private fun versiKode(): Int = try { packageManager.getPackageInfo(packageName, 0).let { if (android.os.Build.VERSION.SDK_INT >= 28) it.longVersionCode.toInt() else @Suppress("DEPRECATION") it.versionCode } } catch (e: Exception) { 0 }
    private fun versiNama(): String = try { packageManager.getPackageInfo(packageName, 0).versionName ?: "" } catch (e: Exception) { "" }

    private fun handleShare(i: android.content.Intent?) {
        i?.getStringExtra(id.tub.dinas.widget.DinasWidget.EXTRA)?.takeIf { it == "gps" || it == "kamera" }?.let { vm.aksiWidget = it; i.removeExtra(id.tub.dinas.widget.DinasWidget.EXTRA) }
        if (i?.action == android.content.Intent.ACTION_SEND && i.type?.startsWith("text/") == true) {
            i.getStringExtra(android.content.Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }?.let { vm.sharedText = it.take(2000) }
            i.action = null
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
        id.tub.dinas.widget.DinasWidget.perbarui(this)
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
    val appCtx = androidx.compose.ui.platform.LocalContext.current.applicationContext
    LaunchedEffect(Unit) {
        ThemeState.playerName = vm.settings.playerName
        id.tub.dinas.ui.components.Sfx.init(appCtx); id.tub.dinas.ui.components.Sfx.enabled = vm.settings.sfx
    }
    val aksiW = vm.aksiWidget
    LaunchedEffect(aksiW) {
        if (aksiW != null) { vm.aksiWidget = null; vm.work { jalankanAksiWidget(vm, aksiW) } }
    }
    SkyBackground {
    BackHandler(enabled = vm.stack.size > 1 || vm.editor != null || vm.mainTab != 0) {
        if (vm.editor != null) vm.editor = null else if (!vm.pop()) vm.mainTab = 0
    }
    when (val s = vm.current) {
        Screen.Home -> ShellScreen(vm)
        is Screen.Detail -> DetailScreen(vm, s.dinasId)
        is Screen.Photo -> PhotoViewerScreen(vm, s)
        is Screen.Studio -> id.tub.dinas.ui.screens.StudioScreen(vm, s)
        is Screen.Pdf -> PdfViewerScreen(vm, s)
        Screen.Settings -> SettingsScreen(vm)
        Screen.Aset -> AsetScreen(vm)
        Screen.UangMuka -> UmScreen(vm)
        Screen.Jadwal -> JadwalScreen(vm)
        Screen.Profil -> ProfilScreen(vm)
        Screen.Galeri -> GaleriScreen(vm)
        Screen.Dokumen -> DokumenScreen(vm)
        is Screen.Present -> PresentationScreen(vm, s.dinasId)
    }
    vm.sharedText?.let { id.tub.dinas.ui.components.SharedLocationDialog(vm, it) }
    id.tub.dinas.ui.components.HasilDialog(vm)
    PembaruanDialog()
    id.tub.dinas.ui.components.LoadingOverlay(aktif = vm.busy || vm.aset.sibuk != null, pesan = vm.aset.sibuk, onBerhenti = if (vm.aset.sibuk != null) ({ vm.aset.berhenti = true }) else null)
    }
}

@Composable
private fun SkyBackground(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().screenBackground()) {
        content()
    }
}

@Composable
fun LockScreen(onUnlock: () -> Unit) {
    LaunchedEffect(Unit) { onUnlock() }
    Column(Modifier.fillMaxSize().background(Color(0xFF0F172A)).padding(32.dp), Arrangement.Center, Alignment.CenterHorizontally) {
        Text("📍", fontSize = 48.sp)
        Spacer(Modifier.height(12.dp))
        Text("Dokumentasi Dinas", color = Color.White, fontSize = 22.sp)
        Text("Aplikasi terkunci", color = Color(0xFFB8C2CB), fontSize = 14.sp)
        Spacer(Modifier.height(24.dp))
        AmberButton("Buka kunci", onClick = onUnlock)
    }
}

@Composable
private fun PembaruanDialog() {
    val u = id.tub.dinas.security.CekOnline.update
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val buka = { runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(u?.url ?: "")).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }; Unit }
    if (u != null && u.wajib) {
        Column(Modifier.fillMaxSize().background(Color(0xFF0F172A)).padding(32.dp), Arrangement.Center, Alignment.CenterHorizontally) {
            Text("⬆️", fontSize = 48.sp); Spacer(Modifier.height(12.dp))
            Text("Perbarui aplikasi", color = Color.White, fontSize = 22.sp)
            Text("Versi ini sudah tidak didukung. Unduh versi terbaru${if (u.nama.isNotBlank()) " (${u.nama})" else ""}, lalu pasang untuk menimpa — data Anda tetap aman.", color = Color(0xFFB8C2CB), fontSize = 14.sp)
            Spacer(Modifier.height(24.dp)); AmberButton("Unduh versi terbaru", onClick = buka)
        }
    } else if (u != null && !id.tub.dinas.security.CekOnline.abaikan) {
        androidx.compose.material3.AlertDialog(onDismissRequest = { id.tub.dinas.security.CekOnline.abaikan = true },
            title = { Text("Ada versi baru") },
            text = { Column(Modifier.heightIn(max = 320.dp).verticalScroll(androidx.compose.foundation.rememberScrollState())) { Text("Versi terbaru${if (u.nama.isNotBlank()) " ${u.nama}" else ""} tersedia. Unduh, lalu pasang untuk menimpa; data tetap aman."); if (u.catatan.isNotBlank()) { Spacer(Modifier.height(8.dp)); Text("Yang berubah:", fontSize = 14.sp); Text(u.catatan, color = Color(0xFF64748B), fontSize = 14.sp) } } },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { id.tub.dinas.security.CekOnline.abaikan = true; buka() }) { Text("Unduh") } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { id.tub.dinas.security.CekOnline.abaikan = true }) { Text("Nanti") } })
    }
    val pl = id.tub.dinas.security.CekOnline.pesanLisensi
    if (pl.isNotBlank()) androidx.compose.material3.AlertDialog(onDismissRequest = { id.tub.dinas.security.CekOnline.pesanLisensi = "" },
        title = { Text("Lisensi") }, text = { Text(pl) },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { id.tub.dinas.security.CekOnline.pesanLisensi = "" }) { Text("OK") } })
}
