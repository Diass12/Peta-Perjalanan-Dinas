package id.tub.pemberi

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { Surface(Modifier.fillMaxSize()) { Layar(this) } } }
    }

    /** Minta sidik jari/PIN perangkat. [ok] dipanggil di utas utama sesaat setelah berhasil. */
    fun otentikasi(judul: String, ok: () -> Unit, gagal: (String) -> Unit) {
        val cb = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = ok()
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = gagal(errString.toString())
        }
        val b = BiometricPrompt.PromptInfo.Builder().setTitle(judul).setSubtitle("Verifikasi sidik jari atau PIN perangkat")
        if (Build.VERSION.SDK_INT >= 30) b.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
        else @Suppress("DEPRECATION") b.setDeviceCredentialAllowed(true)
        BiometricPrompt(this, ContextCompat.getMainExecutor(this), cb).authenticate(b.build())
    }
}

private val ID_RE = Regex("^[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{4}$")

private fun tanggal(tambahHari: Int): String {
    val c = Calendar.getInstance(); c.add(Calendar.DAY_OF_YEAR, tambahHari)
    return SimpleDateFormat("yyyyMMdd", Locale.US).format(c.time)
}

@Composable
private fun Layar(act: MainActivity) {
    val ctx = LocalContext.current
    var adaKunci by remember { mutableStateOf(KunciPrivat.ada(ctx)) }
    var pesan by remember { mutableStateOf("") }
    val aman = remember { (ctx.getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager).isDeviceSecure }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Pemberi Lisensi", style = MaterialTheme.typography.headlineSmall)
        if (!aman) {
            Text("Aktifkan kunci layar (PIN/pola/sidik jari) dulu. Tanpa itu kunci privat tidak bisa disimpan dengan aman.")
        } else if (!adaKunci) {
            ImporKunci(act, onSelesai = { adaKunci = true; pesan = "Kunci tersimpan aman. Hapus berkas lisensi_private.pem dari HP ini." }, onPesan = { pesan = it })
        } else {
            Terbitkan(act, onPesan = { pesan = it }, onHapus = { KunciPrivat.hapus(ctx); adaKunci = false; pesan = "Kunci dihapus dari HP ini." })
        }
        if (pesan.isNotBlank()) Text(pesan, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun ImporKunci(act: MainActivity, onSelesai: () -> Unit, onPesan: (String) -> Unit) {
    val ctx = LocalContext.current
    var teks by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching { ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.readText().orEmpty() }
            .onSuccess { teks = it }.onFailure { onPesan("Berkas tidak bisa dibaca") }
    }
    Text("Langkah sekali saja: masukkan kunci privat lisensi (lisensi_private.pem). Setelah tersimpan, berkasnya boleh dihapus dari HP; kunci hanya bisa dipakai lewat sidik jari/PIN.")
    OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) { Text("Pilih berkas lisensi_private.pem") }
    OutlinedTextField(teks, { teks = it }, label = { Text("…atau tempel isi berkas") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
    Button(onClick = {
        if (teks.isBlank()) onPesan("Pilih berkas atau tempel isinya dulu")
        else act.otentikasi("Simpan kunci lisensi", ok = {
            val err = KunciPrivat.impor(ctx, teks)
            if (err == null) { teks = ""; onSelesai() } else onPesan(err)
        }, gagal = { onPesan(it) })
    }, modifier = Modifier.fillMaxWidth()) { Text("Simpan kunci dengan aman") }
}

@Composable
private fun Terbitkan(act: MainActivity, onPesan: (String) -> Unit, onHapus: () -> Unit) {
    val ctx = LocalContext.current
    var id by remember { mutableStateOf("") }
    var pindai by remember { mutableStateOf(false) }
    val peran = "HO"
    var masa by remember { mutableStateOf(0) } // hari; 0 = tanpa batas
    var hasil by remember { mutableStateOf<String?>(null) }
    var konfirmHapus by remember { mutableStateOf(false) }

    if (pindai) QrScanDialog("Pindai QR dari aplikasi staff (Pengaturan → QR minta lisensi)", onTutup = { pindai = false }, onHasil = { raw ->
        pindai = false
        val v = raw.removePrefix(QrKit.PREFIX_MINTA).trim().uppercase()
        if (raw.startsWith(QrKit.PREFIX_MINTA) && ID_RE.matches(v)) { id = v; hasil = null; onPesan("") } else onPesan("Itu bukan QR minta lisensi")
    })

    Button(onClick = { pindai = true }, modifier = Modifier.fillMaxWidth()) { Text("📷 Pindai QR perangkat staff") }
    OutlinedTextField(id, { id = it.uppercase().trim(); hasil = null }, label = { Text("ID perangkat (atau ketik manual untuk PC)") }, singleLine = true, modifier = Modifier.fillMaxWidth())

    Text("Masa berlaku")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(0 to "Tanpa batas", 30 to "30 hari", 90 to "90 hari", 365 to "1 tahun").forEach { (h, l) -> FilterChip(masa == h, { masa = h; hasil = null }, label = { Text(l) }) }
    }

    Button(enabled = ID_RE.matches(id), onClick = {
        act.otentikasi("Terbitkan lisensi untuk $id", ok = {
            try {
                val exp = if (masa == 0) "0" else tanggal(masa)
                val kode = KunciPrivat.terbitkan(ctx, "v1|$peran|$id|$exp")
                hasil = kode; onPesan("Lisensi terbit untuk $id")
            } catch (e: Exception) { onPesan("Gagal menandatangani: ${e.message ?: e.javaClass.simpleName}") }
        }, gagal = { onPesan(it) })
    }, modifier = Modifier.fillMaxWidth()) { Text("Terbitkan (sidik jari/PIN)") }

    hasil?.let { kode ->
        val bmp = remember(kode) { QrKit.bitmap(QrKit.PREFIX_LISENSI + kode) }
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Image(bmp.asImageBitmap(), null, Modifier.size(280.dp))
            Text("Di HP staff: Pengaturan → Pindai QR lisensi.", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("lisensi", kode)); onPesan("Kode disalin")
                }) { Text("Salin kode") }
                OutlinedButton(onClick = {
                    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, kode), "Kirim kode"))
                }) { Text("Kirim kode") }
            }
            Text("Untuk PC/exe yang tidak punya kamera: salin atau kirim kode di atas.", style = MaterialTheme.typography.bodySmall)
        }
    }

    HorizontalDivider()
    TextButton(onClick = { konfirmHapus = true }) { Text("Hapus kunci dari HP ini") }
    if (konfirmHapus) AlertDialog(onDismissRequest = { konfirmHapus = false }, title = { Text("Hapus kunci?") },
        text = { Text("Anda perlu mengimpor lisensi_private.pem lagi untuk menerbitkan lisensi.") },
        confirmButton = { TextButton(onClick = { konfirmHapus = false; onHapus() }) { Text("Hapus") } },
        dismissButton = { TextButton(onClick = { konfirmHapus = false }) { Text("Batal") } })
}
