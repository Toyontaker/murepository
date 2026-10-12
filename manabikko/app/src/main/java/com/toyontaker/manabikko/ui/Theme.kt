package com.toyontaker.manabikko.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

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
    MaterialTheme(
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
