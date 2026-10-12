package com.toyontaker.manabikko.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.em
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

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

/** この 大きさ(dp)より 小さい 画面では、画面ぜんたいを すこし 縮小して この 大きさとして レイアウトする。 */
private const val MIN_SHORT_SIDE_DP = 380f
private const val MIN_LONG_SIDE_DP = 680f

/** 端末の 文字サイズ設定の 上限(もともと 大きな 文字を つかっているので、これ以上は ボタンから はみだす)。 */
private const val MAX_FONT_SCALE = 1.15f

@Composable
fun ManabikkoTheme(content: @Composable () -> Unit) {
    // ウィンドウの 大きさ(分割画面でも ただしく とれる)
    val window = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    val widthDp = window.width / density.density
    val heightDp = window.height / density.density
    val scale = if (window.width == 0 || window.height == 0) {
        1f
    } else {
        minOf(1f, minOf(widthDp, heightDp) / MIN_SHORT_SIDE_DP, maxOf(widthDp, heightDp) / MIN_LONG_SIDE_DP)
    }
    val appDensity = Density(density.density * scale, density.fontScale.coerceAtMost(MAX_FONT_SCALE))
    CompositionLocalProvider(LocalDensity provides appDensity) {
        BaseTheme(content)
    }
}

@Composable
private fun BaseTheme(content: @Composable () -> Unit) {
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
