package id.tub.dinas.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val ID = Locale("id", "ID")
fun fmtDate(ms: Long?): String = if (ms == null) "" else SimpleDateFormat("dd MMM yyyy", ID).format(Date(ms))
fun fmtDateTime(ms: Long?): String = if (ms == null) "" else SimpleDateFormat("dd MMM yyyy HH:mm", ID).format(Date(ms))
fun fmtInput(ms: Long?): String = if (ms == null) "" else SimpleDateFormat("dd/MM/yyyy", Locale.US).format(Date(ms))
/** "dd/MM/yyyy" -> epoch ms, atau null bila kosong/tidak valid. */
fun parseInputDate(s: String): Long? {
    if (s.isBlank()) return null
    return runCatching { SimpleDateFormat("dd/MM/yyyy", Locale.US).apply { isLenient = false }.parse(s.trim())?.time }.getOrNull()
}
