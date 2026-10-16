package id.tub.dinas.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import id.tub.dinas.MainViewModel
import id.tub.dinas.aset.MasterField
import id.tub.dinas.domain.*
import id.tub.dinas.ui.components.*
import id.tub.dinas.ui.theme.C

/** Bar aksi massal yang menggantikan bar bawah saat mode pilih aktif. */
@Composable
fun SelectionBar(vm: MainViewModel, all: List<AsetModel>, shown: List<AsetModel>, onAction: (String) -> Unit) {
    val c = vm.aset
    Column(Modifier.fillMaxWidth().background(androidx.compose.ui.graphics.Color(0xF0FFFFFF)).navigationBarsPadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${c.selected.size} dipilih", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), color = C.Ink)
            Chip("Semua terlihat", false) { c.selected = shown.map { it.id }.toSet() }
            Chip("Kosongkan", false) { c.selected = emptySet() }
            Chip("✕ Selesai", true) { c.selesaiPilih() }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(listOf("tabel" to "Edit tabel", "massal" to "Edit massal", "foto" to "Foto massal", "status" to "Status audit", "ekspor" to "Ekspor", "label" to "Label QR", "hapus" to "Hapus")) { (k, l) ->
                Chip(l, k == "hapus", if (k == "hapus") C.Danger else C.Ink) { if (c.selected.isNotEmpty()) onAction(k) }
            }
        }
    }
}

@Composable
fun MasterDialog(vm: MainViewModel, onClose: () -> Unit) {
    val c = vm.aset
    val list by c.register.collectAsState()
    var field by remember { mutableStateOf(MasterField.DEPT) }
    var baru by remember { mutableStateOf("") }
    var rename by remember { mutableStateOf<String?>(null) }
    var renameTo by remember { mutableStateOf("") }
    c.masterVer
    val opts = c.opsi(field, list)
    val pakai = list.groupingBy { field.get(it).trim().lowercase() }.eachCount()
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Data Master") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(MasterField.entries.toList()) { f -> Chip(f.label, f == field, onClick = { field = f }) }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.weight(1f)) { Field(baru, { baru = it }, "Tambah ${field.label.lowercase()}", singleLine = true) }
                    TextButton(enabled = baru.isNotBlank(), onClick = { c.master.add(field, baru); baru = ""; c.masterVer++ }) { Text("Tambah") }
                }
                Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                    if (opts.isEmpty()) Text("Belum ada nilai.", color = C.Muted)
                    opts.forEach { v ->
                        val n = pakai[v.lowercase()] ?: 0
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("$v  ($n)", Modifier.weight(1f))
                            TextButton(onClick = { rename = v; renameTo = v }) { Text("Ubah") }
                            if (n == 0 && v !in field.default) TextButton(onClick = { c.master.remove(field, v); c.masterVer++ }) { Text("Hapus", color = C.Danger) }
                        }
                    }
                }
                Text("Mengubah nama akan ikut mengubah semua aset yang memakainya.", color = C.Muted, style = MaterialTheme.typography.labelSmall)
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Selesai") } },
    )
    rename?.let { old ->
        AlertDialog(
            onDismissRequest = { rename = null },
            title = { Text("Ubah “$old”") },
            text = { Field(renameTo, { renameTo = it }, "Nama baru", singleLine = true) },
            confirmButton = { TextButton(enabled = renameTo.isNotBlank(), onClick = { c.renameMaster(field, old, renameTo); rename = null }) { Text("Simpan") } },
            dismissButton = { TextButton(onClick = { rename = null }) { Text("Batal") } },
        )
    }
}

@Composable
fun BulkEditDialog(vm: MainViewModel, ids: Set<String>, onClose: () -> Unit) {
    val c = vm.aset
    val list by c.register.collectAsState()
    val on = remember { mutableStateMapOf<String, Boolean>() }
    val v = remember { mutableStateMapOf<String, String>() }
    val fields = MasterField.entries.map { it.key to it.label } + listOf("user" to "User / pengguna", "tenure" to "Kepemilikan (own/lease)", "check" to "Tgl cek fisik (yyyy-mm-dd)", "remarks" to "Keterangan")
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Edit massal (${ids.size} aset)") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Centang kolom yang mau diubah. Kolom tak dicentang tidak disentuh.", color = C.Muted, style = MaterialTheme.typography.labelSmall)
                fields.forEach { (k, l) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(on[k] == true, { on[k] = it })
                        Box(Modifier.weight(1f)) {
                            val mf = MasterField.entries.firstOrNull { it.key == k }
                            if (mf != null) MasterPick(l, v[k].orEmpty(), c.opsi(mf, list)) { v[k] = it; on[k] = true }
                            else Field(v[k].orEmpty(), { v[k] = it; on[k] = true }, l, singleLine = true)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = on.any { it.value }, onClick = {
                val ch = on.filter { it.value }.keys
                c.bulkEdit(ids, "edit massal") { a0 ->
                    var a = a0
                    MasterField.entries.filter { it.key in ch }.forEach { f -> v[f.key]?.trim()?.let { x -> a = f.set(a, x); if (x.isNotEmpty()) c.master.add(f, x) } }
                    if ("user" in ch) a = a.copy(user = v["user"].orEmpty().trim())
                    if ("tenure" in ch) a = a.copy(tenure = if (v["tenure"].orEmpty().trim().lowercase().startsWith("l") || v["tenure"].orEmpty().lowercase().contains("sewa")) "lease" else "own")
                    if ("check" in ch) a = a.copy(checkDate = v["check"].orEmpty().trim())
                    if ("remarks" in ch) a = a.copy(remarks = v["remarks"].orEmpty().trim())
                    a
                }
                onClose()
            }) { Text("Terapkan") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Batal") } },
    )
}

