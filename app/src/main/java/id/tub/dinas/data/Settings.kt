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
    var skyTheme: Boolean
        get() = p.getBoolean("sky", true)
        set(v) { p.edit().putBoolean("sky", v).apply() }
    var lastDinasId: String?
        get() = p.getString("lastDinas", null)
        set(v) { p.edit().putString("lastDinas", v).apply() }
}
