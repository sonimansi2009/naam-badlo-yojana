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
