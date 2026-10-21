package id.tub.dinas.ui.screens

import id.tub.dinas.ui.theme.ThemeState
import id.tub.dinas.ui.components.Text
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.tub.dinas.MainViewModel
import id.tub.dinas.aset.AsetController
import id.tub.dinas.domain.*
import id.tub.dinas.media.EncryptedImage

/** Palet modul Aset: meniru tampilan AsetScan (ungu–magenta–oranye, kartu gelap). */
object A {
    private fun p(l: Long, d: Long) = Color(if (ThemeState.dark) d else l)
    val Bg get() = p(0xFFF5F7FB, 0xFF0E0D22)
    val Card get() = p(0xFFFFFFFF, 0xFF1A1936)
    val Card2 get() = p(0xFFEEF1F6, 0xFF26244C)
    val Line get() = p(0xFFE5E7EB, 0xFF2E2C54)
    val Ink get() = p(0xFF0F172A, 0xFFF3F2FF)
    val Muted get() = p(0xFF6B7280, 0xFFA9A7CC)
    val Accent get() = p(0xFF15305A, 0xFF9B82FF)
    val Green get() = p(0xFF059669, 0xFF34D399)
    val Amber get() = p(0xFFD97706, 0xFFFBBF24)
    val Red get() = p(0xFFDC2626, 0xFFF87171)
    val Blue get() = p(0xFF2563EB, 0xFF60A5FA)
    val Pink get() = p(0xFFC026D3, 0xFFE879F9)
    val Grad: Brush get() = if (ThemeState.dark) Brush.horizontalGradient(listOf(Color(0xFF6C4DF6), Color(0xFFC94FE0), Color(0xFFF08A5D))) else Brush.horizontalGradient(listOf(Color(0xFF15305A), Color(0xFF15305A)))
    val Head: Brush get() = if (ThemeState.dark) Brush.linearGradient(listOf(Color(0xFF5B3FE0), Color(0xFFB04FE6), Color(0xFFF08A5D))) else Brush.linearGradient(listOf(Color.White, Color.White))
    private val cat = listOf(Color(0xFFD97706), Color(0xFF2563EB), Color(0xFF0D9488), Color(0xFF7C3AED), Color(0xFFDB2777))
    fun cat(k: String): Color = cat[KodeAset.KATEGORI.indexOf(k).coerceAtLeast(0) % cat.size]
    fun state(s: AuditState): Color = when (s) { AuditState.FOUND -> Green; AuditState.MISSING -> Red; AuditState.DAMAGED -> Amber; AuditState.PENDING -> Muted }
}

@Composable
fun AHeader(title: String, sub: String, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}, extra: @Composable ColumnScope.() -> Unit = {}) {
    Column(Modifier.fillMaxWidth().background(A.Head).statusBarsPadding().padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (onBack != null) Text("‹", color = A.Ink, fontSize = 34.sp, modifier = Modifier.clickable { onBack() }.padding(end = 8.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = A.Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(sub, color = A.Muted, fontSize = 13.sp, maxLines = 1)
            }
            actions()
        }
        extra()
    }
}

