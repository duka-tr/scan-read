package com.scanread.translate

import android.content.Context
import com.scanread.translate.mlkit.MlKitTranslationEngine
import com.scanread.translate.opusmt.OpusMtBundle
import com.scanread.translate.opusmt.OpusMtTranslationEngine

/** Chooses the translation engine: the high-quality Opus-MT model when installed, ML Kit otherwise. */
object TranslationBackends {

    enum class Backend { OPUS_MT, ML_KIT }

    fun preferred(context: Context, pair: LanguagePair): Backend =
        if (OpusMtBundle.installed(context.filesDir, pair) != null) Backend.OPUS_MT else Backend.ML_KIT

    fun createEngine(context: Context, pair: LanguagePair): Pair<Backend, TranslationEngine> {
        val bundle = OpusMtBundle.installed(context.filesDir, pair)
        return if (bundle != null) {
            Backend.OPUS_MT to OpusMtTranslationEngine(bundle)
        } else {
            Backend.ML_KIT to MlKitTranslationEngine(pair)
        }
    }
}
