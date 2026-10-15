package id.tub.dinas.route

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * Rute jalan lewat layanan OSRM publik (data OpenStreetMap). Butuh internet; hasilnya disimpan
 * di memori supaya titik yang sama tidak diminta berulang. Gagal = null (pemanggil pakai garis lurus).
 */
object RoadRouter {
    private val cache = HashMap<String, List<Pair<Double, Double>>>()

    suspend fun route(pts: List<Pair<Double, Double>>): List<Pair<Double, Double>>? {
        if (pts.size < 2 || pts.size > 90) return null
        val coords = pts.joinToString(";") { String.format(Locale.US, "%.6f,%.6f", it.second, it.first) }
        cache[coords]?.let { return it }
        return withContext(Dispatchers.IO) {
            try {
                val c = URL("https://router.project-osrm.org/route/v1/driving/$coords?overview=full&geometries=geojson").openConnection() as HttpURLConnection
                c.connectTimeout = 10_000; c.readTimeout = 15_000
                c.setRequestProperty("User-Agent", "DokumentasiDinas/1.0")
                if (c.responseCode != 200) return@withContext null
                val body = c.inputStream.bufferedReader().use { it.readText() }
                val arr = JSONObject(body).getJSONArray("routes").getJSONObject(0).getJSONObject("geometry").getJSONArray("coordinates")
                val out = ArrayList<Pair<Double, Double>>(arr.length())
                for (i in 0 until arr.length()) { val p = arr.getJSONArray(i); out.add(p.getDouble(1) to p.getDouble(0)) }
                if (out.size >= 2) { cache[coords] = out; out } else null
            } catch (_: Exception) { null }
        }
    }
}
