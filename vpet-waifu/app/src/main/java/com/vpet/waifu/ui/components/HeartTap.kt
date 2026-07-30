package com.vpet.waifu.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.vpet.waifu.ui.theme.StatColors

/**
 * The little hearts that come off anything you tap.
 *
 * Tapping is the cheapest thing a player can do, so it is the thing that has to
 * feel best: a tap anywhere she can hear it is worth a point or two of mood, a
 * click, and three hearts drifting up off your finger. The hearts are drawn at
 * the exact touch point rather than at the centre of the thing tapped — a burst
 * that always appears in the same place reads as an animation playing, not as a
 * response to *you*.
 */
class HeartTapState internal constructor() {
    internal val pops = mutableStateListOf<HeartPop>()
    private var nextId = 0L

    /** Fires a burst at [at], in the coordinate space of the [HeartLayer]. */
    fun pop(at: Offset) {
        // A cap, because a fast tapper can otherwise pile up a hundred live
        // animations and drop the frame rate they were meant to celebrate.
        if (pops.size >= MAX_LIVE) pops.removeAt(0)
        pops += HeartPop(nextId++, at)
    }

    private companion object {
        const val MAX_LIVE = 12
    }
}

@Composable
fun rememberHeartTapState(): HeartTapState = remember { HeartTapState() }

internal data class HeartPop(val id: Long, val at: Offset)

/**
 * A box whose empty space is tappable for hearts.
 *
 * Children keep their own click handling — a button inside consumes the tap
 * before this sees it — so this can be wrapped around a whole card without
 * stealing its buy button.
 */
@Composable
fun HeartTap(
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val hearts = rememberHeartTapState()
    Box(
        modifier = modifier.pointerInput(enabled) {
            if (!enabled) return@pointerInput
            detectTapGestures { offset ->
                onTap()
                hearts.pop(offset)
            }
        },
    ) {
        content()
        HeartLayer(hearts)
    }
}

/** Draws whatever [state] has going, over everything else in the box. */
@Composable
fun BoxScope.HeartLayer(state: HeartTapState) {
    state.pops.forEach { pop ->
        key(pop.id) { FloatingHearts(pop) { state.pops.remove(pop) } }
    }
}

@Composable
private fun FloatingHearts(pop: HeartPop, onDone: () -> Unit) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(durationMillis = 820, easing = LinearOutSlowInEasing))
        onDone()
    }

    Canvas(Modifier.fillMaxSize()) {
        val p = progress.value
        if (p >= 1f) return@Canvas
        repeat(3) { i ->
            // Staggered so they leave in a stream, and fanned so they do not
            // simply stack on one vertical line.
            val own = ((p - i * 0.13f) / (1f - i * 0.13f)).coerceIn(0f, 1f)
            if (own <= 0f) return@repeat
            val drift = (i - 1) * 13.dp.toPx()
            val at = Offset(
                pop.at.x + drift * own + kotlin.math.sin(own * 7f + i) * 3.dp.toPx(),
                // A short rise: the tiles these come off are 54dp inside a
                // clipped card, and a heart that climbs further is a heart that
                // gets guillotined by the card's top edge. The quadratic fade
                // has it nearly gone by two-thirds of the way up anyway.
                pop.at.y - own * 38.dp.toPx(),
            )
            // Pops out to full size in the first fifth, then shrinks as it goes.
            val scale = if (own < 0.2f) own / 0.2f else 1f - (own - 0.2f) * 0.45f
            val alpha = (1f - own).coerceIn(0f, 1f).let { it * it }
            drawHeart(at, 7.dp.toPx() * scale, StatColors.Mood.copy(alpha = alpha * 0.95f))
        }
    }
}

private fun DrawScope.drawHeart(center: Offset, radius: Float, color: Color) {
    if (radius <= 0f) return
    val path = Path().apply {
        moveTo(center.x, center.y + radius * 0.85f)
        cubicTo(
            center.x - radius * 1.65f, center.y - radius * 0.25f,
            center.x - radius * 0.55f, center.y - radius * 1.35f,
            center.x, center.y - radius * 0.35f,
        )
        cubicTo(
            center.x + radius * 0.55f, center.y - radius * 1.35f,
            center.x + radius * 1.65f, center.y - radius * 0.25f,
            center.x, center.y + radius * 0.85f,
        )
        close()
    }
    drawPath(path, color)
}
