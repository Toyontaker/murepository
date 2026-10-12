package com.toyontaker.manabikko.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.toyontaker.manabikko.LocalSpeaker
import com.toyontaker.manabikko.Screen

private data class MenuItem(val screen: Screen, val emoji: String, val label: String, val color: Color)

private val menu = listOf(
    MenuItem(Screen.Hiragana, "あ", "ひらがな", Palette.Red),
    MenuItem(Screen.Counting, "🍎", "かず", Palette.Green),
    MenuItem(Screen.Tracing, "✏️", "なぞりがき", Palette.Blue),
    MenuItem(Screen.Quiz, "❓", "クイズ", Palette.Purple),
)

@Composable
fun HomeScreen(onOpen: (Screen) -> Unit) {
    val speaker = LocalSpeaker.current
    LaunchedEffect(speaker.ready) { speaker.speak("なにで あそぶ?") }

    val bob by rememberInfiniteTransition(label = "bob").animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "bob",
    )

    BoxWithConstraints(Modifier.fillMaxSize().padding(16.dp)) {
        val wide = maxWidth > maxHeight
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🐣", fontSize = 48.sp, modifier = Modifier.graphicsLayer { translationY = bob })
                Text(
                    "まなびっこ",
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Black,
                    color = Palette.Ink,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                Text("🌈", fontSize = 48.sp, modifier = Modifier.graphicsLayer { translationY = -bob })
            }
            Spacer(Modifier.height(16.dp))

            // 縦長なら 2×2、横長なら 1×4 で ならべる
            val rows = if (wide) listOf(menu) else menu.chunked(2)
            Column(
                Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                rows.forEach { row ->
                    Row(
                        Modifier.fillMaxWidth().weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        row.forEach { item ->
                            BouncyBox(
                                color = item.color,
                                onClick = {
                                    speaker.speak(item.label)
                                    onOpen(item.screen)
                                },
                                modifier = Modifier.weight(1f).fillMaxSize(),
                                corner = 40.dp,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(item.emoji, fontSize = 84.sp, fontWeight = FontWeight.Black, color = Color.White)
                                    Text(item.label, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
