package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.model.AssistantLanguage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.jarvisDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "jarvis_preferences"
)

data class UserPreferencesState(
    val language: AssistantLanguage = AssistantLanguage.HINGLISH,
    val ttsEnabled: Boolean = true,
    val memoryConsentGranted: Boolean = false
)

class UserPreferencesRepository(private val context: Context) {
    private object Keys {
        val LANGUAGE = stringPreferencesKey("assistant_language")
        val TTS_ENABLED = booleanPreferencesKey("tts_enabled")
        val MEMORY_CONSENT = booleanPreferencesKey("memory_consent_granted")
    }

    val preferencesFlow: Flow<UserPreferencesState> = context.jarvisDataStore.data.map { prefs ->
        val langName = prefs[Keys.LANGUAGE] ?: AssistantLanguage.HINGLISH.name
        val language = runCatching { AssistantLanguage.valueOf(langName) }
            .getOrDefault(AssistantLanguage.HINGLISH)
        UserPreferencesState(
            language = language,
            ttsEnabled = prefs[Keys.TTS_ENABLED] ?: true,
            memoryConsentGranted = prefs[Keys.MEMORY_CONSENT] ?: false
        )
    }

    suspend fun setLanguage(language: AssistantLanguage) {
        context.jarvisDataStore.edit { prefs ->
            prefs[Keys.LANGUAGE] = language.name
        }
    }

    suspend fun setTtsEnabled(enabled: Boolean) {
        context.jarvisDataStore.edit { prefs ->
            prefs[Keys.TTS_ENABLED] = enabled
        }
    }

    suspend fun setMemoryConsent(granted: Boolean) {
        context.jarvisDataStore.edit { prefs ->
            prefs[Keys.MEMORY_CONSENT] = granted
        }
    }
}
