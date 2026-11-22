package com.scanread.text

import com.scanread.text.model.Box
import com.scanread.text.model.TextLine
import com.scanread.text.model.TextWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextBlockBuilderTest {

    private val builder = TextBlockBuilder()

    @Test
    fun `builds a normalized block from capitalized lines`() {
        val blocks = builder.build(
            listOf(
                TextLine("I CAN'T BELIEVE", Box(100, 100, 300, 130)),
                TextLine("IT'S OVER-", Box(120, 136, 280, 166)),
                TextLine("WHELMING!", Box(130, 172, 270, 202)),
            ),
        )

        val block = blocks.single()
        assertEquals("I CAN'T BELIEVE IT'S OVERWHELMING!", block.sourceText)
        assertEquals("I can't believe it's overwhelming!", block.text)
        assertTrue(block.isAllCaps)
        assertEquals(Box(100, 100, 300, 202), block.box)
    }

    @Test
    fun `keeps mixed case text as is`() {
        val block = builder.build(listOf(TextLine("Hello there.", Box(0, 0, 100, 20)))).single()

        assertEquals("Hello there.", block.text)
        assertFalse(block.isAllCaps)
    }

    @Test
    fun `drops blocks without enough letters`() {
        val blocks = builder.build(
            listOf(
                TextLine("12:45", Box(0, 0, 60, 20)),
                TextLine("23", Box(500, 900, 530, 920)),
            ),
        )

        assertTrue(blocks.isEmpty())
    }

    @Test
    fun `drops web addresses`() {
        val blocks = builder.build(
            listOf(
                TextLine("example.co.jp/", Box(0, 0, 300, 30)),
                TextLine("https://example.com", Box(0, 500, 300, 530)),
                TextLine("@ example.co.jp/", Box(0, 1000, 300, 1030)),
                TextLine("Aexample.shop,jp", Box(0, 1500, 300, 1530)),
            ),
        )

        assertTrue(blocks.isEmpty())
    }

    @Test
    fun `skips lines inside ignored areas`() {
        val blocks = builder.build(
            lines = listOf(
                TextLine("15:32 VoLTE", Box(20, 10, 200, 40)),
                TextLine("HELLO THERE", Box(100, 500, 300, 530)),
            ),
            ignoredAreas = listOf(Box(0, 0, 1080, 60)),
        )

        assertEquals(listOf("HELLO THERE"), blocks.map { it.sourceText })
    }

    @Test
    fun `separates neighbouring balloons joined into one OCR line`() {
        val left = TextLine(
            text = "MORNING",
            box = Box(100, 100, 190, 120),
            words = listOf(TextWord("MORNING", Box(100, 100, 190, 120))),
        )
        val joined = TextLine(
            text = "TO ALL WE'LL",
            box = Box(100, 126, 320, 146),
            words = listOf(
                TextWord("TO", Box(100, 126, 140, 146)),
                TextWord("ALL", Box(146, 126, 195, 146)),
                TextWord("WE'LL", Box(250, 126, 320, 146)),
            ),
        )

        val blocks = builder.build(listOf(left, joined))

        assertEquals(listOf("MORNING TO ALL", "WE'LL"), blocks.map { it.sourceText })
    }
}