@Composable
fun IconBtn(glyph: String, active: Boolean = false, onClick: () -> Unit) {
    Box(Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(if (active) A.Accent else A.Card2).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(glyph, fontSize = 18.sp, color = if (active) Color.White else A.Ink, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ACard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) =
    Column(modifier.fillMaxWidth().background(A.Card, RoundedCornerShape(16.dp)).border(1.dp, A.Line, RoundedCornerShape(16.dp)).padding(16.dp), content = content)

@Composable
fun ATitle(glyph: String, text: String) = Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
    Text(glyph, fontSize = 18.sp); Spacer(Modifier.width(8.dp)); Text(text, color = A.Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
}

@Composable
fun AGradButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) =
    Box(modifier.clip(RoundedCornerShape(16.dp)).background(A.Grad).clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
        Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }

@Composable
fun ADarkButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) =
    Box(modifier.clip(RoundedCornerShape(16.dp)).background(A.Card2).clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
        Text(text, color = A.Ink, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }

@Composable
fun ATag(text: String, fg: Color, bg: Color) =
    Text(text, color = fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.background(bg, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 4.dp))

@Composable
fun AChip(text: String, selected: Boolean, dot: Color? = null, onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Row(Modifier.clip(shape).then(if (selected) Modifier.background(A.Grad) else Modifier.background(A.Card).border(1.dp, A.Line, shape))
        .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        if (dot != null) { Box(Modifier.size(9.dp).background(dot, CircleShape)); Spacer(Modifier.width(7.dp)) }
        Text(text, color = if (selected) Color.White else A.Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
fun APill(label: String, options: List<String>, selected: String?, modifier: Modifier = Modifier, onPick: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(22.dp)
    Box(modifier) {
        Row(Modifier.fillMaxWidth().clip(shape).background(A.Card).border(1.dp, A.Line, shape).clickable { open = true }.padding(horizontal = 16.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(selected ?: label, color = A.Ink, fontSize = 14.sp, maxLines = 1, modifier = Modifier.weight(1f))
            Text("⌄", color = A.Muted, fontSize = 16.sp)
        }
        DropdownMenu(open, { open = false }) {
            DropdownMenuItem({ Text(label) }, { onPick(null); open = false })
            options.forEach { o -> DropdownMenuItem({ Text(o) }, { onPick(o); open = false }) }
        }
    }
}

@Composable
fun ARing(fraction: Float, size: Dp, stroke: Dp, track: Color, color: Brush, center: @Composable () -> Unit) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = stroke.toPx(); val inset = sw / 2
            val sz = Size(this.size.width - sw, this.size.height - sw)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), sz, style = Stroke(sw))
            if (fraction > 0f) drawArc(color, -90f, 360f * fraction.coerceIn(0f, 1f), false, Offset(inset, inset), sz, style = Stroke(sw, cap = StrokeCap.Round))
        }
        center()
    }
}

