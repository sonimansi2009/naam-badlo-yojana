package org.naambadlo.keypad

import android.content.Context
import org.json.JSONObject

/** Loads names.json from the app's assets once. Works fully offline. */
object NameStore {
    @Volatile private var cached: Pair<List<Entity>, Matcher>? = null

    /** Swaps the app has put in, shared by the button, keyboard and menu. */
    val memory = AltMemory()

    const val CONGRATS = "Badhai ho! Aapka post ab Meta-proof hai."
    const val APP_NAME = "Shadowban Suraksha Yojana"

    @Volatile private var appCtx: Context? = null

    fun matcher(ctx: Context): Matcher { appCtx = ctx.applicationContext; return load(ctx).second }

    /**
     * Picks a code name, never the one already there.
     * Starred favourites come first (most of the time), then the chosen tier.
     */
    fun pick(e: Entity, current: String): String = maybeDisguise(pickPlain(e, current))

    /** Same as [pick], without the symbol layer. */
    fun pickPlain(e: Entity, current: String): String {
        val ctx = appCtx
        val cur = Decoder.squash(current)
        fun fresh(list: Collection<String>) = list.filter { Decoder.squash(it) != cur }
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

    /** Applies the symbol layer if it is on in Settings. */
    fun maybeDisguise(alt: String): String {
        val ctx = appCtx ?: return alt
        return if (Prefs.symbols(ctx)) disguise(alt) else alt
    }

    private val LOOKALIKE = mapOf('a' to '@', 'o' to '0', 'i' to '1', 'e' to '3', 's' to '$')

    /**
     * Symbol layer: swaps 1-3 letters (never more than 3) for look-alikes, a->@ o->0 i->1 e->3 s->$.
     * Never the first or last letter of a word, so it stays readable: "Kamal Party" -> "K@mal P@rty".
     */
    fun disguise(alt: String): String {
        val letters = alt.count { it in 'a'..'z' || it in 'A'..'Z' }
        if (letters < 3) return alt
        val ok = ArrayList<Int>()
        for (i in alt.indices) {
            val c = alt[i]
            if (c.lowercaseChar() !in LOOKALIKE) continue
            val first = i == 0 || !alt[i - 1].isLetter()
            val last = i == alt.length - 1 || !alt[i + 1].isLetter()
            if (!first && !last) ok.add(i)
        }
        if (ok.isEmpty()) return alt
        val want = when { letters <= 4 -> 1; letters <= 9 -> 2; else -> 3 }
        val chars = alt.toCharArray()
        for (i in ok.shuffled().take(minOf(want, ok.size))) chars[i] = LOOKALIKE.getValue(chars[i].lowercaseChar())
        return String(chars)
    }

    /**
     * Hides a real name on screen: about 20% of the letters become *, never the first letter
     * of a word. "Narendra Modi" -> "Nar*nd*a Mo*i". Same name, same mask, every time.
     */
    fun mask(name: String): String {
        val out = StringBuilder()
        for (word in name.split(' ')) {
            if (out.isNotEmpty()) out.append(' ')
            val idx = word.indices.filter { it > 0 && word[it].isLetter() }
            val letters = word.count { it.isLetter() }
            val n = if (letters < 3) 0 else maxOf(1, Math.round(letters * 0.2).toInt())
            val hide = HashSet<Int>()
            if (n > 0 && idx.isNotEmpty()) for (j in 0 until n) hide.add(idx[minOf(idx.size - 1, ((j + 0.5) * idx.size / n).toInt())])
            word.forEachIndexed { i, c -> out.append(if (i in hide) '*' else c) }
        }
        return out.toString()
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
