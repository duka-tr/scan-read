package tr.duka.scanread.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WordSegmenterTest {

    private val segmenter = WordSegmenter(
        mapOf(
            "a" to -4f, "i" to -4.5f, "of" to -4f, "the" to -3.5f, "with" to -5f, "you" to -4.5f,
            "where" to -7.5f, "left" to -8.5f, "real" to -8f, "whole" to -9f, "world" to -7f,
            "na" to -10f, "mi" to -10.5f, "something" to -7.5f, "rare" to -14f, "word" to -8f,
            "sailor" to -12.2f, "chi" to -9f, "go" to -6f, "trespass" to -13f, "tress" to -12f,
            "passing" to -9f, "anna" to -11f, "possible" to -8f, "right" to -7f, "here" to -6f,
            "nothing" to -7.5f, "going" to -7f, "coming" to -7.5f, "taxi" to -10f,
        ),
    )

    @Test
    fun `repairs a doubled letter in a long unknown word`() {
        assertEquals("He was trespassing.", segmenter.segment("He was tresspassing."))
        assertEquals("POSSIBLE", segmenter.segment("POSSSIBLE"))
    }

    @Test
    fun `repairs an l read for an I`() {
        assertEquals("RIGHT HERE", segmenter.segment("RLGHT HERE"))
    }

    @Test
    fun `restores an exclamation mark read as a letter glued to the last word`() {
        assertEquals("Help is coming!", segmenter.segment("Help is comingi"))
        assertEquals("HELP IS COMING!!", segmenter.segment("HELP IS COMINGI!!"))
        assertEquals("Help is coming!", segmenter.segment("Help is comingl"))
    }

    @Test
    fun `keeps last words that only end in i`() {
        assertEquals("Call a taxi", segmenter.segment("Call a taxi"))
        assertEquals("Hello Naomi", segmenter.segment("Hello Naomi"))
    }

    @Test
    fun `restores a dropped g`() {
        assertEquals("It means nothing, we are GOING", segmenter.segment("It means nothin', we are GOIN'"))
        assertEquals("Rock 'n' roll", segmenter.segment("Rock 'n' roll"))
    }

    @Test
    fun `keeps short names with doubled letters`() {
        assertEquals("Annna", segmenter.segment("Annna"))
    }

    @Test
    fun `splits glued frequent words`() {
        assertEquals("with you", segmenter.segment("withyou"))
        assertEquals("where I left", segmenter.segment("whereileft"))
        assertEquals("A real", segmenter.segment("Areal"))
    }

    @Test
    fun `long parts may be less frequent words`() {
        assertEquals("a sailor", segmenter.segment("asailor"))
    }

    @Test
    fun `a single letter must be followed by a longer word`() {
        assertNull(segmenter.split("ichigo"))
    }

    @Test
    fun `keeps known words, names and rare combinations`() {
        assertNull(segmenter.split("something"))
        assertNull("syllable-like two-letter parts are not words", segmenter.split("nami"))
        assertNull("parts must be frequent words", segmenter.split("rareword"))
        assertNull("unknown names stay intact", segmenter.split("zorblax"))
    }

    @Test
    fun `leaves punctuation and other words untouched`() {
        assertEquals("Look at the whole world!", segmenter.segment("Look at the wholeworld!"))
    }
}
