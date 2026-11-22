package com.scanread.text

import com.scanread.text.model.Box
import com.scanread.text.model.TextBlock
import com.scanread.text.model.TextLine

/** Turns raw OCR lines into normalized [TextBlock]s and drops text that should not be translated. */
class TextBlockBuilder(
    private val splitter: LineSplitter = LineSplitter(),
    private val grouper: TextBlockGrouper = TextBlockGrouper(),
    private val minLetters: Int = DEFAULT_MIN_LETTERS,
) {
    /**
     * @param ignoredAreas screen areas whose text is skipped, e.g. system bars.
     * @param regions enclosed image regions (e.g. speech balloons) used to split and group lines.
     */
    fun build(
        lines: List<TextLine>,
        ignoredAreas: List<Box> = emptyList(),
        regions: RegionLookup = RegionLookup.NONE,
    ): List<TextBlock> {
        val candidates = lines
            .flatMap { splitter.split(it, regions) }
            .filterNot { line -> ignoredAreas.any { it.contains(line.box.centerX, line.box.centerY) } }
        return grouper.group(candidates, regions).mapNotNull(::toBlock)
    }

    private fun toBlock(lines: List<TextLine>): TextBlock? {
        val sourceText = TextNormalizer.joinLines(lines.map(TextLine::text))
        // Skips noise such as page numbers, clocks and stray symbols, as well as web addresses.
        if (sourceText.count(Char::isLetter) < minLetters) return null
        if (looksLikeAddress(sourceText)) return null

        val isAllCaps = TextNormalizer.isAllCaps(sourceText)
        return TextBlock(
            lines = lines,
            box = lines.map(TextLine::box).reduce { acc, box -> acc.union(box) },
            sourceText = sourceText,
            text = TextNormalizer.repairContractions(
                if (isAllCaps) TextNormalizer.toSentenceCase(sourceText) else sourceText,
            ),
            isAllCaps = isAllCaps,
        )
    }

    /** Short text containing a web address, e.g. a browser address bar (possibly with icon noise). */
    private fun looksLikeAddress(text: String): Boolean {
        val tokens = text.split(' ')
        return tokens.size <= MAX_ADDRESS_BLOCK_TOKENS && tokens.any(URL_PATTERN::matches)
    }

    private companion object {
        const val DEFAULT_MIN_LETTERS = 2
        const val MAX_ADDRESS_BLOCK_TOKENS = 3

        /**
         * A token such as "https://…", "www.…" or "example.com/path". OCR sometimes reads a dot as a
         * comma ("example.co,jp"), so commas are accepted between parts once a dot is present.
         */
        val URL_PATTERN = Regex(
            "(?:https?://|www\\.)\\S+|(?=[^.]*\\.)[\\w-]+(?:[.,][\\w-]+)+(?:/\\S*)?",
            RegexOption.IGNORE_CASE,
        )
    }
}
