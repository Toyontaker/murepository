package com.toyontaker.manabikko

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** おとなが えらんだ 設定(じゅんばん など)を アプリを とじても おぼえておく。 */
@Composable
fun rememberPref(key: String, default: String): MutableState<String> {
    val prefs = LocalContext.current.getSharedPreferences("settings", Context.MODE_PRIVATE)
    return remember(key) {
        object : MutableState<String> {
            private val state = mutableStateOf(prefs.getString(key, default) ?: default)
            override var value: String
                get() = state.value
                set(v) {
                    state.value = v
                    prefs.edit().putString(key, v).apply()
                }
            override fun component1() = value
            override fun component2(): (String) -> Unit = { value = it }
        }
    }
}
