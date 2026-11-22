package tr.duka.scanread.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextNormalizerTest {

    @Test
    fun `joinLines joins lines with single spaces`() {
        assertEquals(
            "Where do you think you are going?",
            TextNormalizer.joinLines(listOf("Where do you  ", " think you are", "going?")),
        )
    }

    @Test
    fun `joinLines merges words broken with a hyphen`() {
        assertEquals("I KNOW SOMETHING", TextNormalizer.joinLines(listOf("I KNOW SOME-", "THING")))
    }

    @Test
    fun `joinLines keeps dashes that are not word breaks`() {
        assertEquals("WAIT -- STOP", TextNormalizer.joinLines(listOf("WAIT --", "STOP")))
        assertEquals("WAIT - 3 MORE", TextNormalizer.joinLines(listOf("WAIT -", "3 MORE")))
    }

    @Test
    fun `joinLines ignores blank lines`() {
        assertEquals("A B", TextNormalizer.joinLines(listOf("A", "  ", "B")))
    }

    @Test
    fun `isAllCaps detects capitalized text`() {
        assertTrue(TextNormalizer.isAllCaps("HEY! WHAT ARE YOU DOING?"))
        assertTrue(TextNormalizer.isAllCaps("I'M FINE, MOM"))
        assertFalse(TextNormalizer.isAllCaps("Hey! What are you doing?"))
        assertFalse(TextNormalizer.isAllCaps("I"))
        assertFalse(TextNormalizer.isAllCaps("12:45"))
    }

    @Test
    fun `toSentenceCase capitalizes sentence starts`() {
        assertEquals(
            "Hey! Where are you going? Come back.",
            TextNormalizer.toSentenceCase("HEY! WHERE ARE YOU GOING? COME BACK."),
        )
    }

    @Test
    fun `toSentenceCase keeps the pronoun I capitalized`() {
        assertEquals(
            "Well, I think I'm right and I'll prove it.",
            TextNormalizer.toSentenceCase("WELL, I THINK I'M RIGHT AND I'LL PROVE IT."),
        )
    }

    @Test
    fun `toSentenceCase capitalizes title abbreviations`() {
        assertEquals("Hey, Mr. Fish monger!", TextNormalizer.toSentenceCase("HEY, MR. FISH MONGER!"))
    }

    @Test
    fun `repairContractions restores dropped apostrophes`() {
        assertEquals(
            "I've got it! You'll see, don't worry. What're you doing?",
            TextNormalizer.repairContractions("Ive got it! Youll see, dont worry. Whatre you doing?"),
        )
        assertEquals("Best day I've seen", TextNormalizer.repairContractions("Best day ive seen"))
    }

    @Test
    fun `repairContractions keeps ambiguous words`() {
        assertEquals("We were well. Its tail.", TextNormalizer.repairContractions("We were well. Its tail."))
    }

    @Test
    fun `toSentenceCase handles leading punctuation`() {
        assertEquals("...And then? Nothing.", TextNormalizer.toSentenceCase("...AND THEN? NOTHING."))
    }
}
