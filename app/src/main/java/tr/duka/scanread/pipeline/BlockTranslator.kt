package tr.duka.scanread.pipeline

import tr.duka.scanread.text.SentenceSplitter
import tr.duka.scanread.text.model.TextBlock
import tr.duka.scanread.translate.TranslationEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * @property translation the engine output.
 * @property displayText what is rendered: the translation, in capitals when the source was.
 */
data class TranslatedBlock(
    val block: TextBlock,
    val translation: String,
    val displayText: String,
)

/**
 * Translates text blocks sentence by sentence (all sentences of a screen in one batch) and
 * restores the original letter case style for display.
 */
class BlockTranslator(
    private val engine: TranslationEngine,
    private val targetLocale: Locale,
    /** Optional source text repair applied before translation, e.g. splitting glued words. */
    private val repair: (String) -> String = { it },
) {
    suspend fun translate(blocks: List<TextBlock>): List<TranslatedBlock> {
        val sentencesPerBlock = withContext(Dispatchers.Default) {
            blocks.map { SentenceSplitter.split(repair(it.text)) }
        }
        val sources = sentencesPerBlock.flatten()
        val translations = engine.translate(sources).mapIndexed { index, text -> cleanUp(sources[index], text) }

        var offset = 0
        return blocks.mapIndexed { index, block ->
            val count = sentencesPerBlock[index].size
            val translation = translations.subList(offset, offset + count).joinToString(" ")
            offset += count
            TranslatedBlock(
                block = block,
                translation = translation,
                // Locale-aware, e.g. Turkish "i" becomes "İ".
                displayText = if (block.isAllCaps) translation.uppercase(targetLocale) else translation,
            )
        }
    }

    /** Removes artifacts translation models add, such as a leading dialogue dash. */
    private fun cleanUp(source: String, translation: String): String {
        var result = translation.trim()
        if (!source.trimStart().startsWith("-")) result = result.trimStart('-', '–', '—', ' ')
        return result
    }
}
