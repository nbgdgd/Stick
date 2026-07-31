package com.vpet.waifu.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.vpet.waifu.ui.theme.VPetTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tapping a category.
 *
 * Every category header, tile and tab is a place to pat her, so the rule that
 * matters is when it counts: she has to be free to notice. Tapping while she is
 * asleep or on a shift must not quietly bank mood she never received.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HeartTapTest {

    @get:Rule
    val compose = createComposeRule()

    private var taps = 0

    private fun mount(enabled: Boolean) {
        compose.setContent {
            VPetTheme {
                HeartTap(
                    onTap = { taps++ },
                    enabled = enabled,
                    modifier = Modifier.size(120.dp).testTag("target"),
                ) {
                    Box(Modifier.size(120.dp))
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun `a tap on a category counts`() {
        mount(enabled = true)

        compose.onNodeWithTag("target").performClick()
        compose.waitForIdle()

        assertEquals(1, taps)
    }

    @Test
    fun `every tap counts, not just the first`() {
        mount(enabled = true)

        repeat(5) {
            compose.onNodeWithTag("target").performClick()
            compose.waitForIdle()
        }

        assertEquals(5, taps)
    }

    @Test
    fun `tapping does nothing while she is busy`() {
        mount(enabled = false)

        compose.onNodeWithTag("target").performClick()
        compose.waitForIdle()

        assertEquals(0, taps)
    }

    /**
     * The hearts must not resize the thing they come off.
     *
     * They did. The burst was a `fillMaxSize` canvas inside the box, so for the
     * second it was alive the box grew to whatever space was on offer — which
     * inside the navigation bar meant the whole screen, and the bar jumped to
     * the top of the display with every panel shoved off the bottom. It only
     * happened while a burst was live, which is exactly why it looked like a
     * bug in switching tabs.
     */
    @Test
    fun `a live burst does not resize its container`() {
        compose.mainClock.autoAdvance = false
        var size by mutableStateOf(IntSize.Zero)
        compose.setContent {
            VPetTheme {
                // A tall parent, so a child that grabs the constraints would be
                // instantly and obviously wrong.
                Box(Modifier.size(300.dp)) {
                    HeartTap(
                        onTap = {},
                        modifier = Modifier
                            .testTag("target")
                            .onGloballyPositioned { size = it.size },
                    ) {
                        Box(Modifier.size(60.dp))
                    }
                }
            }
        }
        compose.waitForIdle()
        val resting = size

        compose.onNodeWithTag("target").performClick()
        compose.mainClock.advanceTimeBy(200)
        compose.waitForIdle()

        assertEquals("the burst grew its container", resting, size)
    }
}
