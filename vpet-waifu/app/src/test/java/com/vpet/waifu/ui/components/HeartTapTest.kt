package com.vpet.waifu.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
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
}
