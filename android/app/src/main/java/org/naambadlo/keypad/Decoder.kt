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
    private val realOf = HashMap<String, String>()
    /** surname sound minus its first consonant ("odi" from Modi) -> real name, for jumbles like Nodi, Bodi, Gadani */
    private val rhymes = HashMap<String, String?>()
    /** first letter -> keys, for quick near-miss lookups */
    private val byFirst = HashMap<Char, MutableList<String>>()

    init {
        for (e in entities) {
            val real = realName(e)
            for (t in e.triggers) { realWords.add(Matcher.normalize(t)); triggerKeys.add(squash(t)); realOf[Matcher.normalize(t)] = real }
            for (w in e.alternates + e.triggers) {
                val k = squash(w)
                if (k.length < MIN_KEY) continue
                if (keys.containsKey(k) && keys[k] != real) keys[k] = null else keys[k] = real
            }
        }
        for (k in keys.keys) byFirst.getOrPut(k[0]) { ArrayList() }.add(k)
        // People only (not parties or phrases): the last word of each name, e.g. Modi, Gadkari, Adani
        for (e in entities) {
            if (e.id in NOT_PEOPLE) continue
            val real = realName(e)
            for (t in e.triggers) {
                val w = t.split(' ').lastOrNull { it.isNotBlank() && Matcher.normalize(it) !in HONORIFICS } ?: continue
                val (_, rest) = onset(squash(w))
                if (rest.length < 3 || w.any { it.code > 0x7F }) continue
                if (rhymes.containsKey(rest) && rhymes[rest] != real) rhymes[rest] = null else rhymes[rest] = real
            }
        }
    }

    fun find(text: String): List<Hit> {
        val exact = matcher.findAll(text).map {
            Hit(it.start, it.end, text.substring(it.start, it.end), it.entity.alternates[0])
        }.filter { !samePerson(it) && !afterRealName(text, it) }
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
                if (k > 1 && d > 0) continue               // near-misses only for single words ("Narendra ne" is not a name)
                if (afterRealName(text, Hit(a, b, raw, real))) continue
                if (d < bestD) { bestD = d; hit = Hit(a, b, raw, real, guess = d > 0); used = k }
            }
            if (hit != null) out.add(hit)
            i += used
        }
        return mergeNeighbours(text, out)
    }

    /** "Lodi saheb" -> one name: neighbouring hits for the same person become one. */
    private fun mergeNeighbours(text: String, hits: List<Hit>): List<Hit> {
        val out = ArrayList<Hit>()
        for (h in hits) {
            val p = out.lastOrNull()
            if (p != null && p.real == h.real && text.substring(p.end, h.start).isBlank()) {
                out[out.size - 1] = Hit(p.start, h.end, text.substring(p.start, h.end), h.real, p.guess || h.guess)
            } else out.add(h)
        }
        return out
    }

    /** "Modi saheb": a nickname right after the real name is just a title, leave it. */
    private fun afterRealName(text: String, h: Hit): Boolean {
        var e = h.start
        while (e > 0 && text[e - 1].isWhitespace()) e--
        var b = e
        while (b > 0 && !text[b - 1].isWhitespace()) b--
        if (b >= e) return false
        val prev = text.substring(b, e).trim { it in EDGE }
        return realOf[Matcher.normalize(prev)] == h.real
    }

    /** A real name written plainly is not a code name: leave it alone. */
    private fun samePerson(h: Hit) = Matcher.normalize(h.code) == Matcher.normalize(h.real)

    /** Best real-name guess for a misspelled or disguised word, or null. */
    fun guess(raw: String): String? = score(raw)?.first

    /** (real name, edits needed) for a misspelled or disguised word, or null. */
    private fun score(raw: String): Pair<String, Int>? {
        val k0 = squash(raw)
        if (k0.length < MIN_KEY || Matcher.normalize(raw) in realWords) return null
        val disguised = raw.any { it in DISGUISE } || Regex("^(\\S\\s)+\\S$").matches(raw.trim())
        // Try the word as is, then without a trailing "ji" / "saheb" / "babu" ("Nodiji" -> "Nodi")
        val variants = listOf(k0) + SUFFIXES.filter { k0.endsWith(it) && k0.length - it.length >= MIN_KEY }.map { k0.dropLast(it.length) }
        for ((n, k) in variants.withIndex()) {
            // A real name only counts as disguised if it was dotted, spaced out or written in digits ("M.o.d.i", "M0di")
            if (k in triggerKeys) { if (disguised) return keys[k]?.let { it to 0 }; continue }
            if (keys.containsKey(k)) return keys[k]?.takeIf { Matcher.normalize(it) != Matcher.normalize(raw) }?.let { it to n.coerceAtMost(1) }
        }
        // Letter-swapped surnames: Nodi, Bodi, Godiji -> Modi; Badani -> Adani; Satil -> Patil
        for (k in variants) {
            if (k in triggerKeys) continue                 // "Modi saheb" is just Modi, not a jumble
            val (on, rest) = onset(k)
            if (on.length <= 2 && rest.length >= 3) rhymes[rest]?.let { return it to 1 }
        }
        val k = k0
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
        private val NOT_PEOPLE = setOf("bjp", "eci", "vote-chori", "cockroach-janta-party")
        private val HONORIFICS = setOf("ji", "जी", "sahab", "saheb", "babu", "bhai", "hm", "pm", "fm", "cec", "dr", "shri")
        private val SUFFIXES = listOf("ji", "sahab", "saheb", "babu")

        /** Split a squashed word at its first vowel: "modi" -> ("m", "odi"), "adani" -> ("", "adani"). */
        fun onset(k: String): Pair<String, String> {
            val i = k.indexOfFirst { it in "aeiou" }
            return if (i < 0) k to "" else k.substring(0, i) to k.substring(i)
        }
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
