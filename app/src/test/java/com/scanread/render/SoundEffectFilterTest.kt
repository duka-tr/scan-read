package com.scanread.render

import com.scanread.text.model.Box
import com.scanread.text.model.TextBlock
import com.scanread.text.model.TextLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SoundEffectFilterTest {

    private val filter = SoundEffectFilter()

    private fun block(text: String, lineHeight: Int, lines: Int = 1) = TextBlock(
        lines = List(lines) { TextLine(text, Box(0, it * lineHeight, 100, (it + 1) * lineHeight)) },
        box = Box(0, 0, 100, lineHeight * lines),
        sourceText = text,
        text = text,
        isAllCaps = true,
    )

    @Test
    fun `a single word the engine leaves unchanged is a sound effect`() {
        val blocks = listOf(block("BLAM", 30), block("ZONK", 30), block("HOW ARE YOU", 30))
        val translations = listOf("BLAN", "ZONK", "NASILSIN")

        assertEquals(listOf(true, true, false), filter.detect(blocks, translations, listOf(false, false, false)))
    }

    @Test
    fun `phrases with names are translated even if mostly unchanged`() {
        val blocks = listOf(block("MR. SMITH!", 30))

        assertEquals(listOf(false), filter.detect(blocks, listOf("BAY SMITH!"), listOf(false)))
    }

    @Test
    fun `very large lettering outside balloons is a sound effect`() {
        val blocks = listOf(block("BOOM BOOM BOOM", 120), block("A", 30, 2), block("B", 30), block("C", 30))
        val translations = listOf("XYZ QWE ASD", "...", "...", "...")

        assertTrue(filter.detect(blocks, translations, listOf(false, false, false, false))[0])
    }

    @Test
    fun `shouted dialogue in large letters is translated`() {
        val blocks = listOf(block("YOU?! A SAILOR?! NO WAY!!", 120), block("A", 30, 2), block("B", 30), block("C", 30))
        val translations = listOf("SEN Mİ?! DENİZCİ Mİ?! OLAMAZ!!", "...", "...", "...")

        assertEquals(false, filter.detect(blocks, translations, listOf(false, false, false, false))[0])
    }

    @Test
    fun `reading direction markers are left alone, even with OCR noise`() {
        val blocks = listOf(block("READ THIS WAY", 30), block("READ <THIS < WAY", 30), block("READY TO GO NOW", 30))
        val translations = listOf("BU ŞEKİLDE OKU", "BU YOLU OKU", "ŞİMDİ GİTMEYE HAZIR")

        assertEquals(listOf(true, true, false), filter.detect(blocks, translations, listOf(false, false, false)))
    }

    @Test
    fun `text inside balloons is always translated`() {
        val blocks = listOf(block("HMM", 30))

        assertEquals(listOf(false), filter.detect(blocks, listOf("HMM"), listOf(true)))
    }

    @Test
    fun `similarity ignores case and accents`() {
        assertEquals(1f, SoundEffectFilter.similarity("Kink", "KİNK"))
        assertTrue(SoundEffectFilter.similarity("BLAM", "BLAN") >= 0.5f)
        assertTrue(SoundEffectFilter.similarity("HOW ARE YOU", "NASILSIN") < 0.5f)
    }
}
