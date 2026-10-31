package org.naambadlo.keypad

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator

/** A short vibration when names are swapped, and a soft chime when names are found. */
object Feedback {
    private var pool: SoundPool? = null
    private var chime = 0
    private var loaded = false
    private var lastChime = 0L

    /** Subtle tick after "Make it Meta friendly" swaps the words. */
    fun swapped(ctx: Context) {
        val v = ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        if (!v.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= 26) {
            v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 14, 60, 22), intArrayOf(0, 90, 0, 140), -1))
        } else {
            @Suppress("DEPRECATION") v.vibrate(longArrayOf(0, 14, 60, 22), -1)
        }
    }

    /** Soft two-note chime when the floating button pops up. Quiet, never in silent/vibrate mode. */
    fun found(ctx: Context) {
        if (!Prefs.sound(ctx)) return
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        if (am.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        val now = SystemClock.uptimeMillis()
        if (now - lastChime < 4000) return            // never chatter while typing
        lastChime = now
        val p = pool ?: SoundPool.Builder().setMaxStreams(1).setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        ).build().also { sp ->
            pool = sp
            sp.setOnLoadCompleteListener { s, id, status ->
                if (status == 0) { loaded = true; s.play(id, 0.35f, 0.35f, 1, 0, 1f) }
            }
            chime = sp.load(ctx.applicationContext, R.raw.detect, 1)
            return
        }
        if (loaded) p.play(chime, 0.35f, 0.35f, 1, 0, 1f)
    }
}