@Composable
private fun ScanGlyph() = Canvas(Modifier.size(30.dp)) {
    val w = size.width; val sw = 3.dp.toPx(); val p = sw / 2; val l = w * 0.32f
    fun ln(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(Color.White, Offset(x1, y1), Offset(x2, y2), sw, StrokeCap.Round)
    ln(p, p, l, p); ln(p, p, p, l); ln(w - p, p, w - l, p); ln(w - p, p, w - p, l)
    ln(p, w - p, l, w - p); ln(p, w - p, p, w - l); ln(w - p, w - p, w - l, w - p); ln(w - p, w - p, w - p, w - l)
    ln(w * 0.16f, w / 2, w * 0.84f, w / 2)
}

/** Navigasi bawah: Home · Aset · [Scan] · Audit · Menu. */
@Composable
fun ANav(page: Int, onPage: (Int) -> Unit, onScan: () -> Unit) {
    @Composable
    fun RowScope.Item(glyph: String, label: String, on: Boolean, click: () -> Unit) = Column(Modifier.weight(1f).clickable(onClick = click).padding(vertical = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(glyph, fontSize = 22.sp, modifier = Modifier.alpha(if (on) 1f else 0.55f))
        Text(label, fontSize = 12.sp, fontWeight = if (on) FontWeight.ExtraBold else FontWeight.Medium, color = if (on) A.Accent else A.Muted)
    }
    Box(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().background(A.Card).navigationBarsPadding().padding(top = 10.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Item("🏠", "Home", page == 0) { onPage(0) }
            Item("📦", "Aset", page == 1) { onPage(1) }
            Spacer(Modifier.weight(1f))
            Item("✅", "Audit", page == 2) { onPage(2) }
            Item("☰", "Menu", page == 3) { onPage(3) }
        }
        Box(Modifier.align(Alignment.TopCenter).offset(y = (-26).dp).size(70.dp).clip(RoundedCornerShape(24.dp)).background(A.Grad).clickable(onClick = onScan), contentAlignment = Alignment.Center) { ScanGlyph() }
    }
}

private fun rupiah(d: Double): String = when {
    d >= 1e12 -> String.format(java.util.Locale.US, "%.2f T", d / 1e12)
    d >= 1e9 -> String.format(java.util.Locale.US, "%.2f M", d / 1e9)
    d >= 1e6 -> String.format(java.util.Locale.US, "%.1f jt", d / 1e6)
    else -> String.format(java.util.Locale.US, "%,.0f", d)
}

@Composable
private fun StatTile(value: String, label: String, glyph: String, c1: Color, c2: Color, modifier: Modifier, onClick: () -> Unit) =
    Box(modifier.clip(RoundedCornerShape(24.dp)).background(c1).clickable(onClick = onClick).padding(16.dp)) {
        Text(glyph, fontSize = 26.sp, modifier = Modifier.align(Alignment.TopEnd).alpha(0.5f))
        Column { Text(value, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold); Text(label, color = Color(0xE6FFFFFF), fontSize = 14.sp) }
    }

@Composable
private fun BarRow(label: String, share: Float, right: String, color: Brush, dot: Color? = null, onClick: () -> Unit) =
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(0.42f), verticalAlignment = Alignment.CenterVertically) {
            if (dot != null) { Box(Modifier.size(10.dp).background(dot, CircleShape)); Spacer(Modifier.width(8.dp)) }
            Text(label, color = A.Ink, fontSize = 15.sp, maxLines = 2)
        }
        Box(Modifier.weight(0.42f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(A.Card2)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(share.coerceIn(0.03f, 1f)).clip(RoundedCornerShape(6.dp)).background(color))
        }
        Text(right, color = A.Muted, fontSize = 14.sp, modifier = Modifier.weight(0.16f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }

// ---------------- HOME ----------------

@Composable
fun AHome(vm: MainViewModel, list: List<AsetModel>, fotos: Map<String, List<AsetFotoModel>>, onScanAudit: () -> Unit, go: (AsetController.() -> Unit) -> Unit) {
    val n = list.size
    val done = list.count { it.state != AuditState.PENDING }
    val dup = vm.aset.duplikat(list)
    val frac = if (n == 0) 0f else done.toFloat() / n
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        AHeader("Register Aset", "Audit & register aset", onBack = { vm.pop() })
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            ACard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ARing(frac, 112.dp, 14.dp, A.Card2, A.Grad) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${Math.round(frac * 100)}%", color = A.Ink, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                            Text("teraudit", color = A.Muted, fontSize = 12.sp)
                        }
                    }
                    Spacer(Modifier.width(18.dp))
                    Column {
                        Text("Progres Audit", color = A.Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("$done dari $n", color = A.Muted, fontSize = 15.sp)
                        Spacer(Modifier.height(4.dp))
                        Row { Text("Total NBV: ", color = A.Ink, fontSize = 15.sp); Text(rupiah(list.sumOf { it.nbv ?: 0.0 }), color = A.Accent, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
                        Spacer(Modifier.height(10.dp))
                        AGradButton("Mulai Scan Audit", onClick = onScanAudit)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("$n", "Total Aset", "📦", Color(0xFF2563EB), Color(0xFF2563EB), Modifier.weight(1f)) { go { } }
                StatTile("${list.count { it.state == AuditState.FOUND }}", "Ditemukan", "✔", Color(0xFF15803D), Color(0xFF15803D), Modifier.weight(1f)) { go { auditFilter = AuditState.FOUND } }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("${list.count { it.state == AuditState.MISSING || it.state == AuditState.DAMAGED }}", "Hilang / Rusak", "⚠", Color(0xFFDC2626), Color(0xFFDC2626), Modifier.weight(1f)) { go { auditFilter = AuditState.MISSING } }
                StatTile("${list.count { it.state == AuditState.PENDING }}", "Belum Diaudit", "🏷", Color(0xFF6B7280), Color(0xFF6B7280), Modifier.weight(1f)) { go { auditFilter = AuditState.PENDING } }
            }
            ACard {
                ATitle("🏷", "Per Kategori")
                KodeAset.KATEGORI.forEach { k ->
                    val tot = list.count { it.category == k }
                    if (tot > 0) BarRow(KodeAset.labelKategori(k), tot.toFloat() / n, "${list.count { it.category == k && it.state == AuditState.FOUND }}/$tot", Brush.horizontalGradient(listOf(A.cat(k), A.cat(k))), A.cat(k)) { go { kategori = k } }
                }
                if (n == 0) Text("Belum ada aset.", color = A.Muted)
            }
            val depts = list.map { it.dept.trim() }.filter { it.isNotEmpty() }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(8)
            if (depts.isNotEmpty()) ACard {
                ATitle("🏢", "Per Departemen")
                depts.forEach { (d, m) -> BarRow(d, m.toFloat() / depts.first().value, "$m", A.Grad) { go { deptFilter = d } } }
            }
            ACard {
                ATitle("🏷", "Status Barcode")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AChip("Tagged  ${list.count { it.barcode == StatusBarcode.TAGGED }}", false, A.Green) { go { barcode = StatusBarcode.TAGGED } }
                    AChip("Untagged  ${list.count { it.barcode == StatusBarcode.UNTAGGED }}", false, A.Amber) { go { barcode = StatusBarcode.UNTAGGED } }
                }
                Spacer(Modifier.height(8.dp))
                AChip("Non Aset  ${list.count { it.barcode == StatusBarcode.NONASSET || it.barcode == null }}", false, A.Muted) { go { barcode = StatusBarcode.NONASSET } }
            }
            ACard {
                ATitle("🏢", "Per PT & Kepemilikan")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AChip("Milik  ${list.count { it.tenure != "lease" }}", false, A.Green) { }
                    AChip("Sewa  ${list.count { it.tenure == "lease" }}", false, A.Amber) { }
                }
                val pts = list.map { it.owner.trim() }.filter { it.isNotEmpty() }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }
                pts.forEach { (pt, m) -> Row(Modifier.fillMaxWidth().clickable { go { ownerFilter = pt } }.padding(top = 10.dp)) { Text(pt, color = A.Ink, modifier = Modifier.weight(1f)); Text("$m", color = A.Muted) } }
            }
            ACard {
                ATitle("🧹", "Kualitas Data")
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    @Composable
                    fun Q(count: Int, label: String, color: Color, key: String) {
                        val shape = RoundedCornerShape(22.dp)
                        Row(Modifier.clip(shape).border(1.5.dp, color, shape).clickable(enabled = count > 0) { go { peringatan = key } }.padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("$count", color = color, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp); Spacer(Modifier.width(6.dp))
                            Text(label, color = A.Ink, fontSize = 14.sp)
                        }
                    }
                    Q(list.count { fotos[it.id].isNullOrEmpty() }, "Tanpa Foto", A.Amber, "nofoto")
                    Q(list.count { it.code.isEmpty() }, "Tanpa Kode", A.Red, "nokode")
                    Q(list.count { it.dept.isBlank() }, "Tanpa Dept", A.Blue, "nodept")
                    Q(list.count { it.owner.isBlank() }, "Tanpa PT", A.Green, "noowner")
                    Q(list.count { KodeAset.norm(it.code) in dup }, "Kode Ganda", A.Pink, "dup")
                    Q(list.count { it.name.isBlank() }, "Tanpa Nama", A.Amber, "noname")
                    Q(list.count { it.location.isBlank() }, "Tanpa Lokasi", A.Blue, "noloc")
                    Q(list.count { it.code.isNotEmpty() && KodeAset.validasi(it.code) == null }, "Kode Tak Valid", A.Red, "badcode")
                }
            }
        }
    }
}

