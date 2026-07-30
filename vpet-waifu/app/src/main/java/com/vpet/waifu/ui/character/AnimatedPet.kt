package com.vpet.waifu.ui.character

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
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
    animated: Boolean = true,
) {
    var seconds by remember { mutableFloatStateOf(0f) }

    if (animated) {
        LaunchedEffect(Unit) {
            val start = withFrameNanos { it }
            while (true) {
                withFrameNanos { now -> seconds = (now - start) / 1_000_000_000f }
            }
        }
    }

    val pose = PetPoseFactory.pose(state, if (animated) seconds else STILL_FRAME_SECONDS)
    Canvas(modifier) { drawPet(pose, palette) }
}

/**
 * A single frame of the character, rasterised.
 *
 * The home-screen widget cannot run Compose, so it gets a bitmap drawn by the
 * exact same renderer — one source of truth for the art instead of a parallel
 * set of static drawables that would drift out of sync.
 */
fun renderPetBitmap(
    state: PetState,
    widthPx: Int,
    heightPx: Int,
    density: Float,
    palette: PetPalette = PetPalette.Default,
): Bitmap {
    val target = ImageBitmap(
        width = widthPx.coerceAtLeast(1),
        height = heightPx.coerceAtLeast(1),
        config = ImageBitmapConfig.Argb8888,
    )
    CanvasDrawScope().draw(
        density = Density(density),
        layoutDirection = LayoutDirection.Ltr,
        canvas = Canvas(target),
        size = Size(widthPx.toFloat(), heightPx.toFloat()),
    ) {
        drawPet(PetPoseFactory.pose(state, STILL_FRAME_SECONDS), palette)
    }
    return target.asAndroidBitmap()
}

/**
 * The instant a still frame is sampled at. Picked to miss the blink spikes and
 * to catch each animation mid-gesture rather than at rest.
 */
private const val STILL_FRAME_SECONDS = 1.2f
