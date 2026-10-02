package com.toyontaker.cheerdotsvoice

import android.content.Context
import com.toyontaker.cheerdotsvoice.speech.TextRefiner
import org.json.JSONArray

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

    /** Clean up transcripts with an LLM (Gemini). */
    var refineEnabled: Boolean
        get() = prefs.getBoolean(KEY_REFINE, false)
        set(value) = prefs.edit().putBoolean(KEY_REFINE, value).apply()

    var refineModel: String
        get() = prefs.getString(KEY_REFINE_MODEL, null)?.takeIf { it.isNotBlank() } ?: TextRefiner.DEFAULT_MODEL
        set(value) = prefs.edit().putString(KEY_REFINE_MODEL, value.trim()).apply()

    /** System prompt for refinement; blank means the built-in default. */
    var refinePrompt: String
        get() = prefs.getString(KEY_REFINE_PROMPT, null)?.takeIf { it.isNotBlank() } ?: TextRefiner.DEFAULT_SYSTEM_PROMPT
        set(value) = prefs.edit()
            .putString(KEY_REFINE_PROMPT, if (value.trim() == TextRefiner.DEFAULT_SYSTEM_PROMPT) "" else value)
            .apply()

    /** System prompt for the proofread button; blank means the built-in default. */
    var proofreadPrompt: String
        get() = prefs.getString(KEY_PROOFREAD_PROMPT, null)?.takeIf { it.isNotBlank() } ?: TextRefiner.DEFAULT_PROOFREAD_PROMPT
        set(value) = prefs.edit()
            .putString(KEY_PROOFREAD_PROMPT, if (value.trim() == TextRefiner.DEFAULT_PROOFREAD_PROMPT) "" else value)
            .apply()

    /** Send the text already in the input field (before the cursor) as context. */
    var refineUseFieldContext: Boolean
        get() = prefs.getBoolean(KEY_REFINE_CONTEXT, true)
        set(value) = prefs.edit().putBoolean(KEY_REFINE_CONTEXT, value).apply()

    /** How many recent outputs to send as style examples. */
    var refineHistoryCount: Int
        get() = prefs.getInt(KEY_REFINE_HISTORY_COUNT, 10)
        set(value) = prefs.edit().putInt(KEY_REFINE_HISTORY_COUNT, value.coerceIn(0, MAX_HISTORY)).apply()

    /** Free-form notes about the user (vocabulary, preferred style) sent with every request. */
    var userNotes: String
        get() = prefs.getString(KEY_USER_NOTES, "") ?: ""
        set(value) = prefs.edit().putString(KEY_USER_NOTES, value).apply()

    /** Most recent refined outputs, oldest first. */
    fun history(count: Int = refineHistoryCount): List<String> {
        val all = loadHistory()
        return all.takeLast(count.coerceAtMost(all.size))
    }

    fun addHistory(text: String) {
        if (text.isBlank()) return
        val all = (loadHistory() + text.trim()).takeLast(MAX_HISTORY)
        prefs.edit().putString(KEY_HISTORY, JSONArray(all).toString()).apply()
    }

    fun clearHistory() = prefs.edit().remove(KEY_HISTORY).apply()

    val historySize get() = loadHistory().size

    private fun loadHistory(): List<String> = runCatching {
        val array = JSONArray(prefs.getString(KEY_HISTORY, "[]"))
        List(array.length()) { array.getString(it) }
    }.getOrDefault(emptyList())

    private companion object {
        const val MAX_HISTORY = 50
        const val KEY_REFINE = "refine_enabled"
        const val KEY_REFINE_MODEL = "refine_model"
        const val KEY_REFINE_PROMPT = "refine_prompt"
        const val KEY_PROOFREAD_PROMPT = "proofread_prompt"
        const val KEY_REFINE_CONTEXT = "refine_use_context"
        const val KEY_REFINE_HISTORY_COUNT = "refine_history_count"
        const val KEY_USER_NOTES = "user_notes"
        const val KEY_HISTORY = "refine_history"
        const val KEY_DEVICE = "device_address"
        const val KEY_LANGUAGE = "language"
        const val KEY_OFFLINE = "prefer_offline"
        const val KEY_ENGINE = "engine"
        const val KEY_GEMINI_KEY = "gemini_api_key"
        const val KEY_GEMINI_SMART = "gemini_smart"
    }
}
