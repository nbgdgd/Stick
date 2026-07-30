package com.vpet.waifu.ui.character

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import com.vpet.waifu.domain.PetState

/** ~30 animated frames a second. */
private const val FRAME_INTERVAL_NANOS = 1_000_000_000L / 30

/**
 * The pet, animating.
 *
 * The clock comes from `withFrameNanos`, so the animation is tied to the
 * display's own frame callback: it runs at whatever the panel refreshes at,
 * costs nothing while the composition is not visible, and stops entirely when
 * the screen turns off.
 *
 * Two things keep that cheap. The phase is read inside the draw lambda rather
 * than in composition, so a new frame invalidates the draw phase alone instead
 * of recomposing the whole subtree; and it is only written thirty times a
 * second, because a pose is some thirty `Path`s and a 120 Hz panel would
 * otherwise rebuild all of them four times per frame the eye can see.
 */
@Composable
fun AnimatedPet(
    state: PetState,
    modifier: Modifier = Modifier,
    palette: PetPalette = PetPalette.Default,
) {
    val seconds = remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        var lastFrame = Long.MIN_VALUE
        while (true) {
            withFrameNanos { now ->
                if (now - lastFrame >= FRAME_INTERVAL_NANOS) {
                    lastFrame = now
                    seconds.floatValue = (now - start) / 1_000_000_000f
                }
            }
        }
    }

    Canvas(modifier) { drawPet(PetPoseFactory.pose(state, seconds.floatValue), palette) }
}
