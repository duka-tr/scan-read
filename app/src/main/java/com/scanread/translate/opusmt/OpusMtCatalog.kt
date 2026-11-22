package com.scanread.translate.opusmt

import com.scanread.translate.LanguagePair

/**
 * Downloadable Opus-MT bundles, published as release assets (see `tools/opus-mt/README.md`).
 * Sizes and SHA-256 digests are pinned here so downloads are verified before use.
 */
object OpusMtCatalog {

    data class ModelFile(val name: String, val size: Long, val sha256: String)

    data class Entry(
        /** Directory name under `files/models/`. */
        val bundleName: String,
        /** Release that hosts the files. */
        val releaseTag: String,
        val files: List<ModelFile>,
    ) {
        val totalSize: Long get() = files.sumOf { it.size }
    }

    private val ENTRIES = mapOf(
        LanguagePair("en", "tr") to Entry(
            bundleName = "opus-mt-en-tr",
            releaseTag = "model-opus-mt-en-tr-v1",
            files = listOf(
                ModelFile("encoder.onnx", 84_806_649, "72c32a38532e61050d6992a647fb8d7c6cfa787d9ad4b118a02374fd436cf4d9"),
                ModelFile("decoder.onnx", 137_947_449, "d8b36ce2aea33023afe486caf0e34661ccf71a4e74f90b66d0cc5889b3ac1eee"),
                ModelFile("vocab.txt", 595_686, "f9a5c9bd473a6e4847dbcbd32eee482be7ad52cb17bedbef32ff09097c15d60d"),
                ModelFile("source_pieces.tsv", 1_158_144, "c125c7e8df750c30515ffd741be4cf9398aa94ae5ae2a54935d81bb324c445b0"),
                ModelFile("model.properties", 171, "7aaf082c0e4d9d2c2329784d1db8992a8ea001944ebcf8474765f1799d0bfcd2"),
                ModelFile("ATTRIBUTION.txt", 306, "dfec952b6b46c5b62edac41401596b7b5c996c1e3c624d0984e56b25a5cf95c2"),
            ),
        ),
    )

    fun entry(pair: LanguagePair): Entry? = ENTRIES[pair]

    /** Download URL of [file] below the releases base URL (`…/releases/download`). */
    fun url(baseUrl: String, entry: Entry, file: ModelFile): String =
        "${baseUrl.trimEnd('/')}/${entry.releaseTag}/${file.name}"
}
