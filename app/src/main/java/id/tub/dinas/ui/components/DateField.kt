package id.tub.dinas.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import id.tub.dinas.ui.theme.C
import id.tub.dinas.um.isoToInput
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Date
import java.util.Locale

/** Tanggal lokal (epoch ms, tengah malam lokal) <-> yyyy-MM-dd. */
fun msToIso(ms: Long?): String = if (ms == null) "" else SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(ms))
fun isoToMs(iso: String): Long? = if (iso.isBlank()) null else runCatching { SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }.parse(iso)?.time }.getOrNull()

private fun isoToUtcMs(iso: String): Long? = runCatching { LocalDate.parse(iso).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrNull()
private fun utcMsToIso(ms: Long): String = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate().toString()

@Composable
private fun PickerBox(label: String, shown: String, clearable: Boolean, icon: String, onOpen: () -> Unit, onClear: () -> Unit, modifier: Modifier) {
    Box(modifier) {
        OutlinedTextField(
            value = shown, onValueChange = {}, readOnly = true, singleLine = true, modifier = Modifier.fillMaxWidth(),
            label = { Text(label) }, shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = C.Ink, unfocusedBorderColor = C.Line, focusedLabelColor = C.Ink, cursorColor = C.Ink),
            trailingIcon = {
                if (clearable && shown.isNotEmpty()) Text("✕", color = C.Muted, modifier = Modifier.clickable(onClick = onClear).padding(12.dp))
                else Text(icon, modifier = Modifier.clickable(onClick = onOpen).padding(12.dp))
            },
        )
        // Seluruh kolom (kecuali tombol di kanan) membuka pemilih.
        Box(Modifier.matchParentSize().padding(end = 52.dp).clickable(onClick = onOpen))
    }
}

/** Kolom tanggal dengan kotak kalender. [iso] = yyyy-MM-dd; "" bila kosong. [display] menimpa teks yang ditampilkan. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(label: String, iso: String, onIso: (String) -> Unit, modifier: Modifier = Modifier, clearable: Boolean = true, display: String? = null) {
    var open by remember { mutableStateOf(false) }
    PickerBox(label, display ?: isoToInput(iso), clearable, "📅", { open = true }, { onIso("") }, modifier)
    if (open) {
        val today = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val st = rememberDatePickerState(initialSelectedDateMillis = isoToUtcMs(iso) ?: today)
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = { TextButton(onClick = { st.selectedDateMillis?.let { onIso(utcMsToIso(it)) }; open = false }) { Text("Pilih") } },
            dismissButton = {
                Row {
                    TextButton(onClick = { onIso(utcMsToIso(today)); open = false }) { Text("Hari ini") }
                    TextButton(onClick = { open = false }) { Text("Batal") }
                }
            },
        ) { DatePicker(state = st) }
    }
}

/** Kolom jam (HH:mm) dengan dialog jam. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    PickerBox(label, value, true, "🕒", { open = true }, { onChange("") }, modifier)
    if (open) {
        val p = value.split(":").mapNotNull { it.trim().toIntOrNull() }
        val st = rememberTimePickerState(initialHour = p.getOrNull(0) ?: 9, initialMinute = p.getOrNull(1) ?: 0, is24Hour = true)
        Dialog(onDismissRequest = { open = false }) {
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    TimePicker(state = st)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { open = false }) { Text("Batal") }
                        TextButton(onClick = { onChange("%02d:%02d".format(st.hour, st.minute)); open = false }) { Text("Pilih") }
                    }
                }
            }
        }
    }
}
