package com.toyontaker.manabikko.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.em

/** おとな向けの 設定ボタンの ながおし 時間。 */
const val SETTINGS_HOLD_MILLIS = 1500L

/** かずの 数字ボタンの ながおし 時間。 */
const val NUMBER_HOLD_MILLIS = 1000L

object Palette {
    val Background = Color(0xFFFFF8E7)
    val Ink = Color(0xFF5D4037)
    val Faint = Color(0xFFE8DCC8)
    val Red = Color(0xFFFF8A80)
    val Orange = Color(0xFFFFB74D)
    val Yellow = Color(0xFFFFE082)
    val Green = Color(0xFFA5D6A7)
    val Blue = Color(0xFF90CAF9)
    val Purple = Color(0xFFCE93D8)
    val Pink = Color(0xFFF8BBD0)

    /** タイルなどを順番にぬりわける色。 */
    val rainbow = listOf(Red, Orange, Yellow, Green, Blue, Purple, Pink)

    /** なぞりがき の線の色(こいめ)。 */
    val pens = listOf(
        Color(0xFFE53935), Color(0xFFFB8C00), Color(0xFF43A047),
        Color(0xFF1E88E5), Color(0xFF8E24AA), Color(0xFFD81B60),
    )
}

@Composable
fun ManabikkoTheme(content: @Composable () -> Unit) {
    // 標準の 行の高さは 固定(24sp など)で、大きな文字を 2行に すると 重なるので、文字の大きさに 合わせる
    val base = Typography()
    val lineHeight = 1.3.em
    MaterialTheme(
        typography = base.copy(
            bodyLarge = base.bodyLarge.copy(lineHeight = lineHeight),
            bodyMedium = base.bodyMedium.copy(lineHeight = lineHeight),
            bodySmall = base.bodySmall.copy(lineHeight = lineHeight),
        ),
        colorScheme = lightColorScheme(
            primary = Palette.Orange,
            background = Palette.Background,
            surface = Palette.Background,
            onBackground = Palette.Ink,
            onSurface = Palette.Ink,
        ),
        content = content,
    )
}
