package com.scanread.translate.opusmt

import com.scanread.translate.LanguagePair
import java.io.File
import java.util.Properties

/**
 * An on-device Opus-MT model as produced by `tools/opus-mt/prepare_model.py`.
 */
class OpusMtBundle(val directory: File) {

    val encoderFile get() = File(directory, ENCODER)
    val decoderFile get() = File(directory, DECODER)
    val vocabularyFile get() = File(directory, VOCABULARY)
    val sourcePiecesFile get() = File(directory, SOURCE_PIECES)
    val propertiesFile get() = File(directory, PROPERTIES)

    val isComplete: Boolean get() = REQUIRED_FILES.all { File(directory, it).isFile }

    fun loadConfig(): Config {
        val properties = Properties().apply { propertiesFile.inputStream().use(::load) }
        fun int(key: String) = properties.getProperty(key)?.toInt() ?: error("Missing $key in $PROPERTIES")
        return Config(
            eosTokenId = int("eos_token_id"),
            padTokenId = int("pad_token_id"),
            decoderStartTokenId = int("decoder_start_token_id"),
            unknownTokenId = int("unk_token_id"),
            decoderLayers = int("decoder_layers"),
            attentionHeads = int("decoder_attention_heads"),
            headDim = int("head_dim"),
            maxPositions = int("max_position_embeddings"),
        )
    }

    data class Config(
        val eosTokenId: Int,
        val padTokenId: Int,
        val decoderStartTokenId: Int,
        val unknownTokenId: Int,
        val decoderLayers: Int,
        val attentionHeads: Int,
        val headDim: Int,
        val maxPositions: Int,
    )

    companion object {
        const val ENCODER = "encoder.onnx"
        const val DECODER = "decoder.onnx"
        const val VOCABULARY = "vocab.txt"
        const val SOURCE_PIECES = "source_pieces.tsv"
        const val PROPERTIES = "model.properties"
        private val REQUIRED_FILES = listOf(ENCODER, DECODER, VOCABULARY, SOURCE_PIECES, PROPERTIES)

        private const val MODELS_DIRECTORY = "models"

        /** Bundle directory name per supported language pair. */
        private val BUNDLES = mapOf(
            LanguagePair("en", "tr") to "opus-mt-en-tr",
        )

        fun isSupported(pair: LanguagePair): Boolean = pair in BUNDLES

        /** Where the bundle for [pair] lives under [filesDir], or `null` if the pair is not supported. */
        fun locate(filesDir: File, pair: LanguagePair): OpusMtBundle? =
            BUNDLES[pair]?.let { OpusMtBundle(File(File(filesDir, MODELS_DIRECTORY), it)) }

        /** The installed bundle for [pair], or `null` if it is missing or incomplete. */
        fun installed(filesDir: File, pair: LanguagePair): OpusMtBundle? =
            locate(filesDir, pair)?.takeIf { it.isComplete }
    }
}
