package com.scanread.core.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Persists [AppSettings] with Jetpack DataStore. */
class SettingsRepository(context: Context) {

    private val store = context.applicationContext.dataStore

    val settings: Flow<AppSettings> = store.data.map { it.toSettings() }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setLanguages(source: String, target: String) = store.edit {
        it[SOURCE] = source
        it[TARGET] = target
    }

    suspend fun setButtonSize(size: AppSettings.ButtonSize) = store.edit { it[BUTTON_SIZE] = size.name }

    suspend fun setButtonOpacity(opacity: Float) = store.edit {
        it[BUTTON_OPACITY] = opacity.coerceIn(AppSettings.MIN_BUTTON_OPACITY, 1f)
    }

    suspend fun setTextScale(scale: Float) = store.edit {
        it[TEXT_SCALE] = scale.coerceIn(AppSettings.MIN_TEXT_SCALE, AppSettings.MAX_TEXT_SCALE)
    }

    suspend fun setSkipSoundEffects(skip: Boolean) = store.edit { it[SKIP_SOUND_EFFECTS] = skip }

    suspend fun setOnboardingDone() = store.edit { it[ONBOARDING_DONE] = true }

    private fun Preferences.toSettings() = AppSettings(
        sourceLanguage = this[SOURCE],
        targetLanguage = this[TARGET],
        buttonSize = this[BUTTON_SIZE]
            ?.let { name -> AppSettings.ButtonSize.entries.firstOrNull { it.name == name } }
            ?: AppSettings.ButtonSize.MEDIUM,
        buttonOpacity = this[BUTTON_OPACITY] ?: AppSettings.DEFAULT_BUTTON_OPACITY,
        textScale = this[TEXT_SCALE] ?: AppSettings.DEFAULT_TEXT_SCALE,
        skipSoundEffects = this[SKIP_SOUND_EFFECTS] ?: true,
        onboardingDone = this[ONBOARDING_DONE] ?: false,
    )

    private companion object {
        val SOURCE = stringPreferencesKey("source_language")
        val TARGET = stringPreferencesKey("target_language")
        val BUTTON_SIZE = stringPreferencesKey("button_size")
        val BUTTON_OPACITY = floatPreferencesKey("button_opacity")
        val TEXT_SCALE = floatPreferencesKey("text_scale")
        val SKIP_SOUND_EFFECTS = booleanPreferencesKey("skip_sound_effects")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
    }
}
