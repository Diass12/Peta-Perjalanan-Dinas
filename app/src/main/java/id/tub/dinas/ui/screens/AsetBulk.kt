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
    val fields = MasterField.entries.map { it.key to it.label } + listOf("user" to "User / pengguna", "tenure" to "Kepemilikan (own/lease)", "check" to "Tgl cek fisik", "remarks" to "Keterangan")
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
                            else if (k == "check") DateField(l, v[k].orEmpty(), { v[k] = it; on[k] = true })
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

