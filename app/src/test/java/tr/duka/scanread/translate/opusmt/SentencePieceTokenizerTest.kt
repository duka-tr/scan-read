package tr.duka.scanread.translate.opusmt

import tr.duka.scanread.translate.opusmt.SentencePieceTokenizer.Piece
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class SentencePieceTokenizerTest {

    // Vocabulary ids: 0 <unk>, 1 </s>, 2 ▁, 3 ▁he, 4 llo, 5 ▁hello, 6 ▁world, 7 !, 8 ▁w, 9 orld
    private val vocabulary = listOf("<unk>", "</s>", "▁", "▁he", "llo", "▁hello", "▁world", "!", "▁w", "orld")
    private val pieces = listOf(
        Piece("<unk>", 0f, isNormal = false),
        Piece("</s>", 0f, isNormal = false),
        Piece("▁", -2f, isNormal = true),
        Piece("▁he", -4f, isNormal = true),
        Piece("llo", -4f, isNormal = true),
        Piece("▁hello", -5f, isNormal = true),
        Piece("▁world", -5f, isNormal = true),
        Piece("!", -1f, isNormal = true),
        Piece("▁w", -3f, isNormal = true),
        Piece("orld", -3f, isNormal = true),
    )
    private val tokenizer = SentencePieceTokenizer(pieces, vocabulary, unknownId = 0)

    @Test
    fun `picks the most likely segmentation`() {
        // "▁hello" (-5) beats "▁he" + "llo" (-8); "▁world" (-5) beats "▁w" + "orld" (-6).
        assertEquals(listOf(5, 6, 7), tokenizer.encode("hello world!"))
    }

    @Test
    fun `normalizes whitespace and compatibility characters`() {
        assertEquals(listOf(5, 6), tokenizer.encode("  ｈｅｌｌｏ \n world "))
    }

    @Test
    fun `merges consecutive unknown characters`() {
        assertEquals(listOf(5, 2, 0, 7), tokenizer.encode("hello ÄÖÜ!"))
    }

    @Test
    fun `decodes pieces back to text`() {
        assertEquals("hello world!", tokenizer.decode(listOf(3, 4, 8, 9, 7)))
    }

    /**
     * Compares with reference tokenizations from the real SentencePiece library, written by
     * `tools/opus-mt/prepare_model.py`. Runs only when `SCANREAD_OPUS_BUNDLE` points to a bundle.
     */
    @Test
    fun `matches SentencePiece on a real model bundle`() {
        val directory = System.getenv("SCANREAD_OPUS_BUNDLE")?.let(::File)
        assumeTrue("SCANREAD_OPUS_BUNDLE not set", directory?.isDirectory == true)

        val bundle = OpusMtBundle(directory!!)
        val config = bundle.loadConfig()
        val real = SentencePieceTokenizer.load(bundle.sourcePiecesFile, bundle.vocabularyFile, config.unknownTokenId)

        File(directory, "tokenizer_samples.tsv").readLines().filter(String::isNotEmpty).forEach { line ->
            val (text, ids) = line.split('\t')
            val expected = ids.split(' ').map(String::toInt)
            assertEquals("Tokenization of \"$text\"", expected, real.encode(text) + config.eosTokenId)
        }
    }
}
