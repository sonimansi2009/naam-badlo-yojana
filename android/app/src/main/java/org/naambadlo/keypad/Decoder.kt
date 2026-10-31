package org.naambadlo.keypad

/**
 * The other direction: finds code names in a message and puts the real names back.
 * Pure Kotlin, so it is unit-tested with the matcher.
 */
class Decoder(entities: List<Entity>) {
    data class Hit(val start: Int, val end: Int, val code: String, val real: String)

    // Each code name becomes a "trigger" that maps back to the real name.
    private val matcher = Matcher(entities.map { e ->
        Entity(e.id, e.label, e.alternates, listOf(realName(e)))
    })

    fun find(text: String): List<Hit> = matcher.findAll(text).map {
        Hit(it.start, it.end, text.substring(it.start, it.end), it.entity.alternates[0])
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
        /** The everyday name: "Modi", "Amit Shah", "BJP", "Adani". */
        fun realName(e: Entity): String = when (e.id) {
            "modi" -> "Modi"
            "adani" -> "Adani"
            "eci" -> "Election Commission"
            else -> e.label
        }
    }
}
