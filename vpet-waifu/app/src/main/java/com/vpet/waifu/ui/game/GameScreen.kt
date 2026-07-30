package com.vpet.waifu.ui.game

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vpet.waifu.R
import com.vpet.waifu.domain.PetSnapshot
import com.vpet.waifu.domain.PetState
import com.vpet.waifu.domain.TapGame
import com.vpet.waifu.ui.character.AnimatedPet
import com.vpet.waifu.ui.components.EffectChip
import com.vpet.waifu.ui.components.OutlineButton
import com.vpet.waifu.ui.components.PanelCard
import com.vpet.waifu.ui.components.PrimaryButton
import com.vpet.waifu.ui.components.ScreenTitle
import com.vpet.waifu.ui.components.StatBarTrack
import com.vpet.waifu.ui.theme.Accents
import com.vpet.waifu.ui.theme.StatColors
import com.vpet.waifu.ui.theme.Surfaces
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlin.random.Random

private const val TARGET_SIZE_DP = 56
private const val TARGET_LIFETIME_MILLIS = 1_100L
private const val SPAWN_INTERVAL_MILLIS = 520L
private const val FRAME_MILLIS = 50L
private val TARGET_EMOJI = listOf("💜", "⭐", "🍬", "🌸", "🎀")

private data class Target(val id: Long, val xFraction: Float, val yFraction: Float, val emoji: String)

/**
 * The tap mini-game.
 *
 * Hearts pop up for about a second each; every one caught is mood. It costs
 * energy in proportion to the score, so it lifts her spirits but cannot replace
 * food and sleep — which is the whole point of having it in the loop.
 */
@Composable
fun GameScreen(
    snapshot: PetSnapshot,
    nowMillis: Long,
    onStart: () -> Unit,
    onFinish: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var running by remember { mutableStateOf(false) }
    var score by remember { mutableIntStateOf(0) }
    var secondsLeft by remember { mutableIntStateOf(TapGame.DURATION_SECONDS) }
    var lastScore by remember { mutableStateOf<Int?>(null) }
    val targets: SnapshotStateList<Target> = remember { emptyList<Target>().toMutableStateList() }

    // One coroutine owns the whole round: spawn, expire, count down, settle up.
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        score = 0
        targets.clear()
        secondsLeft = TapGame.DURATION_SECONDS
        onStart()

        val random = Random(nowMillis)
        var elapsed = 0L
        var nextSpawn = 0L
        var nextId = 0L
        try {
            while (elapsed < TapGame.DURATION_SECONDS * 1000L) {
                if (elapsed >= nextSpawn) {
                    targets += Target(
                        id = nextId++,
                        xFraction = 0.08f + random.nextFloat() * 0.84f,
                        yFraction = 0.08f + random.nextFloat() * 0.78f,
                        emoji = TARGET_EMOJI[random.nextInt(TARGET_EMOJI.size)],
                    )
                    nextSpawn = elapsed + SPAWN_INTERVAL_MILLIS
                }
                delay(FRAME_MILLIS)
                elapsed += FRAME_MILLIS
                // Targets live a fixed time; ids are monotonic so the oldest expire first.
                val expiredBefore = nextId - (TARGET_LIFETIME_MILLIS / SPAWN_INTERVAL_MILLIS).toLong() - 1
                targets.removeAll { it.id <= expiredBefore }
                secondsLeft = (TapGame.DURATION_SECONDS - elapsed / 1000L).toInt().coerceAtLeast(0)
            }
        } finally {
            // Every way out of the round settles it here — the clock running
            // down, the stop button, and, crucially, this coroutine being
            // cancelled because the tab was switched away mid-game. Without
            // that last case she stays PLAYING forever and every other action
            // in the app is locked out behind it.
            targets.clear()
            lastScore = score
            onFinish(score)
            running = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenTitle(stringResource(R.string.tab_game)) {
            EffectChip(emoji = "💜", text = "${snapshot.stats.mood.roundToInt()}", tint = StatColors.Mood)
        }

        GameHeader(
            running = running,
            score = score,
            secondsLeft = secondsLeft,
            lastScore = lastScore,
            onStop = { running = false },
        )

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Accents.Deep.copy(alpha = 0.35f), Surfaces.Screen),
                    ),
                )
                .border(1.dp, Surfaces.CardBorder, RoundedCornerShape(24.dp)),
        ) {
            val boardWidth = maxWidth
            val boardHeight = maxHeight

            AnimatedPet(
                state = if (running) PetState.PLAYING else snapshot.state(nowMillis),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .height(boardHeight * 0.55f)
                    .fillMaxWidth(),
            )

            targets.forEach { target ->
                key(target.id) {
                    TargetBubble(
                        target = target,
                        offsetX = boardWidth * target.xFraction - (TARGET_SIZE_DP / 2).dp,
                        offsetY = boardHeight * target.yFraction - (TARGET_SIZE_DP / 2).dp,
                        onTap = {
                            score++
                            targets.remove(target)
                        },
                    )
                }
            }

            StartOverlay(
                visible = !running,
                canPlay = snapshot.acceptsInteraction,
                played = lastScore != null,
                onStart = { running = true },
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

/**
 * The row above the board.
 *
 * It swaps between the live scoreboard and what the last round paid. The height
 * is fixed so the board underneath does not jump when a round starts, and it
 * lives in its own composable so `AnimatedVisibility` resolves to the plain
 * overload rather than the `ColumnScope` one.
 */
@Composable
private fun GameHeader(
    running: Boolean,
    score: Int,
    secondsLeft: Int,
    lastScore: Int?,
    onStop: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth().height(78.dp)) {
        AnimatedVisibility(
            visible = running,
            enter = fadeIn() + scaleIn(initialScale = 0.94f),
            exit = fadeOut() + scaleOut(targetScale = 0.94f),
        ) {
            RoundHeader(score = score, secondsLeft = secondsLeft, onStop = onStop)
        }
        AnimatedVisibility(visible = !running, enter = fadeIn(), exit = fadeOut()) {
            IdleHeader(lastScore)
        }
    }
}

/** The "start a round" call to action floating over the board. */
@Composable
private fun StartOverlay(
    visible: Boolean,
    canPlay: Boolean,
    played: Boolean,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn() + scaleIn(initialScale = 0.85f),
        exit = fadeOut() + scaleOut(targetScale = 0.85f),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PrimaryButton(
                text = stringResource(if (played) R.string.game_again else R.string.game_start),
                onClick = onStart,
                enabled = canPlay,
            )
            if (!canPlay) {
                Text(
                    text = stringResource(R.string.game_busy),
                    style = MaterialTheme.typography.bodySmall,
                    color = StatColors.Hunger,
                )
            }
        }
    }
}

