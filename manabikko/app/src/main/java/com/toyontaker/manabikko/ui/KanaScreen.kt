package com.toyontaker.manabikko.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.scaleIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.toyontaker.manabikko.LocalSpeaker
import com.toyontaker.manabikko.data.Kana
import com.toyontaker.manabikko.data.KanaSet

/** ひらがな / カタカナ の 五十音表 と 大きな カード。 */
@Composable
fun KanaScreen(title: String, kanaSet: KanaSet, color: Color, onHome: () -> Unit) {
    val speaker = LocalSpeaker.current
    val cells = kanaSet.table.flatten()
    var selected by rememberSaveable(title) { mutableIntStateOf(0) }
    val kana = cells[selected]!!
    val say = { k: Kana -> speaker.speak(k.char, k.say) }

    fun move(step: Int) {
        var i = selected
        do { i = (i + step + cells.size) % cells.size } while (cells[i] == null)
        selected = i
        say(cells[i]!!)
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(title, color, onHome)
        BoxWithConstraints(Modifier.fillMaxSize().padding(16.dp)) {
            val wide = maxWidth > maxHeight
            val card = @Composable { m: Modifier -> KanaCard(kana, onTap = { say(kana) }, onPrev = { move(-1) }, onNext = { move(1) }, modifier = m) }
            val grid = @Composable { m: Modifier ->
                KanaGrid(cells, selected, onSelect = { i -> selected = i; say(cells[i]!!) }, modifier = m)
            }
            if (wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    card(Modifier.weight(1f).fillMaxSize())
                    grid(Modifier.weight(1f).fillMaxSize())
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    card(Modifier.weight(1.1f).fillMaxWidth())
                    grid(Modifier.weight(1f).fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun KanaCard(kana: Kana, onTap: () -> Unit, onPrev: () -> Unit, onNext: () -> Unit, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        ArrowButton("◀", "まえの もじ", onPrev)
        BouncyBox(color = Color.White, onClick = onTap, modifier = Modifier.weight(1f).fillMaxSize().padding(8.dp), corner = 36.dp) {
            AnimatedContent(
                targetState = kana,
                transitionSpec = {
                    scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy), initialScale = 0.5f) togetherWith fadeOut()
                },
                label = "kana",
            ) { k ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(k.char, fontSize = 140.sp, fontWeight = FontWeight.Bold, color = Palette.Ink, lineHeight = 150.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(k.emoji, fontSize = 56.sp)
                        Spacer(Modifier.size(8.dp))
                        Text(highlight(k.word, k.char), fontSize = if (k.word.length > 5) 26.sp else 36.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
                    }
                }
            }
        }
        ArrowButton("▶", "つぎの もじ", onNext)
    }
}

@Composable
private fun ArrowButton(label: String, description: String, onClick: () -> Unit) {
    BouncyBox(color = Palette.Yellow, onClick = onClick, modifier = Modifier.size(64.dp), corner = 32.dp, label = description) {
        Text(label, fontSize = 28.sp, color = Palette.Ink)
    }
}

/** ことばの なかの その文字だけ 赤くする。 */
private fun highlight(word: String, char: String) = buildAnnotatedString {
    val i = word.indexOf(char)
    append(word.substring(0, i))
    withStyle(SpanStyle(color = Color(0xFFE53935))) { append(char) }
    append(word.substring(i + char.length))
}

@Composable
private fun KanaGrid(cells: List<Kana?>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(5),
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(cells) { i, k ->
            if (k == null) {
                Spacer(Modifier.aspectRatio(1f))
            } else {
                val color = Palette.rainbow[(i / 5) % Palette.rainbow.size]
                // えらんでいる文字は こい色の わくで かこむ
                Box(
                    Modifier
                        .aspectRatio(1f)
                        .then(if (i == selected) Modifier.border(4.dp, Palette.Ink, RoundedCornerShape(20.dp)) else Modifier)
                        .padding(4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    BouncyBox(color = color, onClick = { onSelect(i) }, modifier = Modifier.fillMaxSize(), corner = 16.dp) {
                        Text(k.char, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
                    }
                }
            }
        }
    }
}
