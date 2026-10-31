package org.naambadlo.keypad

/**
 * The other direction: finds code names in a message and puts the real names back.
 *
 * 1. Exact: any of the 50 code names (or a real name) as written.
 * 2. Guess: misspellings and look-alikes, e.g. "Odiijii", "Kamal Partyy", "M0di",
 *    "M.o.d.i", "Mota bhaai", "Amit Saah". These are marked as guesses so the reader
 *    can tap to undo them.
 *
 * Pure Kotlin, unit-tested with the matcher.
 */
class Decoder(entities: List<Entity>) {
    data class Hit(val start: Int, val end: Int, val code: String, val real: String, val guess: Boolean = false)

    // Each code name becomes a "trigger" that maps back to the real name.
    private val matcher = Matcher(entities.map { e -> Entity(e.id, e.label, e.alternates, listOf(realName(e))) })

    /** squashed spelling -> real name (null when two people share it: never guess then) */
    private val keys = HashMap<String, String?>()
    /** real names exactly as people write them ("Modi", "Modiji"): already decoded, leave them */
    private val realWords = HashSet<String>()
    private val triggerKeys = HashSet<String>()
    /** first letter -> keys, for quick near-miss lookups */
    private val byFirst = HashMap<Char, MutableList<String>>()

    init {
        for (e in entities) {
            val real = realName(e)
            for (t in e.triggers) { realWords.add(Matcher.normalize(t)); triggerKeys.add(squash(t)) }
            for (w in e.alternates + e.triggers) {
                val k = squash(w)
                if (k.length < MIN_KEY) continue
                if (keys.containsKey(k) && keys[k] != real) keys[k] = null else keys[k] = real
            }
        }
        for (k in keys.keys) byFirst.getOrPut(k[0]) { ArrayList() }.add(k)
    }

    fun find(text: String): List<Hit> {
        val exact = matcher.findAll(text).map {
            Hit(it.start, it.end, text.substring(it.start, it.end), it.entity.alternates[0])
        }.filter { !samePerson(it) }
        val toks = Regex("\\S+").findAll(text).map { it.range.first to it.range.last + 1 }.toList()
        val out = ArrayList<Hit>()
        var ei = 0
        var i = 0
        while (i < toks.size) {
            val (ts, _) = toks[i]
            while (ei < exact.size && exact[ei].end <= ts) ei++
            if (ei < exact.size && exact[ei].start <= ts) {           // inside an exact hit
                if (out.lastOrNull() !== exact[ei]) out.add(exact[ei])
                i++; continue
            }
            val limit = if (ei < exact.size) exact[ei].start else Int.MAX_VALUE
            // Try 1..4 words; keep the closest match (fewest edits), longer on a tie
            var hit: Hit? = null
            var used = 1
            var bestD = Int.MAX_VALUE
            for (k in minOf(MAX_WORDS, toks.size - i) downTo 1) {
                val end = toks[i + k - 1].second
                if (end > limit) continue
                var a = toks[i].first; var b = end
                while (a < b && text[a] in EDGE) a++
                while (b > a && text[b - 1] in EDGE) b--
                if (a >= b) continue
                val raw = text.substring(a, b)
                val (real, d) = score(raw) ?: continue
                if (d < bestD) { bestD = d; hit = Hit(a, b, raw, real, guess = true); used = k }
            }
            if (hit != null) out.add(hit)
            i += used
        }
        return out
    }

    /** A real name written plainly is not a code name: leave it alone. */
    private fun samePerson(h: Hit) = Matcher.normalize(h.code) == Matcher.normalize(h.real)

    /** Best real-name guess for a misspelled or disguised word, or null. */
    fun guess(raw: String): String? = score(raw)?.first

