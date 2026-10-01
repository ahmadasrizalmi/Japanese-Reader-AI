package com.japanesereader.ai.data.local

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("komorebi_prefs", Context.MODE_PRIVATE)

    companion object {
        const val KEY_USER_NAME = "user_name"
        const val KEY_DEEPSEEK_API_KEY = "deepseek_api_key"
        const val KEY_BACKEND_URL = "backend_url"
        const val KEY_FURIGANA_MODE = "furigana_mode"
        const val KEY_FONT_STYLE = "font_style"
        const val KEY_TTS_SPEED = "tts_speed"
        const val KEY_TONE = "deepseek_tone"
        const val DEFAULT_BACKEND_URL = "https://komorebi-reader-api.hannabi3108.workers.dev"
    }

    var userName: String
        get() = prefs.getString(KEY_USER_NAME, "Pembaca Komorebi") ?: "Pembaca Komorebi"
        set(value) = prefs.edit().putString(KEY_USER_NAME, value).apply()

    var deepseekApiKey: String
        get() = prefs.getString(KEY_DEEPSEEK_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_DEEPSEEK_API_KEY, value).apply()

    var backendUrl: String
        get() = prefs.getString(KEY_BACKEND_URL, DEFAULT_BACKEND_URL) ?: DEFAULT_BACKEND_URL
        set(value) = prefs.edit().putString(KEY_BACKEND_URL, value).apply()

    var furiganaMode: String
        get() = prefs.getString(KEY_FURIGANA_MODE, "always") ?: "always"
        set(value) = prefs.edit().putString(KEY_FURIGANA_MODE, value).apply()

    var kanjiFontStyle: String
        get() = prefs.getString(KEY_FONT_STYLE, "mincho") ?: "mincho"
        set(value) = prefs.edit().putString(KEY_FONT_STYLE, value).apply()

    var ttsSpeed: Float
        get() = prefs.getFloat(KEY_TTS_SPEED, 1.0f)
        set(value) = prefs.edit().putFloat(KEY_TTS_SPEED, value).apply()

    var deepseekTone: String
        get() = prefs.getString(KEY_TONE, "colloquial") ?: "colloquial"
        set(value) = prefs.edit().putString(KEY_TONE, value).apply()
}
