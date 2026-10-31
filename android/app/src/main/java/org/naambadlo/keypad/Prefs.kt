package org.naambadlo.keypad

import android.content.ComponentName
import android.content.Context
import android.provider.Settings

/** Small settings store (on the phone only). */
object Prefs {
    private const val FILE = "naam_badlo"

    /** Apps where the floating button shows when "Meta apps only" is on. */
    val META_APPS = setOf(
        "com.instagram.android",      // Instagram
        "com.instagram.barcelona",    // Threads
        "com.facebook.katana",        // Facebook
        "com.facebook.lite",          // Facebook Lite
        "com.facebook.orca",          // Messenger
        "com.whatsapp",               // WhatsApp
        "com.whatsapp.w4b",           // WhatsApp Business
    )

    private fun sp(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun metaOnly(ctx: Context) = sp(ctx).getBoolean("meta_only", false)
    fun setMetaOnly(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("meta_only", v).apply()

    /** Soft chime when the floating button finds a name. */
    fun sound(ctx: Context) = sp(ctx).getBoolean("sound", true)
    fun setSound(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("sound", v).apply()

    /** Which code names to use. */
    enum class Tier { RECOGNISABLE, MIX, STEALTH }
    fun tier(ctx: Context): Tier =
        runCatching { Tier.valueOf(sp(ctx).getString("tier", Tier.RECOGNISABLE.name)!!) }.getOrDefault(Tier.RECOGNISABLE)
    fun setTier(ctx: Context, t: Tier) = sp(ctx).edit().putString("tier", t.name).apply()

    /** Starred code names, per politician. */
    fun favourites(ctx: Context, id: String): Set<String> = sp(ctx).getStringSet("fav_$id", emptySet()) ?: emptySet()
    fun toggleFavourite(ctx: Context, id: String, alt: String): Boolean {
        val now = favourites(ctx, id).toMutableSet()
        val added = if (alt in now) { now.remove(alt); false } else { now.add(alt); true }
        sp(ctx).edit().putStringSet("fav_$id", now).apply()
        return added
    }

    /** Reach test log, stored as one line per entry: date|platform|plain|swapped */
    fun reachLog(ctx: Context): List<String> =
        (sp(ctx).getString("reach_log", "") ?: "").split('\n').filter { it.isNotBlank() }
    fun addReach(ctx: Context, line: String) =
        sp(ctx).edit().putString("reach_log", (reachLog(ctx) + line).joinToString("\n")).apply()
    fun clearReach(ctx: Context) = sp(ctx).edit().remove("reach_log").apply()

    fun buttonPos(ctx: Context): Pair<Int, Int> =
        sp(ctx).getInt("btn_x", -1) to sp(ctx).getInt("btn_y", -1)
    fun setButtonPos(ctx: Context, x: Int, y: Int) =
        sp(ctx).edit().putInt("btn_x", x).putInt("btn_y", y).apply()

    fun floatingEnabled(ctx: Context): Boolean {
        val me = ComponentName(ctx, FloatingButtonService::class.java).flattenToString()
        val list = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        return list.split(':').any { it.equals(me, ignoreCase = true) }
    }
}
