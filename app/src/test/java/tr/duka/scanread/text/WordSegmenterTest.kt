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
            "sailor" to -12.2f, "chi" to -9f, "go" to -6f,
        ),
    )

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
