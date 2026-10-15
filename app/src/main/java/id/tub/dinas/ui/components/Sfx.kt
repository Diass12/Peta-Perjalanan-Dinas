package id.tub.dinas.ui.components

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import id.tub.dinas.R

/** Efek suara pendek ala game. Gagal diam-diam bila perangkat tidak mendukung. */
object Sfx {
    var enabled = true
    private var pool: SoundPool? = null
    private var click = 0; private var menu = 0; private var pick = 0

    fun init(ctx: Context) {
        if (pool != null) return
        try {
            val p = SoundPool.Builder().setMaxStreams(3)
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()
            click = p.load(ctx, R.raw.sfx_click, 1); menu = p.load(ctx, R.raw.sfx_menu, 1); pick = p.load(ctx, R.raw.sfx_pick, 1)
            pool = p
        } catch (_: Exception) { pool = null }
    }
    private fun play(id: Int) { if (enabled && id != 0) try { pool?.play(id, 0.6f, 0.6f, 1, 0, 1f) } catch (_: Exception) {} }
    fun click() = play(click)
    fun menu() = play(menu)
    fun pick() = play(pick)
}
