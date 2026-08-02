package com.vpet.waifu.ui.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cookie
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LocalFlorist
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vpet.waifu.R
import com.vpet.waifu.ui.theme.Accents
import com.vpet.waifu.ui.theme.StatColors
import com.vpet.waifu.ui.theme.Surfaces
import kotlinx.coroutines.delay
import kotlin.random.Random

/** The four pads, each its own colour *and* its own shape. */
private enum class Pad(val tint: Color, @DrawableRes val art: Int) {
    HEART(Color(0xFFF477B8), R.drawable.art_pad_heart),
    STAR(Color(0xFFF0C860), R.drawable.art_pad_star),
    FLOWER(Color(0xFF8FCE73), R.drawable.art_pad_flower),
    COOKIE(Color(0xFF7FD1E8), R.drawable.art_pad_cookie),
}

/** Sequence length at difficulty [level], capped so the show phase stays watchable. */
internal fun sequenceLength(level: Int): Int = (3 + level).coerceAtMost(8)

/** How long each pad stays lit while showing. Shrinks as the sequences grow. */
internal fun showMillis(length: Int): Long = (460L - (length - 3) * 45L).coerceAtLeast(230L)

/** A finished sequence is worth its own length, so later ones pay more. */
internal fun sequenceScore(length: Int): Int = length

internal const val MEMORY_LIVES = 3

private enum class Phase { SHOWING, INPUT, WRONG }

/**
 * "Память" — watch the order, repeat the order.
 *
 * The third verb in the arcade. Catching things is reflex and aim; the rhythm
 * game is reflex and timing; this one has no reflex in it at all — you may take
 * as long as you like over each pad, and the only thing being asked is whether
 * you remember. A round of this is restful in a way the other two are not,
 * which is what stops the arcade being three flavours of the same minute.
 *
 * The state machine is deliberately driven by one counter: `attempt` starts a
 * new sequence, and `level` decides how long it is. Making the sequence effect
 * key on `level` instead — the obvious first cut — meant a wrong answer both
 * lowered the difficulty *and* restarted the sequence in the same frame, so the
 * "wrong!" banner was replaced before it could be read.
 */
@Composable
internal fun MemoryBoard(
    running: Boolean,
    seed: Long,
    onScore: (Int) -> Unit,
    onCorrect: () -> Unit,
    onWrong: () -> Unit,
    onOutOfLives: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sequence = remember(seed) { mutableStateListOf<Pad>() }
    var level by remember(seed) { mutableIntStateOf(0) }
    var attempt by remember(seed) { mutableIntStateOf(0) }
    var lit by remember(seed) { mutableStateOf<Pad?>(null) }
    var phase by remember(seed) { mutableStateOf(Phase.SHOWING) }
    var typed by remember(seed) { mutableIntStateOf(0) }
    var lives by remember(seed) { mutableIntStateOf(MEMORY_LIVES) }
    val random = remember(seed) { Random(seed) }

    LaunchedEffect(running, seed, attempt) {
        if (!running) return@LaunchedEffect
        phase = Phase.SHOWING
        typed = 0
        lit = null
        sequence.clear()
        repeat(sequenceLength(level)) { sequence += Pad.entries[random.nextInt(Pad.entries.size)] }

        delay(520)
        val hold = showMillis(sequence.size)
        sequence.forEach { pad ->
            lit = pad
            delay(hold)
            lit = null
            // The gap matters as much as the flash: without it the same pad
            // twice running is indistinguishable from one long press.
            delay(170)
        }
        phase = Phase.INPUT
    }

    // Recovery from a mistake, off the click handler so the banner is readable.
    LaunchedEffect(phase, attempt) {
        if (phase != Phase.WRONG) return@LaunchedEffect
        delay(950)
        if (lives <= 0) onOutOfLives() else attempt++
    }

    Column(
        modifier = modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(
                    when (phase) {
                        Phase.SHOWING -> R.string.game_memory_watch
                        Phase.INPUT -> R.string.game_memory_repeat
                        Phase.WRONG -> R.string.game_memory_wrong
                    },
                ),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (phase == Phase.WRONG) StatColors.Hunger else Accents.Text,
            )
            Text(
                text = "♥".repeat(lives.coerceAtLeast(0)),
                style = MaterialTheme.typography.titleMedium,
                color = StatColors.Mood,
            )
        }

        // Two rows of two: the largest grid that stays inside one thumb's reach
        // in portrait, which is what this is played with.
        Pad.entries.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                row.forEach { pad ->
                    PadButton(
                        pad = pad,
                        lit = lit == pad,
                        enabled = running && phase == Phase.INPUT,
                        modifier = Modifier.weight(1f),
                        onTap = {
                            if (sequence.getOrNull(typed) == pad) {
                                typed++
                                lit = pad
                                onCorrect()
                                if (typed >= sequence.size) {
                                    onScore(sequenceScore(sequence.size))
                                    level++
                                    attempt++
                                }
                            } else {
                                lives--
                                phase = Phase.WRONG
                                lit = null
                                // Drop back a step rather than ending it: three
                                // lives and a shorter sequence is a slope; a
                                // hard stop on the first slip is a cliff.
                                level = (level - 1).coerceAtLeast(0)
                                onWrong()
                            }
                        },
                    )
                }
            }
        }
    }

    // The lit flash from a correct press clears itself.
    LaunchedEffect(typed) {
        if (phase != Phase.INPUT || lit == null) return@LaunchedEffect
        delay(160)
        lit = null
    }
}

@Composable
private fun PadButton(
    pad: Pad,
    lit: Boolean,
    enabled: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val glow by animateFloatAsState(
        targetValue = if (lit) 1f else 0f,
        animationSpec = tween(if (lit) 60 else 260),
        label = "pad-glow",
    )
    val pop = remember { Animatable(1f) }
    LaunchedEffect(lit) {
        if (lit) {
            pop.snapTo(1.06f)
            pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 700f))
        }
    }

    Box(
        modifier = modifier
            .aspectRatio(1.3f)
            .scale(pop.value)
            .clip(RoundedCornerShape(22.dp))
            .background(pad.tint.copy(alpha = 0.12f + glow * 0.55f))
            .border(
                width = if (lit) 2.5.dp else 1.dp,
                color = pad.tint.copy(alpha = 0.35f + glow * 0.6f),
                shape = RoundedCornerShape(22.dp),
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                onClick = onTap,
            )
            .alpha(if (enabled || lit) 1f else 0.7f),
        contentAlignment = Alignment.Center,
    ) {
        Image(
                painter = painterResource(pad.art),
                contentDescription = null,
                modifier = Modifier.size(38.dp),
            )
    }
}
