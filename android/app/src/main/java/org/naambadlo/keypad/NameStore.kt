package org.naambadlo.keypad

import android.content.Context
import org.json.JSONObject

/** Loads names.json from the app's assets once. Works fully offline. */
object NameStore {
    @Volatile private var cached: Pair<List<Entity>, Matcher>? = null

    /** Swaps the app has put in, shared by the button, keyboard and menu. */
    val memory = AltMemory()

    const val CONGRATS = "Congratulations. Your text is encrypted against Meta's violation of Freedom of speech"
    const val APP_NAME = "Naam Chupao Yojana"

    fun matcher(ctx: Context): Matcher = load(ctx).second
    fun entities(ctx: Context): List<Entity> = load(ctx).first

    private fun load(ctx: Context): Pair<List<Entity>, Matcher> {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val text = ctx.assets.open("names.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
            val arr = JSONObject(text).getJSONArray("entities")
            val list = ArrayList<Entity>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    Entity(
                        id = o.getString("id"),
                        label = o.getString("label"),
                        triggers = o.getJSONArray("triggers").let { a -> List(a.length()) { a.getString(it) } },
                        alternates = o.getJSONArray("alternates").let { a -> List(a.length()) { a.getString(it) } },
                    )
                )
            }
            val pair = list to Matcher(list)
            cached = pair
            return pair
        }
    }
}