// ---------------- ASET (daftar) ----------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AAssetCard(vm: MainViewModel, a: AsetModel, fotos: List<AsetFotoModel>) {
    val c = vm.aset
    val picked = a.id in c.selected
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(RoundedCornerShape(22.dp)).background(A.Card)
        .then(if (picked) Modifier.border(2.dp, A.Accent, RoundedCornerShape(22.dp)) else Modifier)
        .combinedClickable(
            onClick = { if (c.selMode) c.toggleSel(a.id) else c.editing = a.id },
            onLongClick = { if (c.selMode) c.toggleSel(a.id) else c.mulaiPilih(a.id) },
        )) {
        Box(Modifier.width(5.dp).fillMaxHeight().background(A.cat(a.category)))
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (c.selMode) Checkbox(picked, { c.toggleSel(a.id) }, colors = CheckboxDefaults.colors(checkedColor = A.Accent, uncheckedColor = A.Muted))
            val cover = fotos.firstOrNull()
            var besar by remember { mutableStateOf(false) }
            if (cover != null) EncryptedImage(vm.repo.vault, cover.path, Modifier.size(74.dp).clip(RoundedCornerShape(14.dp)).clickable { if (c.selMode) c.toggleSel(a.id) else besar = true }, maxSide = 220)
            if (besar) id.tub.dinas.ui.components.ZoomViewer(vm, fotos.map { it.path }, 0) { besar = false }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                if (a.code.isEmpty()) Text("Tanpa Kode", color = A.Accent, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, fontStyle = FontStyle.Italic)
                else Text(a.code, color = A.Accent, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.Monospace)
                Text(a.name.ifEmpty { "—" }, color = A.Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val ck = A.cat(a.category)
                    ATag(KodeAset.labelKategori(a.category), ck, ck.copy(alpha = 0.18f))
                    if (a.dept.isNotEmpty()) ATag(a.dept, A.Ink, A.Card2)
                    ATag(a.state.label, A.state(a.state), A.state(a.state).copy(alpha = 0.16f))
                    when (a.barcode) {
                        StatusBarcode.TAGGED -> ATag("🏷 Tagged", Color(0xFF065F46), Color(0xFFA7F3D0))
                        StatusBarcode.UNTAGGED -> ATag("🏷 Untagged", Color(0xFF7A5200), Color(0xFFFDE68A))
                        else -> ATag("🏷 Non Aset", Color(0xFF3B3B4F), Color(0xFFE5E7EB))
                    }
                    if (a.tenure == "lease") ATag("Sewa", A.Amber, A.Amber.copy(alpha = 0.16f))
                }
                if (a.owner.isNotEmpty()) Text("🏢 ${a.owner}", color = A.Muted, fontSize = 13.sp, maxLines = 1)
            }
        }
    }
}

