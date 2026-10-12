package com.toyontaker.manabikko.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.toyontaker.manabikko.LocalSpeaker
import com.toyontaker.manabikko.LocalSfx
import com.toyontaker.manabikko.Sfx
import com.toyontaker.manabikko.data.Hiragana
import com.toyontaker.manabikko.data.KanaSet
import com.toyontaker.manabikko.data.Katakana
import com.toyontaker.manabikko.data.LetterDeck
import com.toyontaker.manabikko.data.LetterOrder
import com.toyontaker.manabikko.rememberPref
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
    // おとなが えらぶ 設定(アプリを とじても おぼえておく)
    var script by rememberPref("trace_script", "hira")
    var orderPref by rememberPref("trace_order", LetterOrder.Sequential.encode())
    val kanaSet: KanaSet = if (script == "kata") Katakana else Hiragana
    val order = LetterOrder.decode(orderPref)
    val deck = remember(kanaSet, order) { LetterDeck(kanaSet, order) }
    var currentChar by rememberSaveable(kanaSet, order) { mutableStateOf(deck.next(null).char) }
    val kana = kanaSet.all.firstOrNull { it.char == currentChar } ?: kanaSet.all.first()
    // ランダムの ときに ◀ で もどれるように、出した 文字を おぼえておく
    val history = remember(deck) { mutableStateListOf<String>() }
    var showSettings by remember { mutableStateOf(false) }

    fun goNext() {
        history += kana.char
        currentChar = deck.next(kana).char
    }

    fun goPrevious() {
        if (order == LetterOrder.Sequential) {
            currentChar = deck.previous(kana).char
        } else {
            history.removeLastOrNull()?.let { currentChar = it }
        }
    }
    val lines = remember(kana) { mutableStateListOf<Line>() }
    // さいてんの けっか(null のときは まだ)
    var grade by remember(kana) { mutableStateOf<Grade?>(null) }
    var lastGrade by remember { mutableStateOf(Grade.Good) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val strokeData = remember(kana) { Strokes.get(context, kana.char) }
    val glyph = remember(strokeData, canvasSize) {
        Glyph(strokeData, canvasSize.width.toFloat(), canvasSize.height.toFloat())
    }
    val guide = remember(glyph) { if (canvasSize == IntSize.Zero) null else StrokeGuide(glyph, density) }
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
        grade = null
        if (g == Grade.Great) {
            // はなまる なら つぎの 文字へ
            goNext()
        } else {
            // それ以外は けして もういちど(はなまるを めざす)
            lines.clear()
        }
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
                speaker.speak("はなまる!とっても上手!", "次の字に行くよ")
            }
            Grade.Good -> {
                sfx.play(Sfx.Sound.Correct)
                speaker.speak("上手!", "もう一回書いて、花丸をもらおう")
            }
            Grade.Miss -> {
                sfx.play(Sfx.Sound.Wrong)
                speaker.speak("惜しい!", "薄い字の上を、番号の順になぞってみよう")
            }
        }
    }

    fun erase() {
        sfx.play(Sfx.Sound.Swish)
        lines.clear()
    }

    // おてほん と こどもが かく 紙
    val canvasArea: @Composable BoxScope.() -> Unit = {
        Canvas(
            Modifier
                .fillMaxSize()
                .testTag("trace_canvas")
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
            // (画面に でた 直後は 大きさが まだ わからないので、わかってから かく)
            if (guide != null) {
                drawIntoCanvas { guide.draw(it.nativeCanvas, lines.size, demoStroke, demoProgress.value) }
            }

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
                label = "かきじゅんを みる",
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("✍️", fontSize = 26.sp)
                    Text("じゅん", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
                }
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TopBar("なぞりがき", Palette.Blue, onHome) {
                // 文字の しゅるい と じゅんばん を えらぶ(おとな向け)
                BouncyBox(
                    color = if (script == "kata") Palette.Orange else Palette.Red,
                    onClick = { showSettings = true },
                    longPressMillis = SETTINGS_HOLD_MILLIS,
                    label = "もじと じゅんばんの せってい(ながおし)",
                    modifier = Modifier.size(100.dp, 64.dp),
                    corner = 32.dp,
                ) {
                    FitText(orderLabel(kanaSet, order), fontSize = 26.sp, modifier = Modifier.padding(horizontal = 8.dp))
                }
            }

            // 横長(スマホを よこ向き など)の ときは ボタンを 右に ならべて、かく ところを 広く する
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val wide = maxWidth > maxHeight * 1.3f
                val paper = Modifier
                    .padding(16.dp)
                    .clip(RoundedCornerShape(36.dp))
                    .background(Color.White)
                if (wide) {
                    Row(Modifier.fillMaxSize()) {
                        Box(paper.weight(1f).fillMaxHeight()) { canvasArea() }
                        Column(
                            Modifier.width(180.dp).fillMaxHeight().padding(top = 16.dp, bottom = 16.dp, end = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ToolButton("◀", Palette.Yellow, Modifier.weight(1f), description = "まえの もじ") { goPrevious() }
                                ToolButton("▶", Palette.Yellow, Modifier.weight(1f), description = "つぎの もじ") { goNext() }
                            }
                            ToolButton("🧽", Palette.Pink, Modifier.fillMaxWidth(), label = "けす", sound = false) { erase() }
                            ToolButton("💮", Palette.Green, Modifier.fillMaxWidth(), label = "できた", sound = false) { check() }
                        }
                    }
                } else {
                    Column(Modifier.fillMaxSize()) {
                        Box(paper.weight(1f).fillMaxWidth()) { canvasArea() }
                        Row(
                            Modifier.fillMaxWidth().height(100.dp).padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ToolButton("◀", Palette.Yellow, description = "まえの もじ") { goPrevious() }
                            ToolButton("🧽", Palette.Pink, Modifier.weight(1f), label = "けす", sound = false) { erase() }
                            ToolButton("💮", Palette.Green, Modifier.weight(1f), label = "できた", sound = false) { check() }
                            ToolButton("▶", Palette.Yellow, description = "つぎの もじ") { goNext() }
                        }
                    }
                }
            }
        }
        // けっかを ひょうじ(きえていく あいだも 前の けっかを だす)
        val (emoji, message) = when (lastGrade) {
            Grade.Great -> "💮" to "はなまる!\nつぎの もじへ"
            Grade.Good -> "⭕" to "じょうず!\nはなまるまで あと すこし"
            Grade.Miss -> "🙂" to "おしい!\nもういっかい"
        }
        Celebration(visible = grade != null, emoji = emoji, message = message)

        if (showSettings) {
            TraceSettings(
                script = script,
                order = order,
                onScript = { script = it },
                onOrder = { orderPref = it.encode() },
                onClose = { showSettings = false },
            )
        }
    }
}