/** Isian teks dengan menu pilihan dari Data Master. */
@Composable
fun MasterPick(label: String, value: String, options: List<String>, onChange: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedTextField(value, onChange, Modifier.fillMaxWidth(), label = { Text(label) }, singleLine = true,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
            trailingIcon = { if (options.isNotEmpty()) Text("▾", Modifier.clickable { open = true }.padding(12.dp), color = C.Muted) },
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = C.Ink, unfocusedBorderColor = C.Line, focusedLabelColor = C.Ink, cursorColor = C.Ink))
        DropdownMenu(open, { open = false }) {
            options.forEach { o -> DropdownMenuItem({ Text(o) }, { onChange(o); open = false }) }
        }
    }
}

@Composable
fun BulkStatusDialog(vm: MainViewModel, ids: Set<String>, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Status audit (${ids.size} aset)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AuditState.entries.forEach { s ->
                    TextButton(onClick = {
                        vm.aset.bulkEdit(ids, "audit massal: ${s.label}") { it.copy(state = s, auditedAt = if (s == AuditState.PENDING) null else System.currentTimeMillis()) }
                        onClose()
                    }) { Text(s.label) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onClose) { Text("Batal") } },
    )
}

@Composable
fun BulkFotoDialog(vm: MainViewModel, ids: List<String>, onClose: () -> Unit) {
    var mode by remember { mutableStateOf("urut") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(100)) { uris ->
        if (uris.isNotEmpty()) { vm.aset.bulkFoto(ids, uris, mode); onClose() }
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Foto massal (${ids.size} aset)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Berurutan: foto ke-1 → aset ke-1, dst.", mode == "urut", onClick = { mode = "urut" })
                Chip("Sama: semua foto → semua aset", mode == "sama", onClick = { mode = "sama" })
                Text(if (mode == "urut") "Urutan aset mengikuti urutan di daftar. Pilih foto sebanyak aset." else "Setiap foto yang dipilih ditambahkan ke setiap aset terpilih.", color = C.Muted, style = MaterialTheme.typography.labelSmall)
            }
        },
        confirmButton = { TextButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text("Pilih foto") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Batal") } },
    )
}

/** Edit tabel: baris-baris aset terpilih, satu isian per kolom penting. */
@Composable
fun BulkGridDialog(vm: MainViewModel, ids: List<String>, onClose: () -> Unit) {
    val c = vm.aset
    val list by c.register.collectAsState()
    val rows = remember { mutableStateListOf<AsetModel>().also { r -> r.addAll(list.filter { it.id in ids }) } }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(C.Solid)) {
            Header("Edit tabel (${rows.size})", onBack = onClose, actions = { HeaderAction("Simpan") { c.bulkSimpan(rows.toList()); onClose() } })
            LazyColumn(Modifier.weight(1f).navigationBarsPadding().imePadding(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(rows.size, key = { rows[it].id }) { i ->
                    val a = rows[i]
                    Panel {
                        Field(a.code, { rows[i] = a.copy(code = it.uppercase().filter { ch -> !ch.isWhitespace() }) }, "Kode", singleLine = true)
                        Spacer(Modifier.height(4.dp))
                        Field(a.name, { rows[i] = a.copy(name = it) }, "Nama", singleLine = true)
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.weight(1f)) { Field(a.dept, { rows[i] = a.copy(dept = it) }, "Dept", singleLine = true) }
                            Box(Modifier.weight(1f)) { Field(a.location, { rows[i] = a.copy(location = it) }, "Lokasi", singleLine = true) }
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.weight(1f)) { Field(a.user, { rows[i] = a.copy(user = it) }, "User", singleLine = true) }
                            Box(Modifier.weight(1f)) { Field(a.cond, { rows[i] = a.copy(cond = it) }, "Kondisi", singleLine = true) }
                        }
                    }
                }
            }
        }
    }
}

