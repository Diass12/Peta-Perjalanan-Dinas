package id.tub.dinas.data

import android.content.Context

enum class PhotoPreset(val label: String, val maxSide: Int, val quality: Int) {
    HEMAT("Hemat", 1280, 70), STANDAR("Standar", 1600, 80), TAJAM("Tajam", 2400, 88);
    companion object { fun from(s: String?) = values().firstOrNull { it.name == s } ?: STANDAR }
}

class Settings(ctx: Context) {
    private val p = ctx.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var lockEnabled: Boolean
        get() = p.getBoolean("lock", true)
        set(v) { p.edit().putBoolean("lock", v).apply() }
    var lockTimeoutSec: Int
        get() = p.getInt("lockTimeout", 60)
        set(v) { p.edit().putInt("lockTimeout", v).apply() }
    var secureAlways: Boolean
        get() = p.getBoolean("secureAlways", false)
        set(v) { p.edit().putBoolean("secureAlways", v).apply() }
    var photoPreset: PhotoPreset
        get() = PhotoPreset.from(p.getString("preset", null))
        set(v) { p.edit().putString("preset", v.name).apply() }
    var satellite: Boolean
        get() = p.getBoolean("satellite", false)
        set(v) { p.edit().putBoolean("satellite", v).apply() }
    var vehicle: String
        get() = p.getString("vehicle", "mobil") ?: "mobil"
        set(v) { p.edit().putString("vehicle", v).apply() }
    var stamp: Boolean
        get() = p.getBoolean("stamp", true)
        set(v) { p.edit().putBoolean("stamp", v).apply() }
    var mapStyle: String
        get() = p.getString("mapStyle", "JALAN") ?: "JALAN"
        set(v) { p.edit().putString("mapStyle", v).apply() }
    var fx: Boolean
        get() = p.getBoolean("fx", true)
        set(v) { p.edit().putBoolean("fx", v).apply() }
    var sfx: Boolean
        get() = p.getBoolean("sfx", true)
        set(v) { p.edit().putBoolean("sfx", v).apply() }
    var playerName: String
        get() = p.getString("playerName", "Dias") ?: "Dias"
        set(v) { p.edit().putString("playerName", v).apply() }
    var avatar: Int
        get() = p.getInt("avatar", 0)
        set(v) { p.edit().putInt("avatar", v).apply() }
    /** Mode tampilan gelap (hitam kebiruan). */
    var dark: Boolean
        get() = p.getBoolean("dark", false)
        set(v) { p.edit().putBoolean("dark", v).apply() }
    /** Kode lisensi (Staff HO). Kosong/tidak sah = Staff Site. */
    var licenseCode: String
        get() = p.getString("licenseCode", "") ?: ""
        set(v) { p.edit().putString("licenseCode", v.trim()).apply() }
    var skyTheme: Boolean
        get() = p.getBoolean("sky", true)
        set(v) { p.edit().putBoolean("sky", v).apply() }
    /** true = akurasi tinggi (GPS penuh, 1 dtk); false = normal (hemat baterai). */
    var gpsHigh: Boolean
        get() = p.getBoolean("gpsHigh", true)
        set(v) { p.edit().putBoolean("gpsHigh", v).apply() }
    var newsExtra: String
        get() = p.getString("newsExtra", "") ?: ""
        set(v) { p.edit().putString("newsExtra", v).apply() }
    var jabatan: String
        get() = p.getString("jabatan", "Auditor") ?: "Auditor"
        set(v) { p.edit().putString("jabatan", v).apply() }
    /** Kode bahasa aplikasi: id, en, zh, ja, ko. */
    var lang: String
        get() = p.getString("lang", "id") ?: "id"
        set(v) { p.edit().putString("lang", v).apply() }
    /** Tautan video pilihan pengguna, satu per baris "judul|url". */
    var videoLinks: String
        get() = p.getString("videoLinks", "") ?: ""
        set(v) { p.edit().putString("videoLinks", v).apply() }
    /** Repositori/tautan yang diisi pengguna sendiri, satu per baris "nama|url". */
    var repoLinks: String
        get() = p.getString("repoLinks", "") ?: ""
        set(v) { p.edit().putString("repoLinks", v).apply() }
    /** Repositori pemutar video yang diisi pengguna sendiri, satu per baris "nama|url". */
    var repoVideo: String
        get() = p.getString("repoVideo", "") ?: ""
        set(v) { p.edit().putString("repoVideo", v).apply() }
    /** Kunci Gemini (opsional) untuk "Analisis AI"; hanya di perangkat ini. */
    var geminiKey: String
        get() = p.getString("geminiKey", "") ?: ""
        set(v) { p.edit().putString("geminiKey", v.trim()).apply() }
    var geminiModel: String
        get() = p.getString("geminiModel", "gemini-flash-latest") ?: "gemini-flash-latest"
        set(v) { p.edit().putString("geminiModel", v.trim().ifBlank { "gemini-flash-latest" }).apply() }
    var lastDinasId: String?
        get() = p.getString("lastDinas", null)
        set(v) { p.edit().putString("lastDinas", v).apply() }
}
