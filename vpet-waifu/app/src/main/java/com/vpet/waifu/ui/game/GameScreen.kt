package com.vpet.waifu.ui.game

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vpet.waifu.R
import com.vpet.waifu.domain.PetSnapshot
import com.vpet.waifu.domain.PetState
import com.vpet.waifu.domain.TapGame
import com.vpet.waifu.ui.character.AnimatedPet
import com.vpet.waifu.ui.components.EffectChip
import com.vpet.waifu.ui.components.ScreenTitle
import com.vpet.waifu.ui.theme.StatColors
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlin.random.Random

private const val TARGET_SIZE_DP = 56
private const val TARGET_LIFETIME_MILLIS = 1_100L
private const val SPAWN_INTERVAL_MILLIS = 520L

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

        targets.clear()
        lastScore = score
        onFinish(score)
        running = false
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenTitle(stringResource(R.string.tab_game)) {
            EffectChip(emoji = "💜", text = "${snapshot.stats.mood.roundToInt()}", tint = StatColors.Mood)
        }

        if (running) {
            LinearProgressIndicator(
                progress = { secondsLeft.toFloat() / TapGame.DURATION_SECONDS },
                modifier = Modifier.fillMaxWidth(),
                drawStopIndicator = {},
            )
            Text(
                text = stringResource(R.string.game_score, score, secondsLeft),
                style = MaterialTheme.typography.titleMedium,
            )
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.surface,
                        ),
                    ),
                ),
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

            if (!running) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    lastScore?.let { previous ->
                        ElevatedCard {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text = stringResource(R.string.game_result, previous),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    text = stringResource(
                                        R.string.game_reward,
                                        TapGame.moodGain(previous).roundToInt(),
                                        TapGame.coins(previous),
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    Button(
                        onClick = { running = true },
                        enabled = snapshot.acceptsInteraction,
                    ) {
                        Text(
                            stringResource(
                                if (lastScore == null) R.string.game_start else R.string.game_again,
                            ),
                        )
                    }
                    if (!snapshot.acceptsInteraction) {
                        Text(
                            text = stringResource(R.string.game_busy),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }

        Text(
            text = stringResource(R.string.game_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(2.dp))
    }
}

@Composable
private fun TargetBubble(
    target: Target,
    offsetX: androidx.compose.ui.unit.Dp,
    offsetY: androidx.compose.ui.unit.Dp,
    onTap: () -> Unit,
) {
    // Pop in rather than appear: a target that materialises at full size is
    // easy to miss at the edge of vision.
    var appeared by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (appeared) 1f else 0.3f, label = "target-pop")
    LaunchedEffect(Unit) { appeared = true }

    Box(
        modifier = Modifier
            .offset(x = offsetX, y = offsetY)
            .size(TARGET_SIZE_DP.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .clickable(onClick = onTap),
        contentAlignment = Alignment.Center,
    ) {
        Text(target.emoji, style = MaterialTheme.typography.headlineSmall)
    }
}

private const val FRAME_MILLIS = 50L
private val TARGET_EMOJI = listOf("💜", "⭐", "🍬", "🌸", "🎀")
