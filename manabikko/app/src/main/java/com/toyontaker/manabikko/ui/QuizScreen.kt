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

    /** せいかい したときの ことば */
    val praise: String get() = "せいかい! すごいね!"

    /** 「『あ』は どれかな?」 */
    data class FindKana(override val answer: String, override val choices: List<String>) : Question {
        override val prompt get() = "「${answer}」は どれかな?"
    }

    /** 「りんごは いくつ?」 */
    data class HowMany(val count: Int, val thing: Thing, override val choices: List<String>) : Question {
        override val answer get() = thing.counter.label(count)
        override val prompt get() = "${thing.name}は ${thing.counter.question}?"
        override val praise get() = "せいかい! ${thing.counter.reading(count)}!"
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
internal fun QuizScreen(onHome: () -> Unit, firstQuestion: Question = newQuestion()) {
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
        if (!finished) speaker.speak(question.prompt)
    }
    LaunchedEffect(correct) {
        if (correct) {
            delay(1800)
            correct = false
            firstTry = true
            number += 1
            if (number < QUESTIONS_PER_ROUND) question = newQuestion()
        }
    }
    LaunchedEffect(finished) {
        if (finished) sfx.play(Sfx.Sound.Fanfare)
        if (finished) speaker.speak("ぜんぶ おわったよ! ほしが ${stars}こ。 すごいね!")
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
                        color = Palette.Faint,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }

            if (finished) {
                Finished(stars, onAgain = {
                    round += 1
                    firstTry = true
                    question = newQuestion()
                }, onHome = onHome)
            } else {
                QuestionView(
                    question = question,
                    onRepeat = { speaker.speak(question.prompt) },
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
                            speaker.speak("ちがうよ。 もういっかい!")
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
        val tile = minOf(maxWidth / 3.4f, maxHeight / 3f)
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    question.prompt,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Palette.Ink,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.size(12.dp))
                SoundButton(onClick = onRepeat)
            }

            // かずの もんだいは、かぞえる ものを みせる
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (question is Question.HowMany) {
                    FlowRow(
                        Modifier
                            .clip(RoundedCornerShape(32.dp))
                            .background(Color.White)
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    ) {
                        repeat(question.count) { Text(question.thing.emoji, fontSize = 56.sp) }
                    }
                } else {
                    Text("👀", fontSize = 72.sp)
                }
            }

            Row(
                Modifier.fillMaxWidth().height(tile + 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                question.choices.forEachIndexed { i, choice ->
                    ChoiceTile(
                        text = choice,
                        color = listOf(Palette.Red, Palette.Green, Palette.Blue)[i],
                        size = tile,
                        key = question,
                        onClick = { onAnswer(choice) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ChoiceTile(text: String, color: Color, size: androidx.compose.ui.unit.Dp, key: Any, onClick: () -> Boolean) {
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
