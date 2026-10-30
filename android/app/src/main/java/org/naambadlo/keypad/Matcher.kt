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

data class Match(val entity: Entity, val start: Int, val end: Int)

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

    /** The name that ends right before the cursor, if any. */
    fun matchAtEnd(text: String): Match? {
        val toks = tokens(text)
        val n = toks.size
        if (n == 0) return null
        for (k in minOf(maxWords, n) downTo 1) {
            val lo = trimLower(text, toks[n - k])
            val hi = trimUpper(text, toks[n - 1])
            if (lo < hi) lookup(text, lo, hi)?.let { return it }
        }
        return null
    }

    /** Every name in the text, left to right, longest match first. */
    fun findAll(text: String): List<Match> {
        val toks = tokens(text)
        val out = ArrayList<Match>()
        var i = 0
        while (i < toks.size) {
            var step = 1
            for (k in minOf(maxWords, toks.size - i) downTo 1) {
                val lo = trimLower(text, toks[i])
                val hi = trimUpper(text, toks[i + k - 1])
                if (lo >= hi) continue
                val m = lookup(text, lo, hi)
                if (m != null) { out.add(m); step = k; break }
            }
            i += step
        }
        return out
    }

    /** Replace every match with a randomly picked alternate. */
    fun replaceAll(text: String, pick: (Entity) -> String = { it.alternates.random() }): String {
        val sb = StringBuilder()
        var last = 0
        for (m in findAll(text)) {
            sb.append(text, last, m.start).append(pick(m.entity))
            last = m.end
        }
        sb.append(text, last, text.length)
        return sb.toString()
    }

    private fun lookup(text: String, lo: Int, hi: Int): Match? {
        val cand = normalize(text.substring(lo, hi))
        map[cand]?.let { return Match(it, lo, hi) }
        for (suf in POSSESSIVES) {
            if (cand.endsWith(suf)) {
                map[cand.dropLast(suf.length)]?.let { return Match(it, lo, hi - suf.length) }
            }
        }
        return null
    }

    companion object {
        private const val PUNCT = ".,!?;:\"'()[]{}#@*_~“”‘’…-|।"
        private val POSSESSIVES = listOf("'s", "’s")

        fun normalize(s: String): String =
            s.lowercase().trim().split(Regex("\\s+")).joinToString(" ")

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
