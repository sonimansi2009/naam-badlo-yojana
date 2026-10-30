package org.naambadlo.keypad

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MatcherTest {
    private val m = Matcher(
        listOf(
            Entity("modi", "Narendra Modi", listOf("modi", "narendra modi", "मोदी"), listOf("Bodi", "Odiji")),
            Entity("amit-shah", "Amit Shah", listOf("amit shah", "shah"), listOf("Mota Bhai")),
            Entity("eci", "ECI", listOf("eci", "भारत निर्वाचन आयोग"), listOf("Ayog ji")),
        )
    )

    @Test fun singleWord() = assertEquals("modi", m.matchAtEnd("I think modi")?.entity?.id)
    @Test fun trailingPunctuation() = assertEquals(20, m.matchAtEnd("kya kar rahe ho Modi?! ")?.end)
    @Test fun longestMultiWord() = assertEquals(4, m.matchAtEnd("ask Amit Shah")?.start)
    @Test fun hashtag() = assertEquals(1, m.matchAtEnd("#modi")?.start)
    @Test fun possessive() = assertEquals(4, m.matchAtEnd("Modi's")?.end)
    @Test fun hindi() = assertEquals("eci", m.matchAtEnd("भारत निर्वाचन आयोग।")?.entity?.id)
    @Test fun neverOnAlternates() = assertNull(m.matchAtEnd("Bodi"))

    @Test fun replaceAll() = assertEquals(
        "Bodi aur Mota Bhai, Ayog ji!",
        m.replaceAll("Modi aur Amit Shah, ECI!") { it.alternates[0] },
    )
}
