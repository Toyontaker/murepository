package com.toyontaker.manabikko.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.sp
import com.toyontaker.manabikko.LocalSfx
import com.toyontaker.manabikko.Sfx

/**
 * おすと ぷにっと ちぢむ、まるい ボタン。
 *
 * longPressMillis を わたすと「ながおし ボタン」に なる:ちょっと さわった だけでは 反応せず、
 * その時間 おしつづけると onClick。おしている あいだ、ボタンが 左から 白く たまっていく。
 * (あそんでいる 最中に 手が ふれて 画面が かわったり、設定が かわったり しないように)
 */
@Composable
fun BouncyBox(
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    corner: Dp = 28.dp,
    sound: Boolean = true,
    longPressMillis: Long? = null,
    /** 絵文字だけの ボタンの 読み上げ用ラベル(TalkBack など)。 */
    label: String? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val sfx = LocalSfx.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    var holding by remember { mutableStateOf(false) }
    val holdProgress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val currentOnClick by rememberUpdatedState(onClick)
    val fire = {
        if (sound) sfx.play(Sfx.Sound.Pop)
        currentOnClick()
    }
    val scale by animateFloatAsState(
        targetValue = if (pressed || holding) 0.9f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "press",
    )
    val shape = RoundedCornerShape(corner)
    Box(
        modifier = modifier
            .scale(scale)
            .shadow(6.dp, shape)
            .clip(shape)
            .background(color)
            .drawBehind {
                // ながおしの すすみぐあい
                if (holdProgress.value > 0f) {
                    drawRect(Color.White.copy(alpha = 0.55f), size = Size(size.width * holdProgress.value, size.height))
                }
            }
            .border(4.dp, Color.White, shape)
            .then(
                if (longPressMillis == null) {
                    Modifier.clickable(interactionSource = interaction, indication = null) { fire() }
                } else {
                    Modifier
                        .pointerInput(longPressMillis) {
                            awaitEachGesture {
                                awaitFirstDown()
                                holding = true
                                val fill = scope.launch {
                                    holdProgress.snapTo(0f)
                                    holdProgress.animateTo(1f, tween(longPressMillis.toInt(), easing = LinearEasing))
                                }
                                val released = withTimeoutOrNull(longPressMillis) { waitForUpOrCancellation() }
                                if (released == null) {
                                    fire()
                                    waitForUpOrCancellation()
                                }
                                fill.cancel()
                                holding = false
                                scope.launch { holdProgress.snapTo(0f) }
                            }
                        }
                        .semantics { onLongClick { fire(); true } }
                },
            )
            .semantics(mergeDescendants = true) {
                role = Role.Button
                if (label != null) contentDescription = label
            },
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/** 画面の上の、「おうち」ボタンと タイトル。 */
@Composable
fun TopBar(title: String, color: Color, onHome: () -> Unit, trailing: @Composable () -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BouncyBox(color = Color.White, onClick = onHome, modifier = Modifier.size(64.dp), corner = 32.dp, label = "おうちに もどる") {
            Text("🏠", fontSize = 32.sp)
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(32.dp))
                .background(color)
                .padding(horizontal = 24.dp, vertical = 8.dp),
        ) {
            Text(title, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
        }
        Spacer(Modifier.weight(1f))
        trailing()
    }
}

/** 正解したときなどに、まんなかに ぽんっと でてくる おいわい。 */
@Composable
fun Celebration(visible: Boolean, emoji: String, message: String) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy), initialScale = 0.3f),
        exit = fadeOut() + scaleOut(targetScale = 0.6f),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Palette.Background.copy(alpha = 0.75f)),
            contentAlignment = Alignment.Center,
        ) {
            // うしろの 絵と かさならないよう、白い カードに のせる
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .shadow(8.dp, RoundedCornerShape(40.dp))
                    .clip(RoundedCornerShape(40.dp))
                    .background(Color.White)
                    .padding(horizontal = 32.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(emoji, fontSize = 120.sp, lineHeight = 1.2.em)
                Text(
                    message,
                    fontSize = 40.sp,
                    lineHeight = 1.3.em,
                    fontWeight = FontWeight.Bold,
                    color = Palette.Ink,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** おとを もういちど きく ボタン。 */
@Composable
fun SoundButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    BouncyBox(color = Palette.Yellow, onClick = onClick, modifier = modifier.size(72.dp), corner = 36.dp, label = "もういちど きく") {
        Text("🔊", fontSize = 34.sp)
    }
}

/** まるい 数字バッジ。 */
@Composable
fun Badge(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(color)
            .border(2.dp, Color.White, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

/** 白い 文字を のせても 読みやすい 赤(コントラスト 4.5 以上)。 */
val BadgeRed = Color(0xFFD32F2F)
