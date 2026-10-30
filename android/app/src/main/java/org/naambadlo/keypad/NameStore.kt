package org.naambadlo.keypad

import android.content.Context
import org.json.JSONObject

/** Loads names.json from the app's assets once. Works fully offline. */
object NameStore {
    @Volatile private var cached: Pair<List<Entity>, Matcher>? = null

    /** Swaps the app has put in, shared by the button, keyboard and menu. */
    val memory = AltMemory()

    const val CONGRATS = "Badhai ho! Aapka post ab Meta-friendly hai."
    const val APP_NAME = "Naam Chupao Yojana"

    @Volatile private var appCtx: Context? = null

    fun matcher(ctx: Context): Matcher { appCtx = ctx.applicationContext; return load(ctx).second }

    /**
     * Picks a code name, never the one already there.
     * Starred favourites come first (most of the time), then the chosen tier.
     */
    fun pick(e: Entity, current: String): String {
        val ctx = appCtx
        val cur = Matcher.normalize(current)
        fun fresh(list: Collection<String>) = list.filter { Matcher.normalize(it) != cur }
        if (ctx != null) {
            val favs = fresh(Prefs.favourites(ctx, e.id).filter { it in e.alternates })
            val tier = Prefs.tier(ctx)
            if (favs.isNotEmpty() && tier != Prefs.Tier.STEALTH && Math.random() < 0.7) return favs.random()
            val pool = when (tier) {
                Prefs.Tier.RECOGNISABLE -> e.recognisable
                Prefs.Tier.STEALTH -> e.stealth
                Prefs.Tier.MIX -> e.alternates
            }
            fresh(pool).ifEmpty { fresh(e.alternates) }.ifEmpty { e.alternates }.let { return it.random() }
        }
        return Matcher.freshPick(e, current)
    }

    /** Code names in display order for a chip row: favourites, then the tier, then the rest. */
    fun ordered(ctx: Context, e: Entity): List<String> {
        val favs = Prefs.favourites(ctx, e.id).filter { it in e.alternates }
        val first = when (Prefs.tier(ctx)) {
            Prefs.Tier.RECOGNISABLE -> e.recognisable.shuffled()
            Prefs.Tier.STEALTH -> e.stealth.shuffled()
            Prefs.Tier.MIX -> e.alternates.shuffled()
        }
        return (favs + first + e.alternates).distinct()
    }
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
                val alts = o.getJSONArray("alternates").let { a -> List(a.length()) { a.getString(it) } }
                list.add(
                    Entity(
                        id = o.getString("id"),
                        label = o.getString("label"),
                        triggers = o.getJSONArray("triggers").let { a -> List(a.length()) { a.getString(it) } },
                        alternates = alts,
                        popular = o.optInt("popular", alts.size),
                        ask = o.optJSONArray("ask")?.let { a -> List(a.length()) { a.getString(it) }.toSet() } ?: emptySet(),
                    )
                )
            }
            val pair = list to Matcher(list)
            cached = pair
            return pair
        }
    }
}
