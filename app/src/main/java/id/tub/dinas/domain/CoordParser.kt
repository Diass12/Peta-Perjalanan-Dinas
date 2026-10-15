package id.tub.dinas.domain

import java.util.Locale

/** Pembaca koordinat yang perilakunya sama dengan prototipe HTML (parseLL). */
object CoordParser {
    private val AT = Regex("""@(-?\d+(?:\.\d+)?),\s*(-?\d+(?:\.\d+)?)""")
    private val QUERY = Regex("""[?&](?:q|ll|query)=(-?\d+(?:\.\d+)?),\s*(-?\d+(?:\.\d+)?)""")
    private val PLAIN = Regex("""^(-?\d+(?:\.\d+)?)\s*[,;\s]\s*(-?\d+(?:\.\d+)?)$""")

    /** Mengembalikan null bila teks kosong, tidak dikenali, atau di luar rentang. */
    fun parse(text: String?): GeoPoint? {
        val s = text?.trim().orEmpty()
        if (s.isEmpty()) return null
        val m = AT.find(s) ?: QUERY.find(s) ?: PLAIN.find(s) ?: return null
        val lat = m.groupValues[1].toDoubleOrNull() ?: return null
        val lng = m.groupValues[2].toDoubleOrNull() ?: return null
        if (lat !in -90.0..90.0 || lng !in -180.0..180.0) return null
        return GeoPoint(lat, lng)
    }

    fun format(g: GeoPoint): String =
        String.format(Locale.US, "%.6f, %.6f", g.lat, g.lng)

    fun mapsUrl(g: GeoPoint): String =
        "https://www.google.com/maps?q=" + String.format(Locale.US, "%.6f,%.6f", g.lat, g.lng)
}
