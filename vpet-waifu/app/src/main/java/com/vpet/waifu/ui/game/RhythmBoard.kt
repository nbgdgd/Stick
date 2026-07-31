package com.vpet.waifu.ui.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vpet.waifu.ui.theme.Accents
import com.vpet.waifu.ui.theme.StatColors
import kotlin.math.abs
import kotlin.random.Random

/**
 * The beat map for one round.
 *
 * Generated up front from a seed rather than spawned frame by frame, because
 * judging a tap means asking "which note was due closest to *now*" — and that
 * question needs the whole chart, not the two notes currently on screen. It
 * also makes the round reproducible, which is the only way to test it.
 */
internal data class Beat(val atMillis: Long, val lane: Int)

internal fun beatMap(seed: Long, durationSeconds: Int): List<Beat> {
    val random = Random(seed)
    val beats = mutableListOf<Beat>()
    var at = LEAD_IN_MILLIS
    var bpm = 92f
    val end = durationSeconds * 1000L
    var lane = 1
    while (at < end) {
        beats += Beat(at, lane)
        // The tempo creeps up all round, and past the halfway mark some beats
        // split in two — the difficulty ramp is in the chart, not in a
        // multiplier, so it is visible on screen before it is felt.
        bpm = (bpm + 1.6f).coerceAtMost(168f)
        val step = (60_000f / bpm).toLong()
        val split = at > end / 2 && random.nextFloat() < 0.35f
        at += if (split) step / 2 else step
        lane = (lane + 1 + random.nextInt(2)) % 3
    }
    return beats
}

/** How well a tap landed. */
internal enum class Judgement(val points: Int, val windowMillis: Long) {
    PERFECT(3, 85L),
    GREAT(2, 155L),
    GOOD(1, 245L),
    MISS(0, Long.MAX_VALUE),
}

internal fun judge(errorMillis: Long): Judgement {
    val e = abs(errorMillis)
    return when {
        e <= Judgement.PERFECT.windowMillis -> Judgement.PERFECT
        e <= Judgement.GREAT.windowMillis -> Judgement.GREAT
        e <= Judgement.GOOD.windowMillis -> Judgement.GOOD
        else -> Judgement.MISS
    }
}

/** Ten in a row and every note is worth one more. */
internal fun comboBonus(combo: Int): Int = if (combo >= 10) 1 else 0

internal const val LEAD_IN_MILLIS = 1_600L
internal const val APPROACH_MILLIS = 1_150L

private val LANE_TINTS = listOf(
    Color(0xFFF477B8),
    Color(0xFF7FD1E8),
    Color(0xFFF0C860),
)

/**
 * "Ритм" — press on the beat.
 *
 * Rings close on three fixed targets; tap anywhere when one lands. **Where** you
 * tap is deliberately irrelevant — that is the whole point of adding it next to
 * a game about tapping the right spot. The thumb can rest in one place for the
 * entire round and the only thing being measured is *when*.
 */
