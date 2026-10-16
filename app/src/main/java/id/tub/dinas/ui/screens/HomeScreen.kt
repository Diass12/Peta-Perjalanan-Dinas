package id.tub.dinas.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import id.tub.dinas.MainViewModel
import id.tub.dinas.domain.DinasModel
import id.tub.dinas.ui.*
import id.tub.dinas.ui.components.*
import id.tub.dinas.ui.theme.C
import id.tub.dinas.ui.theme.ThemeState

@Composable
fun DinasDialog(existing: DinasModel?, onDismiss: () -> Unit, onSave: (String, Long?, Long?, String) -> Unit) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var s by remember { mutableStateOf(msToIso(existing?.startMs)) }
    var e by remember { mutableStateOf(msToIso(existing?.endMs)) }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    val sOk = true
    val eOk = e.isBlank() || s.isBlank() || e >= s
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Dinas baru" else "Ubah dinas") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(name, { name = it }, "Nama dinas", singleLine = true)
                DateField("Mulai", s, { s = it })
                DateField("Selesai", e, { e = it })
                if (!eOk) Text("Tanggal selesai tidak boleh sebelum tanggal mulai", color = C.Danger)
                Field(note, { note = it }, "Catatan", minLines = 2)
            }
        },
        confirmButton = { TextButton(enabled = sOk && eOk && name.isNotBlank(), onClick = { onSave(name, isoToMs(s), isoToMs(e), note); onDismiss() }) { Text("Simpan") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}
