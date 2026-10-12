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
import androidx.compose.foundation.layout.size
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

/** say は 読み上げ用。 */
private data class MenuItem(val screen: Screen, val emoji: String, val label: String, val color: Color, val say: String = label)

private val menu = listOf(
    MenuItem(Screen.Hiragana, "あ", "ひらがな", Palette.Red),
    MenuItem(Screen.Katakana, "ア", "カタカナ", Palette.Orange),
    MenuItem(Screen.Counting, "🍎", "かず", Palette.Green, "数"),
    MenuItem(Screen.Tracing, "✏️", "なぞりがき", Palette.Blue, "なぞり書き"),
    MenuItem(Screen.Quiz, "❓", "クイズ", Palette.Purple),
)

/** おとなが 3びょう おしつづけたときだけ 反応する(こどもの うっかり タップ よけ)。 */
private const val LOCK_HOLD_MILLIS = 3000L

@Composable
fun HomeScreen(
    onOpen: (Screen) -> Unit,
    locked: Boolean = false,
    onToggleLock: () -> Unit = {},
    onAbout: () -> Unit = {},
) {
    val speaker = LocalSpeaker.current
    LaunchedEffect(speaker.ready) { speaker.speak("何して遊ぶ?") }
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
            // 🔒 ・ タイトル ・ ℹ️ を かさならないように ならべる
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    if (locked) Text("🔒", fontSize = 22.sp)
                }
                Row(
                    Modifier
                        .weight(1f)
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
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text("🐣", fontSize = 36.sp, modifier = Modifier.graphicsLayer { translationY = bob })
                    FitText(
                        "まなびっこ",
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.weight(1f, fill = false).padding(horizontal = 8.dp),
                    )
                    Text("🌈", fontSize = 36.sp, modifier = Modifier.graphicsLayer { translationY = -bob })
                }
                // おとな向け:アプリについて(ながおしで ひらく)
                BouncyBox(
                    color = Palette.Background,
                    onClick = onAbout,
                    longPressMillis = SETTINGS_HOLD_MILLIS,
                    label = "このアプリについて(ながおし)",
                    modifier = Modifier.size(48.dp),
                    corner = 24.dp,
                ) {
                    Text("ℹ️", fontSize = 22.sp)
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
                                    speaker.speak(item.say)
                                    onOpen(item.screen)
                                },
                                modifier = Modifier.weight(1f).fillMaxSize(),
                                corner = 40.dp,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(item.emoji, fontSize = 64.sp, fontWeight = FontWeight.Black, color = Palette.Ink)
                                    FitText(item.label, fontSize = 28.sp, modifier = Modifier.padding(horizontal = 8.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
