package com.toyontaker.manabikko.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.toyontaker.manabikko.LocalSpeaker
import com.toyontaker.manabikko.Screen
import kotlinx.coroutines.withTimeoutOrNull

private data class MenuItem(val screen: Screen, val emoji: String, val label: String, val color: Color)

private val menu = listOf(
    MenuItem(Screen.Hiragana, "あ", "ひらがな", Palette.Red),
    MenuItem(Screen.Katakana, "ア", "カタカナ", Palette.Orange),
    MenuItem(Screen.Counting, "🍎", "かず", Palette.Green),
    MenuItem(Screen.Tracing, "✏️", "なぞりがき", Palette.Blue),
    MenuItem(Screen.Quiz, "❓", "クイズ", Palette.Purple),
)

/** おとなが 3びょう おしつづけたときだけ 反応する(こどもの うっかり タップ よけ)。 */
private const val LOCK_HOLD_MILLIS = 3000L

@Composable
fun HomeScreen(onOpen: (Screen) -> Unit, locked: Boolean = false, onToggleLock: () -> Unit = {}) {
    val speaker = LocalSpeaker.current
    LaunchedEffect(speaker.ready) { speaker.speak("なにで あそぶ?") }
    val toggle by rememberUpdatedState(onToggleLock)

    val bob by rememberInfiniteTransition(label = "bob").animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "bob",
    )

    BoxWithConstraints(Modifier.fillMaxSize().padding(16.dp)) {
        val wide = maxWidth > maxHeight
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.fillMaxWidth()) {
                Row(
                    Modifier
                        .align(Alignment.Center)
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown()
                                val released = withTimeoutOrNull(LOCK_HOLD_MILLIS) { waitForUpOrCancellation() }
                                if (released == null) {
                                    toggle()
                                    waitForUpOrCancellation()
                                }
                            }
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("🐣", fontSize = 44.sp, modifier = Modifier.graphicsLayer { translationY = bob })
                    Text(
                        "まなびっこ",
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Black,
                        color = Palette.Ink,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    Text("🌈", fontSize = 44.sp, modifier = Modifier.graphicsLayer { translationY = -bob })
                }
                if (locked) {
                    Text("🔒", fontSize = 20.sp, modifier = Modifier.align(Alignment.TopEnd))
                }
            }
            Spacer(Modifier.height(16.dp))

            // 縦長なら 2れつ、横長なら 3れつ で ならべる(あまりは よこいっぱい)
            val rows = menu.chunked(if (wide) 3 else 2)
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
                                    Text(item.emoji, fontSize = 64.sp, fontWeight = FontWeight.Black, color = Color.White)
                                    Text(item.label, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
