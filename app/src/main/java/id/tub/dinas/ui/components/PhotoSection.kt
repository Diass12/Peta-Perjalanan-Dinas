package id.tub.dinas.ui.components

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import id.tub.dinas.MainViewModel
import id.tub.dinas.domain.FotoModel
import id.tub.dinas.domain.GeoPoint
import id.tub.dinas.domain.OwnerType
import id.tub.dinas.media.EncryptedImage
import id.tub.dinas.ui.theme.C
import java.io.File
import java.util.UUID

/** Tambah foto (kamera / galeri), beri keterangan, urutkan, hapus. Semua foto langsung tersimpan terenkripsi. */
@Composable
fun PhotoSection(
    vm: MainViewModel, dinasId: String, type: OwnerType, ownerId: String, photos: List<FotoModel>,
    onOpen: (Int) -> Unit, onExif: (GeoPoint) -> Unit,
) {
    val ctx = LocalContext.current
    var camPath by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteId by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(20)) { uris ->
        if (uris.isNotEmpty()) vm.addPhotos(dinasId, type, ownerId, uris, onExif)
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val p = camPath; camPath = null
        if (p != null) { val f = File(p); if (ok) vm.addCameraPhoto(dinasId, type, ownerId, f, onExif) else f.delete() }
    }
    fun launchCamera() {
        val dir = File(ctx.cacheDir, "kamera").apply { mkdirs() }
        val f = File(dir, "cam_${UUID.randomUUID()}.jpg")
        camPath = f.absolutePath
        camera.launch(FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f))
    }
    val camPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) launchCamera() else vm.toast("Izin kamera ditolak. Pakai Galeri untuk memilih foto.")
    }

    Panel {
        Text("FOTO (${photos.size})", style = MaterialTheme.typography.labelSmall, color = C.Muted)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconTextButton(id.tub.dinas.R.drawable.ic_kamera, "Kamera") {
                if (androidx.core.content.ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) launchCamera()
                else camPerm.launch(Manifest.permission.CAMERA)
            }
            IconTextButton(id.tub.dinas.R.drawable.ic_galeri, "Galeri", primary = false) { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
        }
        photos.forEachIndexed { i, f ->
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                EncryptedImage(vm.repo.vault, f.path, Modifier.size(84.dp).clip(RoundedCornerShape(10.dp)).clickable { onOpen(i) }, maxSide = 300)
                Column(Modifier.weight(1f)) {
                    var cap by remember(f.id, f.caption) { mutableStateOf(f.caption) }
                    OutlinedTextField(
                        cap, { cap = it },
                        Modifier.fillMaxWidth().onFocusChanged { st -> if (!st.isFocused && cap != f.caption) vm.work { vm.repo.setCaption(f.id, cap) } },
                        label = { Text("Keterangan foto") }, singleLine = true,
                    )
                    Row {
                        TextButton(enabled = i > 0, onClick = { move(vm, ownerId, photos, i, i - 1) }) { Text("↑") }
                        TextButton(enabled = i < photos.lastIndex, onClick = { move(vm, ownerId, photos, i, i + 1) }) { Text("↓") }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { deleteId = f.id }) { Text("Hapus", color = C.Danger) }
                    }
                }
            }
        }
    }
    deleteId?.let { id -> Confirm("Hapus foto?", "Foto dihapus permanen dari aplikasi.", onConfirm = { vm.work { vm.repo.deleteFoto(id) } }, onDismiss = { deleteId = null }) }
}

private fun move(vm: MainViewModel, ownerId: String, photos: List<FotoModel>, from: Int, to: Int) {
    val ids = photos.map { it.id }.toMutableList()
    val x = ids.removeAt(from); ids.add(to, x)
    vm.work { vm.repo.reorderFoto(ownerId, ids) }
}
