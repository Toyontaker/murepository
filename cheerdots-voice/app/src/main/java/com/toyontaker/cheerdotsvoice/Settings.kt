package com.toyontaker.cheerdotsvoice

import android.content.Context

class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    enum class Engine { ANDROID, GEMINI }

    var deviceAddress: String?
        get() = prefs.getString(KEY_DEVICE, null)
        set(value) = prefs.edit().putString(KEY_DEVICE, value).apply()

    var language: String
        get() = prefs.getString(KEY_LANGUAGE, "ja-JP") ?: "ja-JP"
        set(value) = prefs.edit().putString(KEY_LANGUAGE, value).apply()

    var preferOffline: Boolean
        get() = prefs.getBoolean(KEY_OFFLINE, false)
        set(value) = prefs.edit().putBoolean(KEY_OFFLINE, value).apply()

    var engine: Engine
        get() = runCatching { Engine.valueOf(prefs.getString(KEY_ENGINE, null) ?: "") }.getOrDefault(Engine.ANDROID)
        set(value) = prefs.edit().putString(KEY_ENGINE, value.name).apply()

    /** Gemini API key; stored in app-private preferences. */
    var geminiApiKey: String
        get() = prefs.getString(KEY_GEMINI_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_GEMINI_KEY, value.trim()).apply()

    /** Gemini "smart" transcription: removes fillers and self-corrections. */
    var geminiSmart: Boolean
        get() = prefs.getBoolean(KEY_GEMINI_SMART, true)
        set(value) = prefs.edit().putBoolean(KEY_GEMINI_SMART, value).apply()

    private companion object {
        const val KEY_DEVICE = "device_address"
        const val KEY_LANGUAGE = "language"
        const val KEY_OFFLINE = "prefer_offline"
        const val KEY_ENGINE = "engine"
        const val KEY_GEMINI_KEY = "gemini_api_key"
        const val KEY_GEMINI_SMART = "gemini_smart"
    }
}
