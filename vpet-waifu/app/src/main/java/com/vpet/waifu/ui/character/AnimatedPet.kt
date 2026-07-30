package com.vpet.waifu.ui.character

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import com.vpet.waifu.domain.PetState

/**
 * The pet, animating.
 *
 * The clock comes from `withFrameNanos`, so the animation is tied to the
 * display's own frame callback: it runs at whatever the panel refreshes at,
 * costs nothing while the composition is not visible, and stops entirely when
 * the screen turns off.
 */
@Composable
fun AnimatedPet(
    state: PetState,
    modifier: Modifier = Modifier,
    palette: PetPalette = PetPalette.Default,
) {
    var seconds by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) {
            withFrameNanos { now -> seconds = (now - start) / 1_000_000_000f }
        }
    }

    val pose = PetPoseFactory.pose(state, seconds)
    Canvas(modifier) { drawPet(pose, palette) }
}
