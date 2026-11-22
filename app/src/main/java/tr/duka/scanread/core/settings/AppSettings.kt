package tr.duka.scanread.core.settings

/**
 * User preferences.
 *
 * @property sourceLanguage language read from the screen (ISO 639-1); `null` = default.
 * @property targetLanguage language translated into; `null` = the device language.
 * @property buttonSize size of the floating button.
 * @property buttonOpacity floating button opacity, 0.3..1.
 * @property textScale multiplier for translated text size, 0.8..1.3.
 * @property skipSoundEffects leave sound effects and drawn lettering untranslated.
 * @property onboardingDone the first-run guide was completed.
 */
data class AppSettings(
    val sourceLanguage: String? = null,
    val targetLanguage: String? = null,
    val buttonSize: ButtonSize = ButtonSize.MEDIUM,
    val buttonOpacity: Float = DEFAULT_BUTTON_OPACITY,
    val textScale: Float = DEFAULT_TEXT_SCALE,
    val skipSoundEffects: Boolean = true,
    val onboardingDone: Boolean = false,
) {
    enum class ButtonSize(val dp: Int) { SMALL(44), MEDIUM(52), LARGE(64) }

    companion object {
        const val DEFAULT_BUTTON_OPACITY = 1f
        const val MIN_BUTTON_OPACITY = 0.3f
        const val DEFAULT_TEXT_SCALE = 1f
        const val MIN_TEXT_SCALE = 0.8f
        const val MAX_TEXT_SCALE = 1.3f
    }
}