@Composable
fun AAssets(vm: MainViewModel, all: List<AsetModel>, shown: List<AsetModel>, fotos: Map<String, List<AsetFotoModel>>, onGallery: () -> Unit, onExport: () -> Unit, onLabels: () -> Unit) {
    val c = vm.aset
    val depts = remember(all) { all.map { it.dept.trim() }.filter { it.isNotEmpty() }.distinct().sorted() }
    val pts = remember(all) { all.map { it.owner.trim() }.filter { it.isNotEmpty() }.distinct().sorted() }
    Column(Modifier.fillMaxSize()) {
        AHeader("Aset", "${shown.size} / ${all.size}", actions = {
            IconBtn("✓", c.selMode) { if (c.selMode) c.selesaiPilih() else c.selMode = true }
            IconBtn("🖼", false, onGallery)
            IconBtn("⬇", false, onExport)
            IconBtn("▦", false, onLabels)
            IconBtn("+", false) { c.baru() }
        }, extra = {
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(c.query, { c.query = it }, Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(20.dp),
                placeholder = { Text("Cari kode, nama, lokasi…") }, leadingIcon = { Text("🔍", fontSize = 16.sp) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = A.Card, unfocusedContainerColor = A.Card, focusedBorderColor = A.Accent, unfocusedBorderColor = A.Line,
                    focusedTextColor = A.Ink, unfocusedTextColor = A.Ink, cursorColor = A.Ink,
                    focusedPlaceholderColor = A.Muted, unfocusedPlaceholderColor = A.Muted))
        })
        if (all.isEmpty()) Text("Belum ada aset. Buka tab Menu → Impor Excel / cadangan, atau ketuk tombol Scan untuk mulai.", color = A.Muted, modifier = Modifier.padding(24.dp))
        var limit by remember(shown.size, c.query, c.kategori, c.barcode, c.auditFilter, c.peringatan, c.deptFilter, c.ownerFilter) { mutableIntStateOf(60) }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 90.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { AChip("Semua  ${all.size}", c.kategori == null, null) { c.kategori = null } }
                    items(KodeAset.KATEGORI.filter { k -> all.any { it.category == k } }) { k ->
                        AChip("${KodeAset.labelKategori(k)}  ${all.count { it.category == k }}", c.kategori == k, A.cat(k)) { c.kategori = if (c.kategori == k) null else k }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    APill("Semua Dept", depts, c.deptFilter, Modifier.weight(1f)) { c.deptFilter = it }
                    APill("Semua PT", pts, c.ownerFilter, Modifier.weight(1f)) { c.ownerFilter = it }
                }
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(StatusBarcode.entries.toList()) { b -> AChip(b.label, c.barcode == b, null) { c.barcode = if (c.barcode == b) null else b } }
                    items(AuditState.entries.toList()) { s -> AChip(s.label, c.auditFilter == s, A.state(s)) { c.auditFilter = if (c.auditFilter == s) null else s } }
                    items(listOf("nofoto" to "Tanpa foto", "nokode" to "Tanpa kode", "dup" to "Kode ganda")) { (k, l) -> AChip(l, c.peringatan == k, null) { c.peringatan = if (c.peringatan == k) null else k } }
                }
            }
            items(shown.take(limit), key = { it.id }) { a -> AAssetCard(vm, a, fotos[a.id].orEmpty()) }
            if (shown.size > limit) item { ADarkButton("Tampilkan lebih banyak (${shown.size - limit})", Modifier.fillMaxWidth()) { limit += 60 } }
            if (shown.isEmpty() && all.isNotEmpty()) item { Text("Tidak ada aset yang cocok dengan filter.", color = A.Muted) }
        }
    }
}

