package com.toyontaker.manabikko.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.toyontaker.manabikko.LocalSpeaker
import com.toyontaker.manabikko.LocalSfx
import com.toyontaker.manabikko.Sfx
import com.toyontaker.manabikko.data.Hiragana
import com.toyontaker.manabikko.data.Katakana
import com.toyontaker.manabikko.trace.Glyph
import com.toyontaker.manabikko.trace.Grade
import com.toyontaker.manabikko.trace.Strokes
import kotlinx.coroutines.delay

private class Line(val color: Color) {
    val points = mutableStateListOf<Offset>()

    fun toFloatArray() = FloatArray(points.size * 2) { i -> if (i % 2 == 0) points[i / 2].x else points[i / 2].y }
}

/** なぞりがき:うすく かいてある 文字を、書き順ガイドを 見ながら ゆびで なぞる。 */
@Composable
fun TracingScreen(onHome: () -> Unit, autoDemo: Boolean = true) {
    val speaker = LocalSpeaker.current
    val sfx = LocalSfx.current
    val context = LocalContext.current
    val density = LocalDensity.current.density
    var useKatakana by rememberSaveable { mutableStateOf(false) }
    val letters = if (useKatakana) Katakana.all else Hiragana.all
    var index by rememberSaveable { mutableIntStateOf(0) }
    val kana = letters[index.coerceIn(0, letters.lastIndex)]
    val lines = remember(kana) { mutableStateListOf<Line>() }
    // さいてんの けっか(null のときは まだ)
    var grade by remember(kana) { mutableStateOf<Grade?>(null) }
    var lastGrade by remember { mutableStateOf(Grade.Good) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val strokeData = remember(kana) { Strokes.get(context, kana.char) }
    val glyph = remember(strokeData, canvasSize) {
        Glyph(strokeData, canvasSize.width.toFloat(), canvasSize.height.toFloat())
    }
    val guide = remember(glyph) { StrokeGuide(glyph, density) }
    val scorer = remember(glyph) { if (canvasSize == IntSize.Zero) null else glyph.scorer() }

    // かきじゅん アニメーション(demoStroke が null の ときは とまっている)
    var demoRequest by remember(kana) { mutableIntStateOf(if (autoDemo) 1 else 0) }
    var demoStroke by remember(kana) { mutableStateOf<Int?>(null) }
    val demoProgress = remember(kana) { Animatable(0f) }

    LaunchedEffect(kana, speaker.ready) { speaker.speak("「${kana.char}」をなぞってみよう") }
    LaunchedEffect(kana, demoRequest, canvasSize) {
        if (demoRequest == 0 || canvasSize == IntSize.Zero) return@LaunchedEffect
        delay(600)
        for (i in strokeData.indices) {
            demoStroke = i
            demoProgress.snapTo(0f)
            // 長い 線ほど ゆっくり(ゆびで かく はやさ くらい)
            val millis = (glyph.length(i) / (glyph.strokeWidth * 9f) * 1000).toInt().coerceIn(500, 1600)
            demoProgress.animateTo(1f, tween(millis, easing = LinearEasing))
            delay(250)
        }
        demoStroke = strokeData.size
        delay(500)
        demoStroke = null
    }
    LaunchedEffect(grade) {
        val g = grade ?: return@LaunchedEffect
        delay(2500)
        // はずれた ときは けして もういちど
        if (g == Grade.Miss) lines.clear()
        grade = null
    }

    fun check() {
        val result = scorer?.score(lines.map { it.toFloatArray() })
        if (lines.isEmpty() || result == null) {
            sfx.play(Sfx.Sound.Pop)
            speaker.speak("指でなぞってね")
            return
        }
        grade = result.grade
        lastGrade = result.grade
        when (result.grade) {
            Grade.Great -> {
                sfx.play(Sfx.Sound.Fanfare)
                speaker.speak("はなまる!とっても上手!")
            }
            Grade.Good -> {
                sfx.play(Sfx.Sound.Correct)
                speaker.speak("上手に書けたね!")
            }
            Grade.Miss -> {
                sfx.play(Sfx.Sound.Wrong)
                speaker.speak("惜しい!", "薄い字の上を、番号の順になぞってみよう")
            }
        }
    }

    fun move(step: Int) {
        index = (index + step + letters.size) % letters.size
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TopBar("なぞりがき", Palette.Blue, onHome) {
                // ひらがな ⇔ カタカナ きりかえ
                BouncyBox(
                    color = if (useKatakana) Palette.Orange else Palette.Red,
                    onClick = {
                        useKatakana = !useKatakana
                        index = 0
                    },
                    modifier = Modifier.size(132.dp, 64.dp),
                    corner = 32.dp,
                ) {
                    Text(if (useKatakana) "ア→あ" else "あ→ア", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
                }
            }

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
                        .onSizeChanged { canvasSize = it }
                        .pointerInput(kana) {
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

                    // おてほん と 書き順ガイド(つぎに かく 線 = かいた 線の 数 ばんめ)
                    drawIntoCanvas { guide.draw(it.nativeCanvas, lines.size, demoStroke, demoProgress.value) }

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
                Column(
                    Modifier.align(Alignment.TopEnd).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    SoundButton(onClick = { speaker.speak(kana.char, kana.say) })
                    // かきじゅんを もういちど 見る
                    BouncyBox(
                        color = Palette.Orange,
                        onClick = {
                            lines.clear()
                            demoRequest++
                            speaker.speak("書き順を見てね")
                        },
                        modifier = Modifier.size(72.dp),
                        corner = 36.dp,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("✍️", fontSize = 26.sp)
                            Text("じゅん", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
                        }
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().height(100.dp).padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ToolButton("◀", Palette.Yellow) { move(-1) }
                ToolButton("🧽", Palette.Pink, Modifier.weight(1f), label = "けす", sound = false) {
                    sfx.play(Sfx.Sound.Swish)
                    lines.clear()
                }
                ToolButton("💮", Palette.Green, Modifier.weight(1f), label = "できた", sound = false) { check() }
                ToolButton("▶", Palette.Yellow) { move(1) }
            }
        }
        // けっかを ひょうじ(きえていく あいだも 前の けっかを だす)
        val (emoji, message) = when (lastGrade) {
            Grade.Great -> "💮" to "はなまる!"
            Grade.Good -> "⭕" to "じょうず!"
            Grade.Miss -> "🙂" to "おしい!\nもういっかい"
        }
        Celebration(visible = grade != null, emoji = emoji, message = message)
    }
}

@Composable
private fun ToolButton(
    icon: String,
    color: Color,
    modifier: Modifier = Modifier,
    label: String? = null,
    sound: Boolean = true,
    onClick: () -> Unit,
) {
    BouncyBox(color = color, onClick = onClick, sound = sound, modifier = modifier.height(76.dp).then(if (label == null) Modifier.size(64.dp, 76.dp) else Modifier), corner = 38.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(icon, fontSize = 24.sp, color = Palette.Ink)
            if (label != null) Text(label, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Palette.Ink, maxLines = 1, softWrap = false, modifier = Modifier.padding(start = 4.dp))
        }
    }
}