@Composable
internal fun RhythmBoard(
    running: Boolean,
    seed: Long,
    durationSeconds: Int,
    onScore: (Int) -> Unit,
    onHit: (Judgement) -> Unit,
    modifier: Modifier = Modifier,
) {
    val beats = remember(seed) { beatMap(seed, durationSeconds) }
    // Which beats have already been resolved, by index — a set rather than a
    // mutable copy of the chart, so the chart itself stays immutable.
    val resolved = remember(seed) { mutableSetOf<Int>() }
    var elapsed by remember(seed) { mutableStateOf(0L) }
    var combo by remember(seed) { mutableStateOf(0) }
    // The verdict banner, with a counter beside it: two PERFECTs in a row are
    // the same value, and a LaunchedEffect keyed on the value alone would not
    // restart — the second one would silently not flash.
    var flash by remember(seed) { mutableStateOf<Judgement?>(null) }
    var flashId by remember(seed) { mutableStateOf(0L) }
    val flashAlpha = remember { Animatable(0f) }

    // The round's zero, shared with the tap handler so a tap is judged against
    // the real clock rather than against whatever the last frame happened to
    // write. A frame is 16 ms and the perfect window is 85: reading the stale
    // copy throws away a fifth of the precision the game is measuring.
    var startedAt by remember(seed) { mutableStateOf(0L) }

    LaunchedEffect(running, seed) {
        if (!running) return@LaunchedEffect
        val start = System.nanoTime()
        startedAt = start
        while (true) {
            elapsed = (System.nanoTime() - start) / 1_000_000L
            if (elapsed > durationSeconds * 1000L) break
            // Anything that sailed past the last window is a miss, and misses
            // have to land on their own — waiting for a tap that never comes
            // would let a player keep a combo by simply stopping.
            beats.forEachIndexed { i, beat ->
                if (i !in resolved && elapsed - beat.atMillis > Judgement.GOOD.windowMillis) {
                    resolved += i
                    combo = 0
                    flash = Judgement.MISS
                    flashId++
                    onHit(Judgement.MISS)
                }
            }
            kotlinx.coroutines.delay(16)
        }
    }

    LaunchedEffect(flashId) {
        if (flash == null) return@LaunchedEffect
        flashAlpha.snapTo(1f)
        flashAlpha.animateTo(0f, tween(520, easing = LinearOutSlowInEasing))
    }

    Box(
        modifier = modifier.pointerInput(running, seed) {
            if (!running) return@pointerInput
            detectTapGestures {
                if (startedAt == 0L) return@detectTapGestures
                val now = (System.nanoTime() - startedAt) / 1_000_000L
                val best = beats.indices
                    .filter { it !in resolved }
                    .minByOrNull { abs(beats[it].atMillis - now) }
                    ?: return@detectTapGestures
                val verdict = judge(now - beats[best].atMillis)
                if (verdict == Judgement.MISS) return@detectTapGestures
                resolved += best
                combo++
                flash = verdict
                flashId++
                onScore(verdict.points + comboBonus(combo))
                onHit(verdict)
            }
        },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val targetRadius = 26.dp.toPx()
            val spacing = size.width / 4f
            val y = size.height * 0.42f

            repeat(3) { lane ->
                val centre = Offset(spacing * (lane + 1), y)
                drawCircle(
                    color = LANE_TINTS[lane].copy(alpha = 0.28f),
                    radius = targetRadius,
                    center = centre,
                    style = Stroke(width = 3.dp.toPx()),
                )
            }

            // Every note still in flight, as a ring closing on its target.
            beats.forEachIndexed { i, beat ->
                if (i in resolved) return@forEachIndexed
                val until = beat.atMillis - elapsed
                if (until > APPROACH_MILLIS || until < -Judgement.GOOD.windowMillis) return@forEachIndexed
                val approach = (until.toFloat() / APPROACH_MILLIS).coerceIn(-0.3f, 1f)
                val centre = Offset(spacing * (beat.lane + 1), y)
                val radius = targetRadius * (1f + approach * 2.6f)
                val tint = LANE_TINTS[beat.lane]
                drawCircle(
                    color = tint.copy(alpha = (1f - approach * 0.55f).coerceIn(0f, 1f)),
                    radius = radius,
                    center = centre,
                    style = Stroke(width = 4.dp.toPx()),
                )
                // A filled core once it is nearly there, so the exact instant
                // is readable rather than inferred from a thin outline.
                if (approach < 0.18f) {
                    drawCircle(
                        color = tint.copy(alpha = 0.55f * (1f - approach / 0.18f)),
                        radius = targetRadius * 0.8f,
                        center = centre,
                    )
                }
            }
        }

        flash?.let { verdict ->
            Text(
                text = verdict.label(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = verdict.tint().copy(alpha = flashAlpha.value),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp),
            )
        }
        if (combo >= 3) {
            Text(
                text = "×$combo",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = Accents.Bright.copy(alpha = 0.85f),
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 10.dp, end = 14.dp),
            )
        }
    }
}

private fun Judgement.label(): String = when (this) {
    Judgement.PERFECT -> "PERFECT"
    Judgement.GREAT -> "GREAT"
    Judgement.GOOD -> "GOOD"
    Judgement.MISS -> "MISS"
}

private fun Judgement.tint(): Color = when (this) {
    Judgement.PERFECT -> StatColors.Mood
    Judgement.GREAT -> Accents.Bright
    Judgement.GOOD -> StatColors.Energy
    Judgement.MISS -> StatColors.Hunger
}
