package tr.duka.scanread.text

import org.junit.Assert.assertEquals
import org.junit.Test

class SentenceSplitterTest {

    @Test
    fun `splits at sentence ends followed by a capital`() {
        assertEquals(
            listOf("No way!", "That's not fair!"),
            SentenceSplitter.split("No way! That's not fair!"),
        )
    }

    @Test
    fun `keeps repeated punctuation with its sentence`() {
        assertEquals(listOf("You think it's funny?!", "Ha!"), SentenceSplitter.split("You think it's funny?! Ha!"))
        assertEquals(listOf("Well...", "I already ate it!!"), SentenceSplitter.split("Well... I already ate it!!"))
    }

    @Test
    fun `does not split when the next word is lowercase`() {
        assertEquals(listOf("Wait... what?"), SentenceSplitter.split("Wait... what?"))
    }

    @Test
    fun `does not split after title abbreviations`() {
        assertEquals(listOf("Hey, Mr. Baker!"), SentenceSplitter.split("Hey, Mr. Baker!"))
    }

    @Test
    fun `returns text without terminators as one sentence`() {
        assertEquals(listOf("Hmm"), SentenceSplitter.split("  Hmm "))
        assertEquals(emptyList<String>(), SentenceSplitter.split("   "))
    }
}
