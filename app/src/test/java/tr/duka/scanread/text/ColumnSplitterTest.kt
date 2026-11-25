package tr.duka.scanread.text

import tr.duka.scanread.text.model.Box
import tr.duka.scanread.text.model.TextLine
import tr.duka.scanread.text.model.TextWord
import org.junit.Assert.assertEquals
import org.junit.Test

class ColumnSplitterTest {

    private val splitter = ColumnSplitter()

    /** A line from (text, left) pairs; words are 10 px per letter, 20 px tall. */
    private fun line(top: Int, vararg words: Pair<String, Int>): TextLine {
        val placed = words.map { (text, left) -> TextWord(text, Box(left, top, left + text.length * 10, top + 20)) }
        return TextLine(
            text = placed.joinToString(" ") { it.text },
            box = placed.map { it.box }.reduce { acc, box -> acc.union(box) },
            words = placed,
        )
    }

    @Test
    fun `separates staggered columns read as shared lines`() {
        val lines = listOf(
            line(100, "GOOD" to 100, "SEE" to 230),
            line(124, "MORNING" to 85, "YOU" to 230),
            line(148, "TO" to 110, "ALL" to 138, "SOON" to 225),
        )

        val columns = splitter.split(lines)

        assertEquals(
            listOf(listOf("GOOD", "MORNING", "TO ALL"), listOf("SEE", "YOU", "SOON")),
            columns.map { column -> column.map { it.text } },
        )
    }

    @Test
    fun `keeps a centered balloon with one wide gap together`() {
        val lines = listOf(
            line(100, "WHERE" to 100, "ARE" to 158),
            line(124, "YOU" to 95, "GOING" to 140),
            line(148, "SO" to 100, "LATE?" to 135),
        )

        assertEquals(1, splitter.split(lines).size)
    }

    /** A line of words given as (text, left, right), 37 px tall like widely spaced comic lettering. */
    private fun spaced(top: Int, vararg words: Triple<String, Int, Int>): TextLine {
        val placed = words.map { (text, left, right) -> TextWord(text, Box(left, top, right, top + 37)) }
        return TextLine(
            text = placed.joinToString(" ") { it.text },
            box = placed.map { it.box }.reduce { acc, box -> acc.union(box) },
            words = placed,
        )
    }

    @Test
    fun `keeps widely spaced lettering in one balloon together`() {
        // Word gaps are about 0.6 of the letter height; the long word on the second line overlaps
        // the first word above it by less than a third.
        val lines = listOf(
            spaced(355, Triple("WHEN", 427, 526), Triple("THE", 548, 626), Triple("SUN", 651, 707), Triple("RISES", 729, 841)),
            spaced(416, Triple("TOMORROW", 278, 456), Triple("MORNING", 478, 587), Triple("WE", 609, 731), Triple("CAN", 755, 811), Triple("LEAVE", 833, 989)),
            spaced(470, Triple("TOGETHER", 422, 578), Triple("AGAIN", 600, 736), Triple("SOON", 756, 846)),
        )

        assertEquals(1, splitter.split(lines).size)
    }

    @Test
    fun `keeps a centered balloon with a short first line together`() {
        val lines = listOf(
            spaced(1980, Triple("PLEASE", 410, 580)),
            spaced(2032, Triple("REMEMBER", 192, 444), Triple("THE", 466, 552), Triple("NEW", 573, 678), Triple("TIMES", 700, 821)),
            spaced(2085, Triple("FOR", 215, 324), Triple("THE", 345, 474), Triple("BUS", 497, 532), Triple("STOP", 553, 607), Triple("NEARBY", 629, 800)),
        )

        assertEquals(1, splitter.split(lines).size)
    }

    @Test
    fun `keeps a balloon whose wide word gaps line up vertically together`() {
        // Measured from small lettering: gaps of about 0.6 of the height happen to align across lines.
        fun small(top: Int, vararg words: Pair<Int, Int>): TextLine {
            val placed = words.map { (left, right) -> TextWord("WORD", Box(left, top, right, top + 24)) }
            return TextLine(placed.joinToString(" ") { it.text }, placed.map { it.box }.reduce { a, b -> a.union(b) }, placed)
        }
        val lines = listOf(
            small(1282, 223 to 272, 284 to 328, 343 to 502),
            small(1321, 179 to 234, 248 to 331, 345 to 385, 401 to 546),
            small(1360, 241 to 281, 296 to 339, 353 to 483),
        )

        assertEquals(1, splitter.split(lines).size)
    }

    @Test
    fun `keeps a balloon together when OCR reads each half of its lines separately`() {
        // A wide gap after a full stop makes OCR return both halves of each row as separate lines.
        val lines = listOf(
            spaced(400, Triple("OK.", 560, 700)),
            spaced(400, Triple("NOW,", 726, 880)),
            spaced(455, Triple("LET'S", 560, 650)),
            spaced(455, Triple("GO HOME.", 676, 880)),
        )

        assertEquals(1, splitter.split(lines).size)
    }

    @Test
    fun `separates overlapping balloons that OCR read across only once`() {
        // A narrow left balloon and a wider right one; only the first line reads across both, and
        // their horizontal extents overlap slightly.
        val lines = listOf(
            line(100, "ITS" to 345, "AND" to 385, "THE" to 421),
            line(124, "MAKES" to 330),
            line(126, "NEXT" to 424, "DAY" to 470),
            line(150, "YOU" to 330),
            line(152, "WE" to 418, "LEAVE" to 445),
            line(176, "SOON" to 330),
            line(178, "EARLY" to 438),
        )

        val columns = splitter.split(lines)

        assertEquals(
            listOf(listOf("ITS", "MAKES", "YOU", "SOON"), listOf("AND THE", "NEXT DAY", "WE LEAVE", "EARLY")),
            columns.map { column -> column.map { it.text } },
        )
    }

    @Test
    fun `leaves short blocks alone`() {
        val lines = listOf(line(100, "HEY" to 100, "THERE" to 200))

        assertEquals(1, splitter.split(lines).size)
    }
}
