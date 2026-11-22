package com.scanread.translate.opusmt

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.scanread.translate.TranslationEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.FloatBuffer
import java.nio.LongBuffer

/**
 * [TranslationEngine] running an Opus-MT (Marian) model with ONNX Runtime.
 *
 * Sentences are translated in batches with greedy decoding and a key/value cache: the encoder
 * runs once per batch, then the merged decoder produces one token per step for every sentence.
 * Models are loaded lazily on first use, off the main thread.
 */
class OpusMtTranslationEngine(
    private val bundle: OpusMtBundle,
    private val threads: Int = defaultThreadCount(),
) : TranslationEngine {

    private class Model(
        val encoder: OrtSession,
        val decoder: OrtSession,
        val tokenizer: SentencePieceTokenizer,
        val config: OpusMtBundle.Config,
    )

    private val environment = OrtEnvironment.getEnvironment()
    private val mutex = Mutex()
    private var model: Model? = null

    override suspend fun translate(texts: List<String>): List<String> = withContext(Dispatchers.Default) {
        mutex.withLock {
            val loaded = model ?: load().also { model = it }
            // Sorting by length keeps padding (wasted work) small within a batch.
            val order = texts.indices.sortedBy { texts[it].length }
            val results = arrayOfNulls<String>(texts.size)
            for (chunk in order.chunked(BATCH_SIZE)) {
                translateBatch(loaded, chunk.map(texts::get)).forEachIndexed { i, text -> results[chunk[i]] = text }
            }
            results.map { it.orEmpty() }
        }
    }

    override fun close() {
        model?.let {
            it.encoder.close()
            it.decoder.close()
        }
        model = null
    }

    private fun load(): Model {
        val config = bundle.loadConfig()
        val options = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(threads)
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        }
        return Model(
            encoder = environment.createSession(bundle.encoderFile.path, options),
            decoder = environment.createSession(bundle.decoderFile.path, options),
            tokenizer = SentencePieceTokenizer.load(bundle.sourcePiecesFile, bundle.vocabularyFile, config.unknownTokenId),
            config = config,
        )
    }

    private fun translateBatch(model: Model, texts: List<String>): List<String> {
        val config = model.config
        val maxInput = config.maxPositions / 2
        val inputs = texts.map { model.tokenizer.encode(it).take(maxInput - 1) + config.eosTokenId }
        val batch = inputs.size
        val inputLength = inputs.maxOf { it.size }

        val inputIds = LongArray(batch * inputLength) { config.padTokenId.toLong() }
        val attentionMask = LongArray(batch * inputLength)
        inputs.forEachIndexed { row, ids ->
            ids.forEachIndexed { column, id ->
                inputIds[row * inputLength + column] = id.toLong()
                attentionMask[row * inputLength + column] = 1
            }
        }

        val shape = longArrayOf(batch.toLong(), inputLength.toLong())
        return OnnxTensor.createTensor(environment, LongBuffer.wrap(inputIds), shape).use { idsTensor ->
            OnnxTensor.createTensor(environment, LongBuffer.wrap(attentionMask), shape).use { maskTensor ->
                model.encoder.run(mapOf(INPUT_IDS to idsTensor, ATTENTION_MASK to maskTensor)).use { encoded ->
                    val hiddenStates = encoded[0] as OnnxTensor
                    val maxNewTokens = minOf(config.maxPositions, inputLength * 2 + EXTRA_OUTPUT_TOKENS)
                    decodeGreedy(model, hiddenStates, maskTensor, batch, maxNewTokens)
                        // The unknown token would otherwise appear as "<unk>" in the translation.
                        .map { tokens -> model.tokenizer.decode(tokens.filter { it != config.unknownTokenId }) }
                }
            }
        }
    }

    private fun decodeGreedy(
        model: Model,
        hiddenStates: OnnxTensor,
        encoderMask: OnnxTensor,
        batch: Int,
        maxNewTokens: Int,
    ): List<List<Int>> {
        val config = model.config
        val outputs = List(batch) { mutableListOf<Int>() }
        val finished = BooleanArray(batch)
        var nextTokens = LongArray(batch) { config.decoderStartTokenId.toLong() }

        val emptyCacheShape = longArrayOf(batch.toLong(), config.attentionHeads.toLong(), 0, config.headDim.toLong())
        val emptyCache = OnnxTensor.createTensor(environment, FloatBuffer.allocate(0), emptyCacheShape)
        // The first step's result holds the encoder (cross-attention) cache for all later steps.
        var firstStep: OrtSession.Result? = null
        var previousStep: OrtSession.Result? = null

        try {
            for (step in 0 until maxNewTokens) {
                val tokenTensor = OnnxTensor.createTensor(
                    environment,
                    LongBuffer.wrap(nextTokens),
                    longArrayOf(batch.toLong(), 1),
                )
                val useCacheTensor = OnnxTensor.createTensor(environment, booleanArrayOf(step > 0))
                val feeds = HashMap<String, OnnxTensor>()
                feeds[INPUT_IDS] = tokenTensor
                feeds[ENCODER_ATTENTION_MASK] = encoderMask
                feeds[ENCODER_HIDDEN_STATES] = hiddenStates
                feeds[USE_CACHE_BRANCH] = useCacheTensor
                for (layer in 0 until config.decoderLayers) {
                    for (kind in KEY_VALUE) {
                        feeds["past_key_values.$layer.decoder.$kind"] =
                            previousStep?.cached("present.$layer.decoder.$kind") ?: emptyCache
                        feeds["past_key_values.$layer.encoder.$kind"] =
                            firstStep?.cached("present.$layer.encoder.$kind") ?: emptyCache
                    }
                }

                val result = try {
                    model.decoder.run(feeds.filterKeys { it in model.decoder.inputNames })
                } finally {
                    tokenTensor.close()
                    useCacheTensor.close()
                }

                nextTokens = pickNextTokens(result.cached(LOGITS)!!, batch, config.padTokenId)

                if (firstStep == null) firstStep = result else if (previousStep !== firstStep) previousStep?.close()
                previousStep = result

                for (row in 0 until batch) {
                    if (finished[row]) continue
                    val token = nextTokens[row].toInt()
                    if (token == config.eosTokenId) finished[row] = true else outputs[row] += token
                }
                if (finished.all { it }) break
            }
        } finally {
            if (previousStep !== firstStep) previousStep?.close()
            firstStep?.close()
            emptyCache.close()
        }
        return outputs
    }

    /** Most likely next token per row; the padding token is never chosen. */
    private fun pickNextTokens(logits: OnnxTensor, batch: Int, padTokenId: Int): LongArray {
        val values = logits.floatBuffer
        val vocabularySize = values.capacity() / batch
        return LongArray(batch) { row ->
            val offset = row * vocabularySize
            var bestToken = 0
            var bestScore = Float.NEGATIVE_INFINITY
            for (token in 0 until vocabularySize) {
                if (token == padTokenId) continue
                val score = values.get(offset + token)
                if (score > bestScore) {
                    bestScore = score
                    bestToken = token
                }
            }
            bestToken.toLong()
        }
    }

    private fun OrtSession.Result.cached(name: String): OnnxTensor? = get(name).orElse(null) as OnnxTensor?

    private companion object {
        const val BATCH_SIZE = 16
        const val EXTRA_OUTPUT_TOKENS = 10
        const val MAX_THREADS = 4

        const val INPUT_IDS = "input_ids"
        const val ATTENTION_MASK = "attention_mask"
        const val ENCODER_ATTENTION_MASK = "encoder_attention_mask"
        const val ENCODER_HIDDEN_STATES = "encoder_hidden_states"
        const val USE_CACHE_BRANCH = "use_cache_branch"
        const val LOGITS = "logits"
        val KEY_VALUE = listOf("key", "value")

        fun defaultThreadCount(): Int = Runtime.getRuntime().availableProcessors().coerceIn(1, MAX_THREADS)
    }
}
