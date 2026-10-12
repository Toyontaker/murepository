package com.toyontaker.manabikko.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.toyontaker.manabikko.LocalSpeaker
import com.toyontaker.manabikko.data.Numbers
import kotlinx.coroutines.delay

/**
 * かずあそび:えらんだ数だけ ものが でてくるので、ひとつずつ タップして かぞえる。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CountingScreen(onHome: () -> Unit) {
    val speaker = LocalSpeaker.current
    var target by rememberSaveable { mutableIntStateOf(1) }
    var thingIndex by rememberSaveable { mutableIntStateOf(0) }
    // タップした じゅんばん(どのアイテムが なんばんめ か)
    val order = remember(target, thingIndex) { mutableStateListOf<Int>() }
    var done by remember(target, thingIndex) { mutableStateOf(false) }
    val (emoji, name) = Numbers.things[thingIndex]

    LaunchedEffect(target, thingIndex, speaker.ready) {
        speaker.speak("${name}を かぞえよう")
    }

    fun choose(n: Int) {
        target = n
        thingIndex = (thingIndex + 1) % Numbers.things.size
    }

    fun tap(i: Int) {
        if (i in order || done) return
        order += i
        val count = order.size
        if (count == target) {
            done = true
            speaker.speak("${Numbers.reading(count)}! ${name}は ${Numbers.counter(count)}。 よく できたね!")
        } else {
            speaker.speak(Numbers.reading(count))
        }
    }

    // おいわいを すこし みせたら、つぎの かずへ
    LaunchedEffect(done) {
        if (done) {
            delay(3500)
            choose(if (target >= 10) 1 else target + 1)
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TopBar("かず", Palette.Green, onHome)

            // いま いくつ かぞえたか
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    if (order.isEmpty()) "?" else order.size.toString(),
                    fontSize = 96.sp,
                    fontWeight = FontWeight.Black,
                    color = if (done) Color(0xFFE53935) else Palette.Ink,
                )
                Text(
                    if (order.isEmpty()) "タップしてね" else Numbers.reading(order.size),
                    fontSize = 32.sp,
                    maxLines = 1,
                    fontWeight = FontWeight.Bold,
                    color = Palette.Ink,
                    modifier = Modifier.padding(start = 16.dp),
                )
            }

            // かぞえる もの
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clip(RoundedCornerShape(36.dp))
                    .background(Color.White)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    repeat(target) { i ->
                        val pos = order.indexOf(i)
                        CountItem(emoji = emoji, number = if (pos >= 0) pos + 1 else null, onTap = { tap(i) })
                    }
                }
            }

            // かずを えらぶ
            LazyRow(
                Modifier.fillMaxWidth().height(96.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
            ) {
                items((1..10).toList()) { n ->
                    BouncyBox(
                        color = if (n == target) Palette.Orange else Palette.rainbow[(n - 1) % Palette.rainbow.size],
                        onClick = { choose(n) },
                        modifier = Modifier.size(76.dp),
                        corner = 38.dp,
                    ) {
                        Text(n.toString(), fontSize = 36.sp, fontWeight = FontWeight.Black, color = Palette.Ink)
                    }
                }
            }
        }
        Celebration(visible = done, emoji = "🎉", message = "${target}こ!\nよく できたね")
    }
}

@Composable
private fun CountItem(emoji: String, number: Int?, onTap: () -> Unit) {
    val counted = number != null
    val scale by animateFloatAsState(
        targetValue = if (counted) 1.15f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioHighBouncy),
        label = "count",
    )
    Box(
        Modifier
            .size(96.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(if (counted) Palette.Yellow else Palette.Faint.copy(alpha = 0.5f))
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onTap),
        contentAlignment = Alignment.Center,
    ) {
        Text(emoji, fontSize = 56.sp, modifier = Modifier.alpha(if (counted) 1f else 0.9f))
        if (number != null) {
            Badge(number.toString(), Color(0xFFE53935), Modifier.align(Alignment.TopEnd))
        }
    }
}
