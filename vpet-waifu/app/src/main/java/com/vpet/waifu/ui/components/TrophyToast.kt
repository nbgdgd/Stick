package com.vpet.waifu.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vpet.waifu.R
import com.vpet.waifu.ui.Achievement
import com.vpet.waifu.ui.theme.Accents
import com.vpet.waifu.ui.theme.Surfaces
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

/**
 * "You just unlocked something."
 *
 * Deliberately not a system notification and deliberately not a dialog. A
 * system notification for something that happened inside the app the player is
 * looking at is a notification about the present tense; a dialog stops the game
 * to tell you that the game went well. This slides in over the top of whatever
 * screen you are on, says its piece, and leaves — and the screen underneath
 * stays live the whole time, because nothing here needs an answer.
 *
 * [DWELL_MILLIS] is the whole design constraint: long enough to read a short
 * name, short enough that three in a row are not a queue you sit through. A
 * swipe up takes it away early, which is the gesture people already try.
 */
@Composable
fun TrophyToast(
    trophy: Achievement?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Keyed on the trophy so a second unlock landing while the first is still
    // on screen restarts the timer rather than inheriting the remainder of it.
    LaunchedEffect(trophy) {
        if (trophy == null) return@LaunchedEffect
        delay(DWELL_MILLIS)
        onDismiss()
    }

    AnimatedVisibility(
        visible = trophy != null,
        modifier = modifier,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
    ) {
        // Held after the trophy clears so the exit animation has something to
        // draw; without it the card empties a frame before it slides away.
        val shown = trophy ?: return@AnimatedVisibility
        TrophyCard(shown, onDismiss)
    }
}

@Composable
private fun TrophyCard(trophy: Achievement, onDismiss: () -> Unit) {
    val sparkle by rememberInfiniteTransition(label = "trophy").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2_600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "sparkle",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Surfaces.Card)
            .border(1.dp, trophy.tint.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
            .pointerInput(trophy) {
                detectVerticalDragGestures { _, dragAmount ->
                    if (dragAmount < -4f) onDismiss()
                }
            }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                // The sparks ring the icon rather than the screen: a burst of
                // confetti across a phone for a tenth trophy is a celebration
                // nobody asked for the tenth time.
                .drawBehind { drawSparks(sparkle, trophy.tint) }
                .clip(CircleShape)
                .background(trophy.tint.copy(alpha = 0.18f))
                .border(1.dp, trophy.tint.copy(alpha = 0.7f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = trophy.icon,
                contentDescription = null,
                tint = trophy.tint,
                modifier = Modifier.size(21.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = stringResource(R.string.trophy_unlocked),
                style = MaterialTheme.typography.labelSmall,
                color = trophy.tint,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(trophy.titleRes),
                style = MaterialTheme.typography.titleSmall,
                color = Accents.Text,
                maxLines = 2,
            )
        }
    }
}

/**
 * A ring of sparks, turning slowly.
 *
 * Drawn rather than animated as particles because there are eight of them for
 * three seconds — a particle system for that is machinery nobody needs, and
 * this is one `drawBehind` that costs a handful of circles a frame.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSparks(phase: Float, tint: Color) {
    val centre = Offset(size.width / 2f, size.height / 2f)
    val radius = size.minDimension * 0.62f
    repeat(SPARKS) { i ->
        val turn = (phase + i.toFloat() / SPARKS) % 1f
        val angle = turn * 2f * Math.PI.toFloat()
        // Each spark fades in and out over its own trip round, so the ring
        // shimmers instead of spinning like a loading spinner.
        val alpha = (sin(turn * Math.PI.toFloat()) * 0.7f).coerceIn(0f, 1f)
        drawCircle(
            color = tint.copy(alpha = alpha),
            radius = size.minDimension * 0.045f,
            center = centre + Offset(cos(angle) * radius, sin(angle) * radius),
        )
    }
}

private const val SPARKS = 8
private const val DWELL_MILLIS = 3_000L
