package com.scanread.text.model

/** A single word (OCR element) with its position. */
data class TextWord(
    val text: String,
    val box: Box,
)