// ---------------- AUDIT ----------------

@Composable
fun AAudit(vm: MainViewModel, list: List<AsetModel>, fotos: Map<String, List<AsetFotoModel>>, onScanAudit: () -> Unit, onReset: () -> Unit) {
    val c = vm.aset
    var sel by rememberSaveable { mutableStateOf(AuditState.PENDING.name) }
    val state = AuditState.from(sel)
    val counts = AuditState.entries.associateWith { s -> list.count { it.state == s } }
    val done = list.size - (counts[AuditState.PENDING] ?: 0)
    val frac = if (list.isEmpty()) 0f else done.toFloat() / list.size
    val rows = list.filter { it.state == state }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 90.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            AHeader("Audit", "$done dari ${list.size} teraudit", actions = {
                ARing(frac, 84.dp, 10.dp, A.Card2, A.Grad) {
                    Text("${Math.round(frac * 100)}%", color = A.Ink, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                }
            })
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                ACard {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AGradButton("Mulai Scan Audit", Modifier.weight(1f), onScanAudit)
                        ADarkButton("Sesi Audit Baru", Modifier.weight(1f), onReset)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(c.auditMode, { c.auditMode = it }, colors = SwitchDefaults.colors(checkedTrackColor = A.Accent))
                        Spacer(Modifier.width(10.dp))
                        Text(if (c.auditMode) "Mode Audit aktif: aset yang di-scan ditandai Ditemukan" else "Mode Audit mati: scan hanya mencocokkan dan menambah foto", color = A.Muted, fontSize = 13.sp)
                    }
                }
            }
        }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(AuditState.entries.toList()) { s -> AChip("${s.label}  ${counts[s]}", s == state, A.state(s)) { sel = s.name } }
            }
        }
        if (rows.isEmpty()) item { Text("Tidak ada aset berstatus ${state.label}.", color = A.Muted, modifier = Modifier.padding(horizontal = 24.dp)) }
        items(rows.take(200), key = { it.id }) { a ->
            Column(Modifier.padding(horizontal = 16.dp)) {
                AAssetCard(vm, a, fotos[a.id].orEmpty())
                Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    AuditState.entries.filter { it != a.state }.forEach { s ->
                        TextButton(onClick = { c.setState(a, s) }) { Text(s.label, color = A.state(s), fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
        if (rows.size > 200) item { Text("Menampilkan 200 dari ${rows.size}. Gunakan filter di halaman Aset.", color = A.Muted, modifier = Modifier.padding(horizontal = 24.dp)) }
    }
}

// ---------------- MENU ----------------

@Composable
private fun MenuRow(glyph: String, c1: Color, c2: Color, title: String, sub: String, onClick: () -> Unit) =
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(54.dp).clip(RoundedCornerShape(16.dp)).background(c1), contentAlignment = Alignment.Center) { Text(glyph, fontSize = 24.sp) }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = A.Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(sub, color = A.Muted, fontSize = 14.sp)
        }
    }

