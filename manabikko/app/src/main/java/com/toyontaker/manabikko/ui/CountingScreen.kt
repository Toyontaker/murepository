package com.toyontaker.manabikko.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.toyontaker.manabikko.LocalSfx
import com.toyontaker.manabikko.LocalSpeaker
import com.toyontaker.manabikko.Sfx
import com.toyontaker.manabikko.data.Numbers
import kotlinx.coroutines.delay

private val GAP = 8.dp
private val MAX_ITEM = 140.dp

/**
 * かずあそび:えらんだ数だけ ものが でてくるので、ひとつずつ タップして かぞえる。
 */
@Composable
fun CountingScreen(onHome: () -> Unit, initialTarget: Int = 1, initialThing: Int = 0) {
    val speaker = LocalSpeaker.current
    val sfx = LocalSfx.current
    var target by rememberSaveable { mutableIntStateOf(initialTarget) }
    var thingIndex by rememberSaveable { mutableIntStateOf(initialThing) }
    // タップした じゅんばん(どのアイテムが なんばんめ か)
    val order = remember(target, thingIndex) { mutableStateListOf<Int>() }
    var done by remember(target, thingIndex) { mutableStateOf(false) }
    val (emoji, name, counter, say) = Numbers.things[thingIndex]

    LaunchedEffect(target, thingIndex, speaker.ready) {
        speaker.speak("${say}を数えよう")
    }

    fun choose(n: Int) {
        target = n
        thingIndex = (thingIndex + 1) % Numbers.things.size
    }

    fun tap(i: Int) {
        if (i in order || done) return
        order += i
        val count = order.size
        sfx.count(count)
        if (count == target) {
            done = true
            speaker.speak(counter.say(count), "${say}は${counter.say(count)}。よくできたね!")
        } else {
            // 「いっぽん、にほん、さんぼん…」と じょすうしつきで かぞえる
            speaker.speak(counter.say(count))
        }
    }

    // おいわいを すこし みせたら、つぎの かずへ
    LaunchedEffect(done) {
        if (done) {
            delay(400)
            sfx.play(Sfx.Sound.Fanfare)
            delay(3100)
            choose(if (target >= 10) 1 else target + 1)
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TopBar("かず", Palette.Green, onHome)

            // いま いくつ かぞえたか(「3ぼん さんぼん」)
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.Center,
            ) {
                val countColor = if (done) Color(0xFFE53935) else Palette.Ink
                Text(
                    if (order.isEmpty()) "?" else order.size.toString(),
                    fontSize = 72.sp,
                    fontWeight = FontWeight.Black,
                    color = countColor,
                )
                if (order.isNotEmpty()) {
                    Text(
                        counter.suffix(order.size),
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Black,
                        color = countColor,
                        modifier = Modifier.padding(bottom = 14.dp),
                    )
                }
                Text(
                    if (order.isEmpty()) "タップしてね" else counter.reading(order.size),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Palette.Ink,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 16.dp, bottom = 16.dp),
                )
            }

            // かぞえる もの(スクロール なしで ぜんぶ 見えるように 大きさを きめる)
            BoxWithConstraints(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(36.dp))
                    .background(Color.White)
                    .padding(12.dp),
                contentAlignment = Alignment.Center,
            ) {
                val (columns, itemSize) = fitGrid(target, maxWidth, maxHeight)
                Column(verticalArrangement = Arrangement.spacedBy(GAP)) {
                    (0 until target).chunked(columns).forEach { row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(GAP),
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        ) {
                            row.forEach { i ->
                                val pos = order.indexOf(i)
                                CountItem(
                                    emoji = emoji,
                                    number = if (pos >= 0) pos + 1 else null,
                                    size = itemSize,
                                    onTap = { tap(i) },
                                    modifier = Modifier.testTag("item_$i"),
                                )
                            }
                        }
                    }
                }
            }

            // かずを えらぶ(5こずつ 2だん)
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                (1..10).chunked(5).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { n ->
                            BouncyBox(
                                color = if (n == target) Palette.Orange else Palette.Yellow,
                                onClick = { choose(n) },
                                modifier = Modifier.weight(1f).height(60.dp),
                                corner = 30.dp,
                            ) {
                                Text(n.toString(), fontSize = 30.sp, fontWeight = FontWeight.Black, color = Palette.Ink)
                            }
                        }
                    }
                }
            }
        }
        Celebration(visible = done, emoji = "🎉", message = "${counter.label(target)}!\nよく できたね")
    }
}

/** n こ を ならべるとき、いちばん 大きく できる れつの数 と 1こ の 大きさ。 */
private fun fitGrid(n: Int, width: Dp, height: Dp): Pair<Int, Dp> {
    var best = 1 to 0.dp
    for (cols in 1..n) {
        val rows = (n + cols - 1) / cols
        val size = min((width - GAP * (cols - 1)) / cols, (height - GAP * (rows - 1)) / rows)
        if (size > best.second) best = cols to size
    }
    return best.first to min(best.second, MAX_ITEM)
}

@Composable
private fun CountItem(emoji: String, number: Int?, size: Dp, onTap: () -> Unit, modifier: Modifier = Modifier) {
    val counted = number != null
    val scale by animateFloatAsState(
        targetValue = if (counted) 1.08f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioHighBouncy),
        label = "count",
    )
    // まわりに すこし よゆうを とって、ふくらんでも バッジが きれないようにする
    Box(
        modifier
            .size(size)
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onTap),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(size * 0.84f)
                .scale(scale)
                .clip(CircleShape)
                .background(if (counted) Palette.Yellow else Palette.Faint.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(emoji, fontSize = (size.value * 0.48f).sp)
        }
        if (number != null) {
            Badge(number.toString(), Color(0xFFE53935), Modifier.align(Alignment.TopEnd))
        }
    }
}
