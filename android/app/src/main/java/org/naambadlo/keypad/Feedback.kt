package org.naambadlo.keypad

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator

/**
 * Small touches of sound and feel. Sounds only play with "Sound" on in Settings
 * and the phone's ringer on normal (never in silent or vibrate mode).
 *  - found:     soft two-note chime when a name is spotted
 *  - encrypted: just the buzz after "Make it Meta friendly" (no sound)
 *  - welcome:   "S S Y" in Morse code when the tutorial opens
 *  - swapped:   just the buzz (Decode, Undo-style moments)
 */
object Feedback {
    private var pool: SoundPool? = null
    private val ids = HashMap<Int, Int>()          // raw res -> sound id
    private val ready = HashSet<Int>()             // sound ids that finished loading
    private val pending = HashMap<Int, Float>()    // sound id -> volume, to play once loaded
    private var lastChime = 0L

    /** Subtle tick when names are swapped. */
    fun swapped(ctx: Context) {
        val v = ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        if (!v.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= 26) {
            v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 14, 60, 22), intArrayOf(0, 90, 0, 140), -1))
        } else {
            @Suppress("DEPRECATION") v.vibrate(longArrayOf(0, 14, 60, 22), -1)
        }
    }

    /** After "Make it Meta friendly": vibration only. */
    fun encrypted(ctx: Context) = swapped(ctx)

    /** Morse "S S Y" when the tutorial or What's new opens. */
    fun welcome(ctx: Context) = play(ctx, R.raw.morse, 0.4f)

    /** Soft chime when the floating button pops up; never chatters while typing. */
    fun found(ctx: Context) {
        val now = SystemClock.uptimeMillis()
        if (now - lastChime < 4000) return
        lastChime = now
        play(ctx, R.raw.detect, 0.35f)
    }

    private fun play(ctx: Context, res: Int, vol: Float) {
        if (!Prefs.sound(ctx)) return
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        if (am.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        val p = pool ?: SoundPool.Builder().setMaxStreams(2).setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        ).build().also { sp ->
            pool = sp
            sp.setOnLoadCompleteListener { s, id, status ->
                if (status != 0) return@setOnLoadCompleteListener
                ready.add(id)
                pending.remove(id)?.let { v -> s.play(id, v, v, 1, 0, 1f) }
            }
        }
        val id = ids.getOrPut(res) { p.load(ctx.applicationContext, res, 1) }
        if (id in ready) p.play(id, vol, vol, 1, 0, 1f) else pending[id] = vol
    }
}