@Composable
fun AMenu(vm: MainViewModel, count: Int, onImport: () -> Unit, onExport: () -> Unit, onMaster: () -> Unit, onGallery: () -> Unit, onLabels: () -> Unit, onBackup: () -> Unit, onRestore: () -> Unit, onClear: () -> Unit, onTemplate: () -> Unit, onSelect: () -> Unit) {
    val c = vm.aset
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 90.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        AHeader("Menu", "Pengaturan modul aset")
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            ACard {
                ATitle("💾", "Data")
                MenuRow("📄", Color(0xFF3B9DD8), Color(0xFF34C3A0), "Unduh Templat Excel", "Format bawaan untuk impor (Master Database + sheet foto)", onTemplate)
                HorizontalDivider(color = A.Line)
                MenuRow("⬆", Color(0xFF34C3A0), Color(0xFF3B9DD8), "Impor Excel / Cadangan", "Muat register Excel (termasuk foto tertanam) atau cadangan AsetScan", onImport)
                HorizontalDivider(color = A.Line)
                MenuRow("⬇", Color(0xFF6B4DF0), Color(0xFFB04FE6), "Ekspor Laporan (Excel / HTML / PDF)", "Pilih cakupan dan jumlah foto per aset", onExport)
                HorizontalDivider(color = A.Line)
                MenuRow("🗄", Color(0xFFF08A5D), Color(0xFFE0508F), "Data Master", "Klasifikasi, departemen, lokasi, kondisi, PT (pilihan di formulir)", onMaster)
                HorizontalDivider(color = A.Line)
                MenuRow("🔎", Color(0xFF3B9DD8), Color(0xFF4C7DF0), "OCR Ulang Semua Foto", "OCR dalam → status Tagged / Untagged / Non Aset") { c.ocrSemua() }
                HorizontalDivider(color = A.Line)
                MenuRow("🔄", Color(0xFF7ACB4E), Color(0xFF34C3A0), "Hitung Ulang Status Barcode", "Dari hasil OCR yang tersimpan") { c.hitungUlang() }
                HorizontalDivider(color = A.Line)
                MenuRow("🖼", Color(0xFFE0508F), Color(0xFF8B5CF6), "Tambah dari Galeri", "Pilih foto dari galeri lalu cocokkan / buat aset", onGallery)
                HorizontalDivider(color = A.Line)
                MenuRow("▦", Color(0xFFF08A5D), Color(0xFFE0508F), "Cetak Label QR", "Stiker label untuk aset terdaftar", onLabels)
                HorizontalDivider(color = A.Line)
                MenuRow("✓", Color(0xFF8B5CF6), Color(0xFF4C7DF0), "Pilih Banyak Aset", "Edit tabel, edit massal, foto massal, status, ekspor, hapus", onSelect)
                HorizontalDivider(color = A.Line)
                MenuRow("🗂", Color(0xFF4C7DF0), Color(0xFF3B9DD8), "Cadangan JSON", "Simpan semua data aset & foto (format AsetScan v2)", onBackup)
                HorizontalDivider(color = A.Line)
                MenuRow("♻", Color(0xFFB04FE6), Color(0xFF6B4DF0), "Impor / Pulihkan JSON", "Periksa dulu perbandingannya, lalu pilih yang diproses", onRestore)
                HorizontalDivider(color = A.Line)
                MenuRow("🗑", Color(0xFFE0506A), Color(0xFFDC2626), "Hapus Database", "Hapus semua aset (opsi cadangan dulu)", onClear)
            }
            ACard {
                ATitle("🔒", "Keamanan & Info")
                Text("Data aset tersimpan terenkripsi (SQLCipher) dan foto dienkripsi AES-GCM. Kunci aplikasi memakai biometrik/PIN perangkat.", color = A.Muted, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                Text("Cadangan seluruh data, termasuk aset dan fotonya, ada di layar Pengaturan aplikasi → Cadangan.", color = A.Muted, fontSize = 14.sp)
            }
            Text("Dokumentasi Dinas · Modul Aset · $count aset", color = A.Muted, fontSize = 13.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

// ---------------- bar aksi massal ----------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ASelectionBar(vm: MainViewModel, shown: List<AsetModel>, onAction: (String) -> Unit) {
    val c = vm.aset
    Column(Modifier.fillMaxWidth().background(A.Card).padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${c.selected.size}", color = A.Accent, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            Text("dipilih", color = A.Ink, fontSize = 16.sp, modifier = Modifier.weight(1f))
            ADarkButton("Pilih semua (${shown.size})") { c.selected = shown.map { it.id }.toSet() }
            ADarkButton("Kosongkan") { c.selected = emptySet() }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { ADarkButton("Batal") { c.selesaiPilih() } }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            @Composable
            fun Tile(k: String, glyph: String, label: String, danger: Boolean = false) =
                Column(Modifier.width(74.dp).clip(RoundedCornerShape(16.dp)).background(A.Card2).clickable { if (c.selected.isNotEmpty()) onAction(k) }.padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(glyph, fontSize = 22.sp)
                    Text(label, fontSize = 11.sp, color = if (danger) A.Red else A.Accent, maxLines = 1, fontWeight = FontWeight.SemiBold)
                }
            Tile("tabel", "📝", "Edit Tabel"); Tile("massal", "✏", "Edit Massal"); Tile("foto", "🖼", "Foto Massal"); Tile("status", "✅", "Status")
            Tile("ekspor", "⬇", "Ekspor"); Tile("label", "▦", "Label QR"); Tile("hapus", "🗑", "Hapus", true)
        }
    }
}
