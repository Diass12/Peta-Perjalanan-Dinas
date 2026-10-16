package id.tub.dinas.aset

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.VibrationEffect
import android.os.Vibrator

/** Bip dan getar pendek untuk hasil scan. */
object Beep {
    fun hasil(ctx: Context, sukses: Boolean, bunyi: Boolean) {
        if (bunyi) runCatching {
            val t = ToneGenerator(AudioManager.STREAM_MUSIC, 70)
            t.startTone(if (sukses) ToneGenerator.TONE_PROP_BEEP else ToneGenerator.TONE_PROP_NACK, 120)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ t.release() }, 400)
        }
        runCatching {
            val v = ctx.getSystemService(Vibrator::class.java)
            v?.vibrate(VibrationEffect.createOneShot(if (sukses) 50L else 120L, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }
}
