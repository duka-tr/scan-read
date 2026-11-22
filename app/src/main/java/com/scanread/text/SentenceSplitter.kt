package com.scanread.text

/**
 * Splits text into sentences. Translating sentences separately keeps short exclamations
 * ("No way!") from being dropped when they precede a longer sentence.
 *
 * A sentence ends at `.`, `!`, `?` or `…` (possibly repeated) followed by whitespace and a
 * capital letter, digit or opening quote. Common title abbreviations ("Mr.") never end a sentence.
 */
object SentenceSplitter {

    private val TERMINATORS = setOf('.', '!', '?', '…')
    private val OPENING_QUOTES = setOf('"', '\'', '“', '‘', '(')
    private val ABBREVIATIONS = setOf("mr", "mrs", "ms", "dr", "st", "jr", "sr", "vs", "prof", "mt")

    fun split(text: String): List<String> {
        val sentences = mutableListOf<String>()
        var start = 0
        var i = 0
        while (i < text.length) {
            if (text[i] !in TERMINATORS) {
                i++
                continue
            }
            var end = i
            while (end + 1 < text.length && (text[end + 1] in TERMINATORS || text[end + 1] in "\"'”’)")) end++

            val next = text.indexOfFirst(end + 1) { !it.isWhitespace() }
            val hasSpace = next > end + 1
            if (next == -1 || (hasSpace && startsSentence(text[next]) && !isAbbreviation(text, i))) {
                text.substring(start, end + 1).trim().takeIf(String::isNotEmpty)?.let(sentences::add)
                start = end + 1
            }
            i = end + 1
        }
        text.substring(start).trim().takeIf(String::isNotEmpty)?.let(sentences::add)
        return sentences
    }

    private fun startsSentence(char: Char): Boolean = char.isUpperCase() || char.isDigit() || char in OPENING_QUOTES

    private fun isAbbreviation(text: String, period: Int): Boolean {
        if (text[period] != '.') return false
        var wordStart = period
        while (wordStart > 0 && text[wordStart - 1].isLetter()) wordStart--
        return text.substring(wordStart, period).lowercase() in ABBREVIATIONS
    }

    private inline fun String.indexOfFirst(from: Int, predicate: (Char) -> Boolean): Int {
        for (index in from until length) if (predicate(this[index])) return index
        return -1
    }
}
