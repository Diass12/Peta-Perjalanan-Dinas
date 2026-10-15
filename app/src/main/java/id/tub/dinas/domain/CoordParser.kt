package id.tub.dinas.domain

import java.util.Locale

/** Pembaca koordinat yang perilakunya sama dengan prototipe HTML (parseLL). */
object CoordParser {
    private val BANG = Regex("""!3d(-?\d+(?:\.\d+)?)!4d(-?\d+(?:\.\d+)?)""")
    private val GEO = Regex("""geo:(-?\d+(?:\.\d+)?),\s*(-?\d+(?:\.\d+)?)""")
    private val DMS = Regex("""(\d{1,2})\s*°\s*(\d{1,2})\s*['′]\s*(\d{1,2}(?:\.\d+)?)\s*["″]?\s*([NSns])[\s,;]+(\d{1,3})\s*°\s*(\d{1,2})\s*['′]\s*(\d{1,2}(?:\.\d+)?)\s*["″]?\s*([EWew])""")
    private val AT = Regex("""@(-?\d+(?:\.\d+)?),\s*(-?\d+(?:\.\d+)?)""")
    private val QUERY = Regex("""[?&](?:q|ll|query|center|destination)=(-?\d+(?:\.\d+)?),\s*(-?\d+(?:\.\d+)?)""")
    private val PLAIN = Regex("""^(-?\d+(?:\.\d+)?)\s*[,;\s]\s*(-?\d+(?:\.\d+)?)$""")

    /** Mengembalikan null bila teks kosong, tidak dikenali, atau di luar rentang. */
    fun parse(text: String?): GeoPoint? {
        val s = text?.trim().orEmpty()
        if (s.isEmpty()) return null
        DMS.find(s)?.let { d ->
            val g = d.groupValues
            var lat = g[1].toDouble() + g[2].toDouble() / 60 + g[3].toDouble() / 3600
            var lng = g[5].toDouble() + g[6].toDouble() / 60 + g[7].toDouble() / 3600
            if (g[4].uppercase() == "S") lat = -lat
            if (g[8].uppercase() == "W") lng = -lng
            return if (lat in -90.0..90.0 && lng in -180.0..180.0) GeoPoint(lat, lng) else null
        }
        val m = BANG.find(s) ?: GEO.find(s) ?: AT.find(s) ?: QUERY.find(s) ?: PLAIN.find(s) ?: return null
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
