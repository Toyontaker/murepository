package com.toyontaker.manabikko.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.toyontaker.manabikko.LocalSpeaker
import com.toyontaker.manabikko.LocalSfx
import com.toyontaker.manabikko.Sfx
import com.toyontaker.manabikko.data.Hiragana
import com.toyontaker.manabikko.data.Kana
import com.toyontaker.manabikko.data.Katakana
import com.toyontaker.manabikko.data.Numbers
import com.toyontaker.manabikko.data.Thing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val QUESTIONS_PER_ROUND = 5

internal sealed interface Question {
    val prompt: String
    val choices: List<String>
    val answer: String

    /** 読み上げ用の 問題文 */
    val speech: String

    /** せいかい したときの ことば(読み上げ用) */
    val praise: String get() = "正解!すごいね!"

    /** 「『あ』は どれかな?」 */
    data class FindKana(override val answer: String, override val choices: List<String>) : Question {
        override val prompt get() = "「${answer}」は どれかな?"
        override val speech get() = "「${answer}」はどれかな?"
    }

    /** 「りんごは いくつ?」 */
    data class HowMany(val count: Int, val thing: Thing, override val choices: List<String>) : Question {
        override val answer get() = thing.counter.label(count)
        override val prompt get() = "${thing.name}は ${thing.counter.question}?"
        override val speech get() = "${thing.say}は${thing.counter.sayQuestion}?"
        override val praise get() = "正解!${thing.counter.say(count)}!"
    }
}

internal fun newQuestion(): Question =
    when ((0 until 5).random()) {
        // ひらがな 2 : カタカナ 1 : かず 2 の わりあい
        0, 1 -> findKana(Hiragana.all)
        2 -> findKana(Katakana.all)
        else -> howMany()
    }

private fun findKana(letters: List<Kana>): Question {
    val picks = letters.shuffled().take(3).map { it.char }
    return Question.FindKana(answer = picks.first(), choices = picks.shuffled())
}

private fun howMany(): Question {
    // 3さいむけに 1〜5 まで
    val count = (1..5).random()
    val thing = Numbers.things.random()
    val choices = ((1..5).filter { it != count }.shuffled().take(2) + count).shuffled().map { thing.counter.label(it) }
    return Question.HowMany(count, thing, choices)
}