/** The live scoreboard: countdown bar, score, and a way out of the round. */
@Composable
private fun RoundHeader(score: Int, secondsLeft: Int, onStop: () -> Unit) {
    // Animated so the bar drains smoothly instead of stepping once a second.
    val fraction by animateFloatAsState(
        targetValue = secondsLeft.toFloat() / TapGame.DURATION_SECONDS,
        animationSpec = tween(durationMillis = 1_000),
        label = "round-timer",
    )
    // The score pops each time it changes — the tap needs to feel like it landed.
    var bumped by remember { mutableStateOf(false) }
    LaunchedEffect(score) {
        bumped = true
        delay(90)
        bumped = false
    }
    val pop by animateFloatAsState(
        targetValue = if (bumped) 1.22f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "score-pop",
    )

    PanelCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "$score",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Accents.Text,
                        modifier = Modifier.scale(pop),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.game_seconds_left, secondsLeft),
                        style = MaterialTheme.typography.labelLarge,
                        color = Accents.TextDim,
                    )
                }
                Spacer(Modifier.height(8.dp))
                StatBarTrack(fraction, Accents.Bright, height = 6.dp)
            }
            Spacer(Modifier.width(12.dp))
            OutlineButton(
                text = stringResource(R.string.game_stop),
                onClick = onStop,
                tint = StatColors.Hunger,
            )
        }
    }
}

/** Between rounds: what the last one paid, or nothing at all before the first. */
@Composable
private fun IdleHeader(lastScore: Int?) {
    PanelCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("🎮", fontSize = 22.sp)
            Spacer(Modifier.width(12.dp))
            if (lastScore == null) {
                Text(
                    text = stringResource(R.string.game_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Accents.TextDim,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.game_result, lastScore),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Accents.Text,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        EffectChip(
                            emoji = "💜",
                            text = "+${TapGame.moodGain(lastScore).roundToInt()}",
                            tint = StatColors.Mood,
                        )
                        EffectChip(
                            emoji = "💰",
                            text = "+${TapGame.coins(lastScore)}",
                            tint = StatColors.Money,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TargetBubble(
    target: Target,
    offsetX: Dp,
    offsetY: Dp,
    onTap: () -> Unit,
) {
    // Pop in rather than appear: a target that materialises at full size is
    // easy to miss at the edge of vision. It also breathes and fades towards
    // the end of its life, which is the only warning that it is about to go.
    var appeared by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (appeared) 1f else 0.3f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "target-pop",
    )
    val fade by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "target-fade",
    )
    LaunchedEffect(Unit) { appeared = true }

    Box(
        modifier = Modifier
            .offset(x = offsetX, y = offsetY)
            .size(TARGET_SIZE_DP.dp)
            .scale(scale)
            .alpha(fade)
            .clip(CircleShape)
            .background(
                Brush.verticalGradient(
                    listOf(Accents.Primary.copy(alpha = 0.55f), Accents.Deep.copy(alpha = 0.75f)),
                ),
            )
            .border(1.dp, Accents.Bright.copy(alpha = 0.6f), CircleShape)
            // No ripple: it lags behind a target that vanishes on the same tap.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onTap,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(target.emoji, fontSize = 24.sp)
    }
}
