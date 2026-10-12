package com.toyontaker.manabikko.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.toyontaker.manabikko.LocalSpeaker
import com.toyontaker.manabikko.data.Hiragana
import kotlinx.coroutines.delay

private class Line(val color: Color) {
    val points = mutableStateListOf<Offset>()
}

/** なぞりがき:うすく かいてある ひらがなを ゆびで なぞる。 */
@Composable
fun TracingScreen(onHome: () -> Unit) {
    val speaker = LocalSpeaker.current
    var index by rememberSaveable { mutableIntStateOf(0) }
    val kana = Hiragana.all[index]
    val lines = remember(index) { mutableStateListOf<Line>() }
    var praised by remember(index) { mutableStateOf(false) }
    val measurer = rememberTextMeasurer()

    LaunchedEffect(index, speaker.ready) { speaker.speak("「${kana.char}」を なぞってみよう") }
    LaunchedEffect(praised) {
        if (praised) {
            delay(2500)
            praised = false
        }
    }

    fun move(step: Int) {
        index = (index + step + Hiragana.all.size) % Hiragana.all.size
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TopBar("なぞりがき", Palette.Blue, onHome)

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clip(RoundedCornerShape(36.dp))
                    .background(Color.White),
            ) {
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(index) {
                            detectDragGestures(
                                onDragStart = { start ->
                                    val line = Line(Palette.pens[lines.size % Palette.pens.size])
                                    line.points += start
                                    lines += line
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    lines.lastOrNull()?.points?.add(change.position)
                                },
                            )
                        },
                ) {
                    // 十字の ガイド線
                    val dash = Color(0xFFF0E6D6)
                    drawLine(dash, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), 4f)
                    drawLine(dash, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 4f)

                    // おてほんの文字(うすい色)
                    val fontPx = minOf(size.width, size.height) * 0.78f
                    val layout = measurer.measure(
                        kana.char,
                        TextStyle(fontSize = (fontPx / density).sp, fontWeight = FontWeight.Bold, color = Palette.Faint),
                    )
                    drawText(
                        layout,
                        topLeft = Offset(
                            (size.width - layout.size.width) / 2,
                            (size.height - layout.size.height) / 2,
                        ),
                    )

                    // こどもが かいた線
                    val strokeWidth = minOf(size.width, size.height) * 0.05f
                    lines.forEach { line ->
                        val pts = line.points
                        if (pts.size == 1) {
                            drawCircle(line.color, strokeWidth / 2, pts[0])
                        } else if (pts.size > 1) {
                            val path = Path().apply {
                                moveTo(pts[0].x, pts[0].y)
                                for (p in pts.drop(1)) lineTo(p.x, p.y)
                            }
                            drawPath(path, line.color, style = Stroke(strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                    }
                }
                Text(
                    "${kana.emoji} ${kana.word}",
                    fontSize = 28.sp,
                    color = Palette.Ink,
                    modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
                )
                SoundButton(
                    onClick = { speaker.speak("${kana.char}。 ${kana.word}") },
                    modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
                )
            }

            Row(
                Modifier.fillMaxWidth().height(100.dp).padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ToolButton("◀", Palette.Yellow) { move(-1) }
                ToolButton("🧽", Palette.Pink, Modifier.weight(1f), label = "けす") { lines.clear() }
                ToolButton("💮", Palette.Green, Modifier.weight(1f), label = "できた") {
                    if (lines.isNotEmpty()) {
                        praised = true
                        speaker.speak("じょうずに かけたね!")
                    } else {
                        speaker.speak("ゆびで なぞってね")
                    }
                }
                ToolButton("▶", Palette.Yellow) { move(1) }
            }
        }
        Celebration(visible = praised, emoji = "💮", message = "じょうず!")
    }
}

@Composable
private fun ToolButton(icon: String, color: Color, modifier: Modifier = Modifier, label: String? = null, onClick: () -> Unit) {
    BouncyBox(color = color, onClick = onClick, modifier = modifier.height(76.dp).then(if (label == null) Modifier.size(64.dp, 76.dp) else Modifier), corner = 38.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(icon, fontSize = 28.sp, color = Palette.Ink)
            if (label != null) Text(label, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Palette.Ink, maxLines = 1, softWrap = false, modifier = Modifier.padding(start = 4.dp))
        }
    }
}
