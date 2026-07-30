package com.vpet.waifu.ui.character

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs

/**
 * The character's clock.
 *
 * Every pose she has is a function of this one number, so if it stops the
 * character freezes in all eleven states at once while every other animation in
 * the app carries on looking fine — which is exactly how it shipped broken, the
 * frame-pacing gap overflowing on its first comparison and the check never
 * passing again.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AnimatedPetTest {

    @get:Rule
    val compose = createComposeRule()

    private var observed by mutableFloatStateOf(-1f)

    private fun mount() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val seconds = rememberPetPhaseSeconds()
            // Read it in composition so the test can see it; production reads it
            // in the draw lambda, which no assertion can reach.
            observed = seconds.floatValue
        }
        compose.waitForIdle()
    }

    private fun advance(millis: Long) {
        compose.mainClock.advanceTimeBy(millis)
        compose.waitForIdle()
    }

    @Test
    fun `the phase advances with the frame clock`() {
        mount()
        advance(500)

        assertTrue("the character never moved: phase stuck at $observed", observed > 0f)
    }

    @Test
    fun `the phase tracks real time rather than counting frames`() {
        mount()
        advance(2_000)

        // Two seconds of frames should read as about two seconds, whatever the
        // panel's refresh rate happens to be.
        assertTrue("two seconds of frames read as $observed", abs(observed - 2f) < 0.25f)
    }

    @Test
    fun `it keeps going, not just for the first frame`() {
        mount()
        advance(500)
        val early = observed

        advance(1_500)

        assertTrue("stalled after $early", observed > early + 1f)
    }
}
