package com.toyontaker.cheerdotsvoice

import android.content.Context

class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var deviceAddress: String?
        get() = prefs.getString(KEY_DEVICE, null)
        set(value) = prefs.edit().putString(KEY_DEVICE, value).apply()

    var language: String
        get() = prefs.getString(KEY_LANGUAGE, "ja-JP") ?: "ja-JP"
        set(value) = prefs.edit().putString(KEY_LANGUAGE, value).apply()

    var preferOffline: Boolean
        get() = prefs.getBoolean(KEY_OFFLINE, false)
        set(value) = prefs.edit().putBoolean(KEY_OFFLINE, value).apply()

    private companion object {
        const val KEY_DEVICE = "device_address"
        const val KEY_LANGUAGE = "language"
        const val KEY_OFFLINE = "prefer_offline"
    }
}
