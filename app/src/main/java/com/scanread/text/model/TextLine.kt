package com.scanread.text.model

/**
 * A single line of text as returned by OCR.
 *
 * @property words the words of the line with their positions, when the OCR engine provides them.
 */
data class TextLine(
    val text: String,
    val box: Box,
    val words: List<TextWord> = emptyList(),
)