@Composable
internal fun QuizScreen(
    onHome: () -> Unit,
    questions: () -> Question = ::newQuestion,
    firstQuestion: Question = questions(),
) {
    val speaker = LocalSpeaker.current
    val sfx = LocalSfx.current
    var round by remember { mutableIntStateOf(0) }
    var number by remember(round) { mutableIntStateOf(0) }
    var stars by remember(round) { mutableIntStateOf(0) }
    // そのもんだいで 1かいめで せいかいしたか
    var firstTry by remember { mutableStateOf(true) }
    var question by remember { mutableStateOf(firstQuestion) }
    var correct by remember { mutableStateOf(false) }
    val finished = number >= QUESTIONS_PER_ROUND

    LaunchedEffect(question, speaker.ready) {
        if (!finished) speaker.speak(question.speech)
    }
    LaunchedEffect(correct) {
        if (correct) {
            delay(1800)
            correct = false
            firstTry = true
            number += 1
            if (number < QUESTIONS_PER_ROUND) question = questions()
        }
    }
    LaunchedEffect(finished) {
        if (finished) sfx.play(Sfx.Sound.Fanfare)
        if (finished) speaker.speak("全部終わったよ!", "星が${stars}個。すごいね!")
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TopBar("クイズ", Palette.Purple, onHome)
            // ほし(すすみぐあい)
            Row(
                Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                repeat(QUESTIONS_PER_ROUND) { i ->
                    Text(
                        if (i < stars) "⭐" else "☆",
                        fontSize = 40.sp,
                        color = Color(0xFF8D6E63),
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }

            if (finished) {
                Finished(stars, onAgain = {
                    round += 1
                    firstTry = true
                    question = questions()
                }, onHome = onHome)
            } else {
                QuestionView(
                    question = question,
                    onRepeat = { speaker.speak(question.speech) },
                    onAnswer = { choice ->
                        if (correct) return@QuestionView true
                        if (choice == question.answer) {
                            if (firstTry) stars += 1
                            correct = true
                            sfx.play(Sfx.Sound.Correct)
                            speaker.speak(question.praise)
                            true
                        } else {
                            firstTry = false
                            sfx.play(Sfx.Sound.Wrong)
                            speaker.speak("違うよ。もう一回!")
                            false
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Celebration(visible = correct, emoji = "🎉", message = "せいかい!")
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuestionView(question: Question, onRepeat: () -> Unit, onAnswer: (String) -> Boolean, modifier: Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth().padding(16.dp)) {
        // よこ長の 画面では、もんだいを 左、こたえを 右に たてに ならべる
        val wide = maxWidth > maxHeight * 1.3f
        val prompt: @Composable () -> Unit = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FitText(question.prompt, fontSize = 32.sp, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.size(12.dp))
                SoundButton(onClick = onRepeat)
            }

        }
        val items: @Composable ColumnScope.() -> Unit = {
            // かずの もんだいは、かぞえる ものを みせる
            // (よこ向きで 高さが たりない ときも 見えるように、あいている 大きさに あわせる)
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (question is Question.HowMany) {
                    val n = question.count
                    // いちばん 大きく ならべられる れつの数を えらぶ
                    val gap = 8.dp
                    val (cols, item) = (1..n).map { c ->
                        val rows = (n + c - 1) / c
                        c to minOf((maxWidth - 32.dp) / c - gap, (maxHeight - 32.dp) / rows - gap)
                    }.maxBy { it.second }.let { (c, size) -> c to size.coerceIn(16.dp, 120.dp) }
                    val emojiSize = with(LocalDensity.current) { (item * 0.85f).toSp() }
                    Column(
                        Modifier
                            .clip(RoundedCornerShape(32.dp))
                            .background(Color.White)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(gap),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        (0 until n).chunked(cols).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                                row.forEach {
                                    Box(Modifier.size(item), contentAlignment = Alignment.Center) {
                                        Text(question.thing.emoji, fontSize = emojiSize, lineHeight = emojiSize)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    val eyes = with(LocalDensity.current) { minOf(72.dp, maxHeight * 0.8f).toSp() }
                    Text("👀", fontSize = eyes, lineHeight = eyes)
                }
            }

        }
        val choice: @Composable (Int, Dp) -> Unit = { i, tile ->
            val text = question.choices[i]
            ChoiceTile(
                text = text,
                color = listOf(Palette.Red, Palette.Green, Palette.Blue)[i],
                size = tile,
                key = question,
                onClick = { onAnswer(text) },
            )
        }
        if (wide) {
            val tile = minOf(maxHeight / 3.4f, maxWidth / 4.5f)
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                    prompt()
                    items()
                }
                Column(
                    Modifier.fillMaxHeight().padding(start = 16.dp),
                    verticalArrangement = Arrangement.SpaceEvenly,
                ) {
                    question.choices.indices.forEach { choice(it, tile) }
                }
            }
        } else {
            val tile = minOf(maxWidth / 3.4f, maxHeight / 3f)
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                prompt()
                items()
                Row(
                    Modifier.fillMaxWidth().height(tile + 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    question.choices.indices.forEach { choice(it, tile) }
                }
            }
        }
    }
}

@Composable
private fun ChoiceTile(text: String, color: Color, size: Dp, key: Any, onClick: () -> Boolean) {
    val shake = remember(key) { Animatable(0f) }
    val scope = rememberCoroutineScope()
    BouncyBox(
        color = color,
        sound = false,
        onClick = {
            if (!onClick()) {
                // まちがえたら ぷるぷる
                scope.launch {
                    for (x in listOf(20f, -20f, 14f, -14f, 6f, 0f)) shake.animateTo(x, tween(50))
                }
            }
        },
        modifier = Modifier
            .size(size)
            .graphicsLayer { translationX = shake.value },
        corner = 32.dp,
    ) {
        // 「3ぼん」は 数字を 大きく、じょすうしを 小さく
        val digits = text.takeWhile { it.isDigit() }
        Text(
            buildAnnotatedString {
                append(text)
                if (digits.isNotEmpty()) addStyle(SpanStyle(fontSize = (size.value * 0.2f).sp), digits.length, text.length)
            },
            fontSize = (size.value * if (digits.isNotEmpty() && digits.length < text.length) 0.42f else 0.5f).sp,
            fontWeight = FontWeight.Black,
            color = Palette.Ink,
            maxLines = 1,
        )
    }
}

@Composable
private fun Finished(stars: Int, onAgain: () -> Unit, onHome: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("🏆", fontSize = 120.sp)
        Text("ぜんぶ おわったよ!", fontSize = 40.sp, fontWeight = FontWeight.Black, color = Palette.Ink)
        Text("⭐ × $stars", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
        Spacer(Modifier.height(32.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            BouncyBox(color = Palette.Orange, onClick = onAgain, modifier = Modifier.size(160.dp, 96.dp)) {
                Text("もういちど", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
            }
            BouncyBox(color = Palette.Blue, onClick = onHome, modifier = Modifier.size(160.dp, 96.dp)) {
                Text("🏠 おうち", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
            }
        }
    }
}
