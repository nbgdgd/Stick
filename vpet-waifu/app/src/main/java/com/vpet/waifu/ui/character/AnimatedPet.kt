package com.vpet.waifu.ui.character

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import com.vpet.waifu.domain.PetState

/**
 * How often the pose clock is allowed to move, in nanoseconds.
 *
 * This is a throttle on *state writes*, not on the display: `withFrameNanos`
 * fires every frame the panel draws either way, and this decides how many of
 * those frames actually invalidate the character.
 *
 * It used to be a flat 30 a second for everything, which on a 120 Hz phone
 * meant the entire interface moved smoothly while the character — the one
 * thing anybody looks at — visibly did not. It reads as the app being locked.
 *
 * The number that justified it was the vector rig's cost per frame, and that
 * cost is now much lower (see the limb outline in PetArt: it used to run four
 * Skia boolean path unions per frame). A sprite costs a single blit and has no
 * business being throttled at all.
 */
private const val RIG_FRAME_INTERVAL_NANOS = 1_000_000_000L / 60
internal const val SPRITE_FRAME_INTERVAL_NANOS = 0L

/**
 * Seconds since this composable appeared, advanced about thirty times a second.
 *
 * Split out from [AnimatedPet] because it is the single point of failure for
 * every animation the character has: if this stops moving she freezes in every
 * state at once, and nothing else in the app looks any different. It is worth
 * being able to test on its own.
 */
@Composable
internal fun rememberPetPhaseSeconds(
    /** Nanoseconds between pose updates; 0 follows the display exactly. */
    intervalNanos: Long = RIG_FRAME_INTERVAL_NANOS,
): FloatState {
    val seconds = remember { mutableFloatStateOf(0f) }

    LaunchedEffect(intervalNanos) {
        val start = withFrameNanos { it }
        // Seeded from `start`, not from a sentinel. `Long.MIN_VALUE` looks like
        // the obvious "nothing yet", but `now - Long.MIN_VALUE` overflows for
        // any positive frame time, so the gap came out negative, the interval
        // check never passed, and the character never moved again.
        var lastFrame = start
        while (true) {
            withFrameNanos { now ->
                if (now - lastFrame >= intervalNanos) {
                    lastFrame = now
                    seconds.floatValue = (now - start) / 1_000_000_000f
                }
            }
        }
    }

    return seconds
}

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
    /** Which job's pantomime to play while she is working or studying. */
    workProp: Prop? = null,
) {
    val seconds = rememberPetPhaseSeconds()

    Canvas(modifier) { drawPet(PetPoseFactory.pose(state, seconds.floatValue, workProp), palette) }
}
