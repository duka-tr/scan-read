package com.scanread.translate

import com.scanread.pipeline.BlockTranslator
import com.scanread.text.model.Box
import com.scanread.text.model.TextBlock
import com.scanread.text.model.TextLine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/** Fake engine: returns canned translations and records what it was asked to translate. */
private class FakeEngine(private val translations: Map<String, String>) : TranslationEngine {
    val requests = mutableListOf<List<String>>()
    val translatedCount get() = requests.sumOf { it.size }

    override suspend fun translate(texts: List<String>): List<String> {
        requests += texts
        return texts.map(translations::getValue)
    }

    override fun close() = Unit
}

class CachingTranslationEngineTest {

    @Test
    fun `repeated text is translated only once`() = runBlocking {
        val fake = FakeEngine(mapOf("Hello" to "Merhaba"))
        val engine = CachingTranslationEngine(fake)

        assertEquals("Merhaba", engine.translate("Hello"))
        assertEquals(listOf("Merhaba", "Merhaba"), engine.translate(listOf("Hello", "Hello")))
        assertEquals(1, fake.translatedCount)
    }

    @Test
    fun `only uncached texts are sent, once each`() = runBlocking {
        val fake = FakeEngine(mapOf("a" to "A", "b" to "B", "c" to "C"))
        val engine = CachingTranslationEngine(fake)
        engine.translate("a")

        assertEquals(listOf("B", "A", "C", "B"), engine.translate(listOf("b", "a", "c", "b")))
        assertEquals(listOf("b", "c"), fake.requests.last())
    }

    @Test
    fun `least recently used entries are evicted`() = runBlocking {
        val fake = FakeEngine(mapOf("a" to "A", "b" to "B", "c" to "C"))
        val engine = CachingTranslationEngine(fake, maxEntries = 2)

        engine.translate("a")
        engine.translate("b")
        engine.translate("c") // evicts "a"
        engine.translate("a")

        assertEquals(4, fake.translatedCount)
    }
}

class BlockTranslatorTest {

    private val turkish = Locale.forLanguageTag("tr")

    private fun block(text: String, allCaps: Boolean) = TextBlock(
        lines = listOf(TextLine(text, Box(0, 0, 100, 20))),
        box = Box(0, 0, 100, 20),
        sourceText = text,
        text = text,
        isAllCaps = allCaps,
    )

    @Test
    fun `capitalized sources are rendered in locale-aware capitals`() = runBlocking {
        val translator = BlockTranslator(FakeEngine(mapOf("This is it!" to "İşte bu kadar!")), turkish)

        val result = translator.translate(listOf(block("This is it!", allCaps = true))).single()

        assertEquals("İşte bu kadar!", result.translation)
        assertEquals("İŞTE BU KADAR!", result.displayText)
    }

    @Test
    fun `mixed case sources keep the translation as is`() = runBlocking {
        val translator = BlockTranslator(FakeEngine(mapOf("Hi there." to "Selam.")), turkish)

        assertEquals("Selam.", translator.translate(listOf(block("Hi there.", allCaps = false))).single().displayText)
    }

    @Test
    fun `sentences of all blocks are translated in one batch and reassembled`() = runBlocking {
        val engine = FakeEngine(
            mapOf("No way!" to "Olmaz!", "That's not fair!" to "Bu adil değil!", "Hey." to "Hey."),
        )
        val translator = BlockTranslator(engine, turkish)

        val results = translator.translate(
            listOf(block("No way! That's not fair!", allCaps = false), block("Hey.", allCaps = false)),
        )

        assertEquals(listOf("Olmaz! Bu adil değil!", "Hey."), results.map { it.translation })
        assertEquals(1, engine.requests.size)
    }

    @Test
    fun `leading dialogue dashes added by the engine are removed`() = runBlocking {
        val translator = BlockTranslator(FakeEngine(mapOf("No way!" to "-Olmaz!")), turkish)

        assertEquals("Olmaz!", translator.translate(listOf(block("No way!", allCaps = false))).single().translation)
    }
}

class LanguagePairTest {

    private val supported = setOf("en", "tr", "de", "es")

    @Test
    fun `targets the device language when supported`() {
        assertEquals(LanguagePair("en", "tr"), LanguagePair.forDevice("tr", supported))
    }

    @Test
    fun `falls back when the device language is the source or unsupported`() {
        assertEquals(LanguagePair.FALLBACK_TARGET, LanguagePair.forDevice("en", supported).target)
        assertEquals(LanguagePair.FALLBACK_TARGET, LanguagePair.forDevice("xx", supported).target)
    }
}
