package id.tub.dinas

import android.app.Application
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import id.tub.dinas.domain.*
import id.tub.dinas.media.PhotoPipeline
import id.tub.dinas.ui.DetailTab
import id.tub.dinas.ui.Editor
import id.tub.dinas.ui.Screen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainViewModel(app: Application) : AndroidViewModel(app) {
    val c = (app as DinasApp).container
    val repo = c.repo
    val settings = c.settings
    val tracker = c.tracker

    val stack = mutableStateListOf<Screen>(Screen.Home)
    var tab by mutableStateOf(DetailTab.PETA)
    var editor by mutableStateOf<Editor?>(null)
    var locked by mutableStateOf(settings.lockEnabled)
    var busy by mutableStateOf(false)
    var lockTimeoutSec by mutableStateOf(settings.lockTimeoutSec)

    val dinasList = repo.observeDinas().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init { viewModelScope.launch { runCatching { repo.cleanOrphans() } } }

    val current: Screen get() = stack.last()
    fun push(s: Screen) { stack.add(s) }
    fun pop(): Boolean { if (stack.size > 1) { stack.removeAt(stack.lastIndex); return true }; return false }

    fun toast(msg: String) = Toast.makeText(getApplication(), msg, Toast.LENGTH_LONG).show()

    /** Menjalankan pekerjaan dan menampilkan galat dengan pesan yang jelas (tanpa data sensitif). */
    fun work(onDone: (() -> Unit)? = null, block: suspend () -> Unit) {
        viewModelScope.launch {
            busy = true
            try { block(); onDone?.invoke() }
            catch (e: IllegalArgumentException) { toast(e.message ?: "Data tidak valid") }
            catch (e: Exception) { toast("Gagal: ${e.message ?: e.javaClass.simpleName}") }
            finally { busy = false }
        }
    }

    // ---------- foto ----------
    fun addPhotos(dinasId: String, type: OwnerType, ownerId: String, uris: List<Uri>, onExif: (GeoPoint) -> Unit = {}) = work {
        var ok = 0
        for (u in uris) {
            try {
                val p = withContext(Dispatchers.IO) { PhotoPipeline.process(getApplication(), u, settings.photoPreset) }
                repo.addFoto(dinasId, type, ownerId, p.jpeg, p.width, p.height, p.takenAt)
                p.geo?.let(onExif)
                ok++
            } catch (e: Exception) { toast("Foto dilewati: ${e.message ?: "tidak dapat dibaca"}") }
        }
        if (ok > 0) toast("$ok foto disimpan terenkripsi")
    }

    fun addCameraPhoto(dinasId: String, type: OwnerType, ownerId: String, file: File, onExif: (GeoPoint) -> Unit = {}) = work {
        try {
            val p = withContext(Dispatchers.IO) { PhotoPipeline.process(getApplication(), Uri.fromFile(file), settings.photoPreset) }
            repo.addFoto(dinasId, type, ownerId, p.jpeg, p.width, p.height, p.takenAt ?: System.currentTimeMillis())
        } finally { file.delete() }
    }

    fun addPdf(dinasId: String, temuanId: String, uri: Uri) = work {
        val cr = getApplication<Application>().contentResolver
        val name = cr.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null } ?: "lampiran.pdf"
        val size = cr.query(uri, arrayOf(android.provider.OpenableColumns.SIZE), null, null, null)?.use { if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else -1L } ?: -1L
        if (size > id.tub.dinas.data.Repository.WARN_PDF && size <= id.tub.dinas.data.Repository.MAX_PDF) toast("PDF besar (${size / 1024 / 1024} MB); aplikasi mungkin lambat")
        val ins = cr.openInputStream(uri) ?: throw IllegalArgumentException("PDF tidak dapat dibuka")
        ins.use { repo.addLampiran(dinasId, temuanId, name, it) }
        toast("PDF dilampirkan")
    }

    // ---------- Excel & cadangan ----------
    fun exportExcel(dinasId: String, uri: Uri) = work {
        val os = getApplication<Application>().contentResolver.openOutputStream(uri, "w") ?: throw IllegalArgumentException("Tidak dapat menulis berkas")
        os.use { repo.exportExcel(dinasId, it) }
        toast("Excel tersimpan")
    }

    fun exportBackup(uri: Uri, pass: String) = work {
        val os = getApplication<Application>().contentResolver.openOutputStream(uri, "w") ?: throw IllegalArgumentException("Tidak dapat menulis berkas")
        os.use { repo.exportBackup(it, pass.toCharArray().takeIf { p -> p.isNotEmpty() }) }
        toast(if (pass.isEmpty()) "Cadangan tersimpan (TANPA kata sandi)" else "Cadangan terenkripsi tersimpan")
    }

    fun restoreBackup(uri: Uri, pass: String) = work {
        val ins = getApplication<Application>().contentResolver.openInputStream(uri) ?: throw IllegalArgumentException("Tidak dapat membuka berkas")
        val n = ins.use { repo.restoreBackup(it, pass.toCharArray().takeIf { p -> p.isNotEmpty() }) }
        toast("$n dinas dipulihkan")
    }

    override fun onCleared() { tracker.stop() }
}
