package com.example.core

import android.content.Context
import android.content.SharedPreferences
import com.example.BuildConfig

class Prefs(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("mimi_assistant_prefs", Context.MODE_PRIVATE)

    init {
        // One-time migration of model ids: the draft id "animated_mimi" and the old 1-triangle
        // placeholders now show the rigged anime_mimi model. Other choices are kept.
        if (!prefs.getBoolean("char_model_v3_migrated", false)) {
            val saved = prefs.getString("char_model", null)
            val editor = prefs.edit()
            if (saved == "animated_mimi" || saved == "cyber_mimi" || saved == "chibi_mimi") {
                editor.putString("char_model", "anime_mimi")
            }
            editor.putBoolean("char_model_v3_migrated", true).apply()
        }
    }

    var characterName: String
        get() = prefs.getString("char_name", "Mimi") ?: "Mimi"
        set(value) = prefs.edit().putString("char_name", value).apply()

    var characterModel: String
        get() = prefs.getString("char_model", "anime_mimi") ?: "anime_mimi"
        set(value) = prefs.edit().putString("char_model", value).apply()

    var voicePitch: Float
        get() = prefs.getFloat("voice_pitch", 1.05f)
        set(value) = prefs.edit().putFloat("voice_pitch", value).apply()

    var voiceSpeed: Float
        get() = prefs.getFloat("voice_speed", 0.94f)
        set(value) = prefs.edit().putFloat("voice_speed", value).apply()

    var voiceLanguage: String
        get() = prefs.getString("voice_language", "bn-BD") ?: "bn-BD"
        set(value) = prefs.edit().putString("voice_language", value).apply()

    var wakeWord: String
        get() = prefs.getString("wake_word", "hey mimi") ?: "hey mimi"
        set(value) = prefs.edit().putString("wake_word", value).apply()

    var isWakeWordEnabled: Boolean
        get() = prefs.getBoolean("wake_word_enabled", false)
        set(value) = prefs.edit().putBoolean("wake_word_enabled", value).apply()

    var isVocalResponseEnabled: Boolean
        get() = prefs.getBoolean("vocal_response_enabled", true)
        set(value) = prefs.edit().putBoolean("vocal_response_enabled", value).apply()

    var geminiApiKey: String
        get() {
            val saved = prefs.getString("gemini_api_key", "") ?: ""
            if (saved.isNotEmpty()) return saved
            return try {
                BuildConfig.GEMINI_API_KEY
            } catch (e: Exception) {
                ""
            }
        }
        set(value) = prefs.edit().putString("gemini_api_key", value).apply()

    var geminiModel: String
        get() = prefs.getString("gemini_model", "gemini-3.5-flash") ?: "gemini-3.5-flash"
        set(value) = prefs.edit().putString("gemini_model", value).apply()

    var themeMode: String
        get() = prefs.getString("theme_mode", "amoled") ?: "amoled"
        set(value) = prefs.edit().putString("theme_mode", value).apply()

    var accentColor: String
        get() = prefs.getString("accent_color", "pink") ?: "pink"
        set(value) = prefs.edit().putString("accent_color", value).apply()

    var isOnboardingCompleted: Boolean
        get() = prefs.getBoolean("onboarding_completed", false)
        set(value) = prefs.edit().putBoolean("onboarding_completed", value).apply()

    var affectionScore: Int
        get() = prefs.getInt("affection_score", 45)
        set(value) = prefs.edit().putInt("affection_score", value.coerceIn(0, 100)).apply()

    var currentMood: String
        get() = prefs.getString("current_mood", "happy") ?: "happy"
        set(value) = prefs.edit().putString("current_mood", value).apply()
}