// ---------------- tab Ringkasan ----------------

@Composable
private fun Ring(fraction: Float, color: Color, label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(84.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val st = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
                val inset = 6.dp.toPx()
                drawArc(Color(0x22000000), 0f, 360f, false, Offset(inset, inset), Size(size.width - 2 * inset, size.height - 2 * inset), style = st)
                drawArc(color, -90f, 360f * fraction.coerceIn(0f, 1f), false, Offset(inset, inset), Size(size.width - 2 * inset, size.height - 2 * inset), style = st)
            }
            Text(value, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = C.Ink)
        }
        Text(label, fontSize = 11.sp, color = C.Muted)
    }
}

private fun rupiah(d: Double): String = when {
    d >= 1e9 -> String.format(java.util.Locale.US, "%.2f M", d / 1e9)
    d >= 1e6 -> String.format(java.util.Locale.US, "%.1f jt", d / 1e6)
    else -> String.format(java.util.Locale.US, "%,.0f", d)
}

@Composable
fun AsetRingkasanTab(vm: MainViewModel, list: List<AsetModel>, fotos: Map<String, List<AsetFotoModel>>, goFilter: (String?, String?, StatusBarcode?, AuditState?) -> Unit) {
    val c = vm.aset
    val n = list.size
    val tagged = list.count { it.barcode == StatusBarcode.TAGGED }
    val diaudit = list.count { it.state != AuditState.PENDING }
    val dup = c.duplikat(list)
    fun frac(a: Int) = if (n == 0) 0f else a.toFloat() / n
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Panel {
                Text("Ringkasan register", style = MaterialTheme.typography.titleMedium)
                Text("$n aset · nilai buku ${rupiah(list.sumOf { it.nbv ?: 0.0 })}", color = C.Muted)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Ring(frac(tagged), Color(0xFF34A853), "Tagged", "$tagged")
                    Ring(frac(diaudit), Color(0xFF2563EB), "Diaudit", "$diaudit/$n")
                    Ring(frac(list.count { fotos[it.id].isNullOrEmpty().not() }), Color(0xFFF59E0B), "Berfoto", "${list.count { fotos[it.id].isNullOrEmpty().not() }}")
                }
            }
        }
        item {
            Panel {
                Text("Per kategori", style = MaterialTheme.typography.titleMedium)
                KodeAset.KATEGORI.forEach { k ->
                    val m = list.count { it.category == k }
                    if (m > 0) Row(Modifier.clickable { goFilter(k, null, null, null) }.padding(vertical = 4.dp)) {
                        Text(KodeAset.labelKategori(k), Modifier.weight(1f)); Text("$m", fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text("Per status barcode", style = MaterialTheme.typography.titleMedium)
                StatusBarcode.entries.forEach { b ->
                    Row(Modifier.clickable { goFilter(null, null, b, null) }.padding(vertical = 4.dp)) { Text(b.label, Modifier.weight(1f)); Text("${list.count { it.barcode == b }}", fontWeight = FontWeight.Bold) }
                }
                Spacer(Modifier.height(6.dp))
                Text("Per status audit", style = MaterialTheme.typography.titleMedium)
                AuditState.entries.forEach { s ->
                    Row(Modifier.clickable { goFilter(null, null, null, s) }.padding(vertical = 4.dp)) { Text(s.label, Modifier.weight(1f)); Text("${list.count { it.state == s }}", fontWeight = FontWeight.Bold) }
                }
            }
        }
        item {
            Panel {
                Text("Kualitas data", style = MaterialTheme.typography.titleMedium)
                Text("Ketuk baris untuk melihat asetnya di Daftar.", color = C.Muted, style = MaterialTheme.typography.labelSmall)
                val rows = listOf(
                    Triple("nokode", "Tanpa kode", list.count { it.code.isEmpty() }),
                    Triple("badcode", "Format kode tidak valid", list.count { it.code.isNotEmpty() && KodeAset.validasi(it.code) == null }),
                    Triple("dup", "Kode ganda", list.count { KodeAset.norm(it.code) in dup }),
                    Triple("nofoto", "Tanpa foto", list.count { fotos[it.id].isNullOrEmpty() }),
                    Triple("noname", "Tanpa nama", list.count { it.name.isBlank() }),
                    Triple("nodept", "Tanpa departemen", list.count { it.dept.isBlank() }),
                    Triple("noloc", "Tanpa lokasi", list.count { it.location.isBlank() }),
                )
                rows.forEach { (k, l, m) ->
                    Row(Modifier.clickable(enabled = m > 0) { goFilter(null, k, null, null) }.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(l, Modifier.weight(1f))
                        Text("$m", fontWeight = FontWeight.Bold, color = if (m == 0) Color(0xFF34A853) else C.Danger)
                    }
                }
            }
        }
    }
}