/** 設定ボタンの 表示(じゅんばん「あ→」、ぜんぶランダム「あ🎲」、か行ランダム「か🎲」)。 */
internal fun orderLabel(set: KanaSet, order: LetterOrder): String = when (order) {
    LetterOrder.Sequential -> "${set.all.first().char}→"
    LetterOrder.AllRandom -> "${set.all.first().char}🎲"
    is LetterOrder.RowRandom -> "${rowHead(set, order.row)}🎲"
}

private fun rowHead(set: KanaSet, row: Int) = set.table[row].firstOrNull { it != null }?.char ?: ""

/** なぞりがきの 設定パネル:ひらがな/カタカナ、じゅんばん/ぜんぶランダム/行ごとランダム。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TraceSettings(
    script: String,
    order: LetterOrder,
    onScript: (String) -> Unit,
    onOrder: (LetterOrder) -> Unit,
    onClose: () -> Unit,
) {
    val set: KanaSet = if (script == "kata") Katakana else Hiragana
    Box(
        Modifier
            .fillMaxSize()
            .background(Palette.Ink.copy(alpha = 0.4f))
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(16.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(Palette.Background)
                // パネルの なかを おしても とじない
                .clickable(remember { MutableInteractionSource() }, indication = null) {}
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingsLabel("もじ")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("ひらがな", script != "kata", Modifier.weight(1f)) { onScript("hira") }
                Chip("カタカナ", script == "kata", Modifier.weight(1f)) { onScript("kata") }
            }
            SettingsLabel("じゅんばん")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("じゅんばん", order == LetterOrder.Sequential, Modifier.weight(1f)) { onOrder(LetterOrder.Sequential) }
                Chip("ぜんぶ 🎲", order == LetterOrder.AllRandom, Modifier.weight(1f)) { onOrder(LetterOrder.AllRandom) }
            }
            SettingsLabel("ぎょうの なかで 🎲")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                maxItemsInEachRow = 5,
            ) {
                set.table.indices.forEach { row ->
                    Chip(rowHead(set, row), order == LetterOrder.RowRandom(row), Modifier.weight(1f)) {
                        onOrder(LetterOrder.RowRandom(row))
                    }
                }
            }
            BouncyBox(color = Palette.Green, onClick = onClose, modifier = Modifier.fillMaxWidth().height(64.dp), corner = 32.dp) {
                Text("OK", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
            }
        }
    }
}

@Composable
private fun SettingsLabel(text: String) {
    Text(text, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
}

@Composable
private fun Chip(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    BouncyBox(
        color = if (selected) Palette.Orange else Color.White,
        onClick = onClick,
        modifier = modifier.height(56.dp),
        corner = 28.dp,
    ) {
        Text(text, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Palette.Ink, maxLines = 1)
    }
}

@Composable
private fun ToolButton(
    icon: String,
    color: Color,
    modifier: Modifier = Modifier,
    label: String? = null,
    sound: Boolean = true,
    description: String? = null,
    onClick: () -> Unit,
) {
    BouncyBox(
        color = color,
        onClick = onClick,
        sound = sound,
        label = description,
        modifier = modifier.height(76.dp).then(if (label == null) Modifier.widthIn(min = 64.dp) else Modifier),
        corner = 38.dp,
    ) {
        Row(Modifier.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(icon, fontSize = 24.sp, color = Palette.Ink)
            if (label != null) FitText(label, fontSize = 22.sp, modifier = Modifier.weight(1f, fill = false).padding(start = 4.dp))
        }
    }
}
