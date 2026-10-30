package org.naambadlo.keypad

/**
 * Finds real names (triggers) in text. Pure Kotlin, no Android imports,
 * so it can be unit-tested on any JVM.
 */
data class Entity(
    val id: String,
    val label: String,
    val triggers: List<String>,
    val alternates: List<String>,
)

/** fromMemory = this is a swap the app put in earlier, not the real name. */
data class Match(val entity: Entity, val start: Int, val end: Int, val fromMemory: Boolean = false)

/**
 * Remembers the alternates the app has inserted, so tapping again can
 * re-shuffle them with fresh names. Only words the app itself put in are
 * remembered; typed-in nicknames are never touched.
 */
class AltMemory {
    internal val map = HashMap<String, Entity>()
    internal var maxWords = 1
        private set

    fun add(alt: String, e: Entity) {
        if (map.size > 3000) clear()
        val k = Matcher.normalize(alt)
        if (k.isEmpty()) return
        map[k] = e
        maxWords = maxOf(maxWords, k.split(' ').size)
    }

    fun clear() { map.clear(); maxWords = 1 }
}

class Matcher(entities: List<Entity>) {
    private val map = HashMap<String, Entity>()
    private val maxWords: Int

    init {
        var mw = 1
        for (e in entities) for (t in e.triggers) {
            val k = normalize(t)
            map[k] = e
            mw = maxOf(mw, k.split(' ').size)
        }
        maxWords = mw
    }

    private fun words(mem: AltMemory?) = maxOf(maxWords, mem?.maxWords ?: 1)

    /** The name (or earlier swap) that ends right before the cursor, if any. */
    fun matchAtEnd(text: String, mem: AltMemory? = null): Match? {
        val toks = tokens(text)
        val n = toks.size
        if (n == 0) return null
        for (k in minOf(words(mem), n) downTo 1) {
            val lo = trimLower(text, toks[n - k])
            val hi = trimUpper(text, toks[n - 1])
            if (lo < hi) lookup(text, lo, hi, toks[n - k][0], toks[n - 1][1], mem)?.let { return it }
        }
        return null
    }

    /** Every name (and earlier swap) in the text, left to right, longest first. */
    fun findAll(text: String, mem: AltMemory? = null): List<Match> {
        val toks = tokens(text)
        val out = ArrayList<Match>()
        var i = 0
        while (i < toks.size) {
            var step = 1
            for (k in minOf(words(mem), toks.size - i) downTo 1) {
                val lo = trimLower(text, toks[i])
                val hi = trimUpper(text, toks[i + k - 1])
                if (lo >= hi) continue
                val m = lookup(text, lo, hi, toks[i][0], toks[i + k - 1][1], mem)
                if (m != null) { out.add(m); step = k; break }
            }
            i += step
        }
        return out
    }

    /**
     * Replace every name with a fresh alternate (never the one already there).
     * Picks are remembered in [mem] so the next call can shuffle them again.
     */
    fun replaceAll(
        text: String,
        mem: AltMemory? = null,
        pick: (Entity, String) -> String = ::freshPick,
    ): String {
        val sb = StringBuilder()
        var last = 0
        for (m in findAll(text, mem)) {
            val alt = pick(m.entity, text.substring(m.start, m.end))
            mem?.add(alt, m.entity)
            sb.append(text, last, m.start).append(alt)
            last = m.end
        }
        sb.append(text, last, text.length)
        return sb.toString()
    }

    private fun lookup(text: String, lo: Int, hi: Int, rawLo: Int, rawHi: Int, mem: AltMemory?): Match? {
        val cand = normalize(text.substring(lo, hi))
        map[cand]?.let { return Match(it, lo, hi) }
        if (mem != null) {
            // Code names can start or end with symbols, e.g. (B)(J)(P) or B.J.P.
            // Try the span with its symbols first, then without.
            for (a in rawLo..lo) for (b in hi..rawHi) {
                mem.map[normalize(text.substring(a, b))]?.let { return Match(it, a, b, fromMemory = true) }
            }
        }
        for (suf in POSSESSIVES) {
            if (cand.endsWith(suf)) {
                val base = cand.dropLast(suf.length)
                map[base]?.let { return Match(it, lo, hi - suf.length) }
                mem?.map?.get(base)?.let { return Match(it, lo, hi - suf.length, fromMemory = true) }
            }
        }
        return null
    }

    companion object {
        private const val PUNCT = ".,!?;:\"'()[]{}#@*_~“”‘’…-|।"
        private val POSSESSIVES = listOf("'s", "’s")

        fun normalize(s: String): String =
            s.lowercase().trim().split(Regex("\\s+")).joinToString(" ")

        /** A random alternate that differs from what is there now. */
        fun freshPick(e: Entity, current: String): String {
            val cur = normalize(current)
            val options = e.alternates.filter { normalize(it) != cur }
            return (options.ifEmpty { e.alternates }).random()
        }

        private fun tokens(text: String): List<IntArray> {
            val out = ArrayList<IntArray>()
            var i = 0
            while (i < text.length) {
                if (text[i].isWhitespace()) { i++; continue }
                val s = i
                while (i < text.length && !text[i].isWhitespace()) i++
                out.add(intArrayOf(s, i))
            }
            return out
        }

        private fun trimLower(text: String, t: IntArray): Int {
            var lo = t[0]
            while (lo < t[1] && PUNCT.indexOf(text[lo]) >= 0) lo++
            return lo
        }

        private fun trimUpper(text: String, t: IntArray): Int {
            var hi = t[1]
            while (hi > t[0] && PUNCT.indexOf(text[hi - 1]) >= 0) hi--
            return hi
        }
    }
}
