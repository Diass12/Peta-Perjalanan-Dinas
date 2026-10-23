package id.tub.dinas.security

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import id.tub.dinas.data.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Info pembaruan dari server. [wajib] = versi terpasang di bawah batas minimum, aplikasi dikunci sampai diperbarui. */
class InfoUpdate(val nama: String, val url: String, val wajib: Boolean)

/**
 * Pemeriksaan online (Cloudflare Worker): lisensi yang dicabut dan versi aplikasi terbaru.
 * Tanpa internet aplikasi tetap jalan; lisensi HO hanya ditahan bila tidak terverifikasi > 30 hari.
 */
object CekOnline {
    private const val SERVER = "https://lisensi-dinas.dias-arifmityanto.workers.dev"
    private const val TOLERANSI_MS = 30L * 24 * 3600 * 1000

    var update by mutableStateOf<InfoUpdate?>(null)
    var abaikan by mutableStateOf(false)
    /** Pesan untuk pengguna bila lisensi baru saja dicabut / perlu verifikasi. */
    var pesanLisensi by mutableStateOf("")

    private fun panggil(path: String, body: String?): JSONObject? = try {
        val c = URL(SERVER + path).openConnection() as HttpURLConnection
        c.connectTimeout = 8000; c.readTimeout = 8000
        if (body != null) { c.requestMethod = "POST"; c.doOutput = true; c.setRequestProperty("content-type", "application/json"); c.outputStream.use { it.write(body.toByteArray()) } }
        if (c.responseCode == 200) JSONObject(c.inputStream.bufferedReader().readText()) else null
    } catch (e: Exception) { null }

    suspend fun jalan(ctx: Context, s: Settings) = withContext(Dispatchers.IO) {
        cekLisensi(ctx, s)
        cekVersi(ctx)
    }

    private fun cekLisensi(ctx: Context, s: Settings) {
        val kode = s.licenseCode
        if (kode.isBlank()) return
        val now = System.currentTimeMillis()
        if (s.cekOk == 0L) s.cekOk = now
        val dev = Lisensi.idPerangkat(ctx)
        val body = JSONObject().put("kode", kode).put("dev", dev).toString()
        val r = panggil("/cek", body)
        if (r == null) {
            if (now - s.cekOk > TOLERANSI_MS) { Lisensi.ho = false; Lisensi.keterangan = "perlu online untuk verifikasi"; pesanLisensi = "Hubungkan ke internet sekali untuk memverifikasi lisensi Staff HO." }
            return
        }
        if (r.optBoolean("ok")) { s.cekOk = now; return }
        val alasan = r.optString("alasan")
        if (alasan == "dicabut") { s.licenseCode = ""; Lisensi.muat(ctx, ""); pesanLisensi = "Lisensi Staff HO ini sudah dicabut. Aplikasi kembali ke Staff Site." }
    }

    private fun cekVersi(ctx: Context) {
        val r = panggil("/versi", "{}") ?: return
        val ada = try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).let { if (android.os.Build.VERSION.SDK_INT >= 28) it.longVersionCode.toInt() else @Suppress("DEPRECATION") it.versionCode } } catch (e: Exception) { return }
        val terbaru = r.optInt("kode", 0); val min = r.optInt("min", 0)
        if (terbaru > ada || min > ada) update = InfoUpdate(r.optString("nama", ""), r.optString("url", ""), min > ada)
    }
}
