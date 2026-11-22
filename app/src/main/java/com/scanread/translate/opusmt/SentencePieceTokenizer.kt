package com.scanread.translate.opusmt

import java.io.File
import java.text.Normalizer

/**
 * Minimal SentencePiece *unigram* tokenizer for Marian (Opus-MT) models.
 *
 * Encoding normalizes text (NFKC, collapsed whitespace, `▁` word marker), finds the most likely
 * segmentation with the Viterbi algorithm over the source pieces, merges consecutive unknown
 * characters and maps pieces to model ids through the shared vocabulary.
 *
 * @param pieces source-language pieces with their log-probability scores.
 * @param vocabulary model vocabulary; the list index is the token id.
 */
class SentencePieceTokenizer(
    pieces: List<Piece>,
    private val vocabulary: List<String>,
    private val unknownId: Int,
) {
    data class Piece(val text: String, val score: Float, val isNormal: Boolean)

    private val scores: Map<String, Float> = pieces.filter { it.isNormal }.associate { it.text to it.score }
    private val maxPieceLength = scores.keys.maxOfOrNull { it.length } ?: 1
    private val unknownScore = (scores.values.minOrNull() ?: 0f) - UNKNOWN_PENALTY
    private val ids: Map<String, Int> = vocabulary.withIndex().associate { (index, token) -> token to index }

    /** Token ids of [text], without the end-of-sentence token. */
    fun encode(text: String): List<Int> {
        val normalized = normalize(text)
        if (normalized.isEmpty()) return emptyList()
        return segment(normalized).map { piece -> if (piece == null) unknownId else ids[piece] ?: unknownId }
    }

    /** Text for model output [tokenIds]; special tokens must be removed beforehand. */
    fun decode(tokenIds: List<Int>): String =
        tokenIds.joinToString("") { vocabulary.getOrElse(it) { "" } }
            .replace(WORD_MARKER, ' ')
            .trim()

    private fun normalize(text: String): String {
        val collapsed = Normalizer.normalize(text, Normalizer.Form.NFKC)
            .replace(WHITESPACE, " ")
            .trim()
        if (collapsed.isEmpty()) return ""
        return WORD_MARKER + collapsed.replace(' ', WORD_MARKER)
    }

    /** Viterbi segmentation; `null` entries are (merged) unknown characters. */
    private fun segment(text: String): List<String?> {
        val length = text.length
        val best = FloatArray(length + 1) { Float.NEGATIVE_INFINITY }
        val previous = IntArray(length + 1) { -1 }
        val isUnknown = BooleanArray(length + 1)
        best[0] = 0f

        for (start in 0 until length) {
            if (best[start] == Float.NEGATIVE_INFINITY) continue
            val charLength = Character.charCount(text.codePointAt(start))
            var hasSingleCharPiece = false

            for (end in start + 1..minOf(length, start + maxPieceLength)) {
                val score = scores[text.substring(start, end)] ?: continue
                if (end - start == charLength) hasSingleCharPiece = true
                val candidate = best[start] + score
                if (candidate > best[end]) {
                    best[end] = candidate
                    previous[end] = start
                    isUnknown[end] = false
                }
            }
            // Like SentencePiece: an unknown character is only considered when no piece covers it.
            if (!hasSingleCharPiece) {
                val end = start + charLength
                val candidate = best[start] + unknownScore
                if (candidate > best[end]) {
                    best[end] = candidate
                    previous[end] = start
                    isUnknown[end] = true
                }
            }
        }

        val result = ArrayDeque<String?>()
        var end = length
        while (end > 0) {
            val start = previous[end]
            val piece = if (isUnknown[end]) null else text.substring(start, end)
            // Consecutive unknown characters become a single unknown token.
            if (!(piece == null && result.isNotEmpty() && result.first() == null)) result.addFirst(piece)
            end = start
        }
        return result.toList()
    }

    companion object {
        private const val WORD_MARKER = '▁'
        private const val UNKNOWN_PENALTY = 10f
        private val WHITESPACE = Regex("\\s+")

        /** Loads `source_pieces.tsv` and `vocab.txt` from a model bundle. */
        fun load(piecesFile: File, vocabularyFile: File, unknownId: Int): SentencePieceTokenizer {
            val pieces = piecesFile.readLines().filter(String::isNotEmpty).map { line ->
                val (text, score, type) = line.split('\t')
                Piece(text, score.toFloat(), type == "normal")
            }
            val vocabulary = vocabularyFile.readLines()
            return SentencePieceTokenizer(pieces, vocabulary, unknownId)
        }
    }
}