    /** (real name, edits needed) for a misspelled or disguised word, or null. */
    private fun score(raw: String): Pair<String, Int>? {
        val k = squash(raw)
        if (k.length < MIN_KEY || Matcher.normalize(raw) in realWords) return null
        // A real name only counts as disguised if it was dotted, spaced out or written in digits ("M.o.d.i", "M0di")
        if (k in triggerKeys && raw.none { it in DISGUISE } && !Regex("^(\\S\\s)+\\S$").matches(raw.trim())) return null
        if (keys.containsKey(k)) return keys[k]?.takeIf { Matcher.normalize(it) != Matcher.normalize(raw) }?.let { it to 0 }
        if (k.length < 6) return null
        val maxD = if (k.length >= 10) 2 else 1
        var best: String? = null
        var bestD = maxD + 1
        var tie = false
        for (c in byFirst[k[0]] ?: return null) {
            if (kotlin.math.abs(c.length - k.length) > maxD || c.length < 6) continue
            val d = distance(k, c, maxD)
            val real = keys[c] ?: continue
            if (d < bestD) { bestD = d; best = real; tie = false }
            else if (d == bestD && real != best) tie = true
        }
        return if (best != null && !tie && Matcher.normalize(best) != Matcher.normalize(raw)) best to bestD else null
    }

    /** [skip] = start offsets the reader chose to keep as written. */
    fun decode(text: String, hits: List<Hit> = find(text), skip: Set<Int> = emptySet()): String {
        val sb = StringBuilder()
        var last = 0
        for (h in hits) {
            sb.append(text, last, h.start).append(if (h.start in skip) h.code else h.real)
            last = h.end
        }
        return sb.append(text, last, text.length).toString()
    }

    companion object {
        private const val MIN_KEY = 4
        private const val MAX_WORDS = 4
        private const val DISGUISE = "0134578@$._-*|/•~"
        private const val EDGE = ".,!?;:\"'()[]{}#@*_~“”‘’…-|।"

        /** The everyday name: "Modi", "Amit Shah", "BJP", "Adani". */
        fun realName(e: Entity): String = when (e.id) {
            "modi" -> "Modi"
            "adani" -> "Adani"
            "eci" -> "Election Commission"
            else -> e.label
        }

        /**
         * Spelling-insensitive key: lower case, look-alike digits to letters, drop
         * dots/dashes/spaces/emoji, common Hinglish spellings folded, doubled letters collapsed.
         * "M.o.d.i" -> "modi", "Odiijii" -> "odiji", "Kamal Partyy 🪷" -> "kamalparti".
         */
        fun squash(s: String): String {
            val sb = StringBuilder()
            for (ch in s.lowercase()) {
                val c = when (ch) { '0' -> 'o'; '1' -> 'i'; '3' -> 'e'; '4' -> 'a'; '5' -> 's'; '7' -> 't'; '@' -> 'a'; '$' -> 's'; else -> ch }
                if (Character.isLetter(c) || Character.getType(c) == Character.NON_SPACING_MARK.toInt()
                    || Character.getType(c) == Character.COMBINING_SPACING_MARK.toInt()) sb.append(c)
            }
            var k = sb.toString()
                .replace("ph", "f").replace("ck", "k").replace("q", "k")
                .replace('w', 'v').replace('z', 'j')
                .replace("ee", "i").replace("oo", "u")
            k = collapse(k)
            if (k.endsWith("y")) k = k.dropLast(1) + "i"
            return collapse(k)
        }

        private fun collapse(k: String): String {
            val out = StringBuilder()
            for (c in k) if (out.isEmpty() || out.last() != c) out.append(c)
            return out.toString()
        }

        /** Edit distance with swaps (optimal string alignment), stopping early past [max]. */
        fun distance(a: String, b: String, max: Int): Int {
            val n = a.length; val m = b.length
            if (kotlin.math.abs(n - m) > max) return max + 1
            var prev2 = IntArray(m + 1)
            var prev = IntArray(m + 1) { it }
            var cur = IntArray(m + 1)
            for (i in 1..n) {
                cur[0] = i
                var rowMin = cur[0]
                for (j in 1..m) {
                    val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                    var v = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
                    if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) v = minOf(v, prev2[j - 2] + 1)
                    cur[j] = v
                    if (v < rowMin) rowMin = v
                }
                if (rowMin > max) return max + 1
                val t = prev2; prev2 = prev; prev = cur; cur = t
            }
            return prev[m]
        }
    }
}
