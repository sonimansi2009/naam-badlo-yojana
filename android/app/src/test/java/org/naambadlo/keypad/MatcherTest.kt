package org.naambadlo.keypad

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MatcherTest {
    private val modi = Entity("modi", "Narendra Modi", listOf("modi", "narendra modi", "मोदी"), listOf("Bodi", "Odiji", "Naru"))
    private val shah = Entity("amit-shah", "Amit Shah", listOf("amit shah", "shah"), listOf("Mota Bhai", "Chanakya"), popular = 1, ask = setOf("shah"))
    private val eci = Entity("eci", "ECI", listOf("eci", "भारत निर्वाचन आयोग"), listOf("Ayog ji", "Vote Ghar"))
    private val m = Matcher(listOf(modi, shah, eci))

    @Test fun singleWord() = assertEquals("modi", m.matchAtEnd("I think modi")?.entity?.id)
    @Test fun trailingPunctuation() = assertEquals(20, m.matchAtEnd("kya kar rahe ho Modi?! ")?.end)
    @Test fun longestMultiWord() = assertEquals(4, m.matchAtEnd("ask Amit Shah")?.start)
    @Test fun hashtag() = assertEquals(1, m.matchAtEnd("#modi")?.start)
    @Test fun possessive() = assertEquals(4, m.matchAtEnd("Modi's")?.end)
    @Test fun hindi() = assertEquals("eci", m.matchAtEnd("भारत निर्वाचन आयोग।")?.entity?.id)
    @Test fun typedNicknamesAreLeftAlone() = assertNull(m.matchAtEnd("Bodi"))

    @Test fun replaceAll() = assertEquals(
        "Bodi aur Mota Bhai, Ayog ji!",
        m.replaceAll("Modi aur Amit Shah, ECI!") { e, _ -> e.alternates[0] },
    )

    @Test fun bareSurnameAsksFirst() {
        assertTrue(m.findAll("Shah Rukh ki film").single().ask)
        assertEquals("Shah Rukh ki film", m.replaceAll("Shah Rukh ki film"))
        assertTrue(!m.findAll("Amit Shah bola").single().ask)
    }

    @Test fun tiers() {
        assertEquals(listOf("Mota Bhai"), shah.recognisable)
        assertEquals(listOf("Chanakya"), shah.stealth)
    }

    @Test fun shuffleKeepsWorking() {
        val mem = AltMemory()
        var text = m.replaceAll("Modi aur Amit Shah, ECI!", mem)
        repeat(10) {
            val next = m.replaceAll(text, mem)
            assertEquals(3, m.findAll(text, mem).size)
            assertNotEquals(text, next)
            text = next
        }
        assertTrue(m.findAll(text).isEmpty())
    }

    @Test fun decodeGuessesMisspellings() {
        val bjp = Entity("bjp", "BJP", listOf("bjp"), listOf("Kamal Party", "Kamal Party 🪷"))
        val d = Decoder(listOf(modi, shah, bjp))
        val t = "Odiijii aur Mota bhaai ne kaha, Kamal Partyy jeetegi, M.o.d.i bhi"
        val hits = d.find(t)
        assertEquals("Modi aur Amit Shah ne kaha, BJP jeetegi, Modi bhi", d.decode(t, hits))
        assertEquals(true, hits.first().guess)
    }

    @Test fun decodeLeavesPlainWordsAlone() {
        val d = Decoder(listOf(modi, shah))
        val t = "Aaj mausam accha hai, Modi ji ne kaha"
        assertEquals(t, d.decode(t))
    }

    @Test fun decodeGuessesJumbles() {
        val d = Decoder(listOf(modi, shah))
        assertEquals("Modi aur Modi ne kaha", d.decode("Nodiji aur Nodi ne kaha"))
    }
}
