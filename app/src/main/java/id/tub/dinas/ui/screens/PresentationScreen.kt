package id.tub.dinas.ui.screens

import id.tub.dinas.ui.components.Text
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import id.tub.dinas.MainViewModel
import id.tub.dinas.domain.Lens
import id.tub.dinas.domain.OwnerType
import id.tub.dinas.domain.SummaryCalc
import id.tub.dinas.media.EncryptedImage
import id.tub.dinas.ui.components.*
import id.tub.dinas.ui.theme.C

/** Mode presentasi: satu temuan per halaman, foto besar, layar tetap menyala. */
@Composable
fun PresentationScreen(vm: MainViewModel, dinasId: String) {
    val snap by remember(dinasId) { vm.repo.observeSnapshot(dinasId) }.collectAsState(initial = null)
    var idx by remember { mutableIntStateOf(0) }
    val view = LocalView.current
    DisposableEffect(Unit) { view.keepScreenOn = true; onDispose { view.keepScreenOn = false } }
    val s = snap
    val list = s?.temuan?.sortedBy { it.order } ?: emptyList()

    Column(Modifier.fillMaxSize().background(C.Bg)) {
        Header(s?.dinas?.name ?: "Presentasi", onBack = { vm.pop() })
        if (list.isEmpty()) { Empty("Belum ada temuan untuk dipresentasikan."); return@Column }
        val i = idx.coerceIn(0, list.lastIndex)
        val t = list[i]
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("${i + 1} / ${list.size}", color = C.Muted)
            Text(t.title, style = MaterialTheme.typography.titleLarge)
            if (t.code.isNotBlank()) Text(t.code, color = C.Muted)
            s!!.fotoOf(OwnerType.TEMUAN, t.id).forEach { f ->
                val ratio = if (f.height > 0) f.width.toFloat() / f.height else 1.5f
                EncryptedImage(vm.repo.vault, f.path, Modifier.fillMaxWidth().aspectRatio(ratio.coerceIn(0.5f, 2.5f)).clip(RoundedCornerShape(12.dp)), maxSide = 1400, contentScale = ContentScale.Fit)
                if (f.caption.isNotBlank()) Text(f.caption, color = C.Muted)
            }
            if (t.description.isNotBlank()) Text(t.description, style = MaterialTheme.typography.bodyLarge)
            Lens.values().forEach { l ->
                SummaryCalc.lensText(t, l)?.let { txt ->
                    Column(Modifier.fillMaxWidth().background(C.lensBg(l), RoundedCornerShape(10.dp)).padding(12.dp)) {
                        Text(l.label.uppercase(), color = C.lens(l), style = MaterialTheme.typography.labelSmall)
                        Text(txt)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SoftButton("‹ Sebelumnya", Modifier.weight(1f), enabled = i > 0) { idx = i - 1 }
            PrimaryButton("Berikutnya ›", Modifier.weight(1f), enabled = i < list.lastIndex) { idx = i + 1 }
        }
    }
}
