package com.vpet.waifu.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.vpet.waifu.ui.theme.StatColors
import com.vpet.waifu.ui.theme.VPetTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * "Pay her while she works, and let me see it happen."
 *
 * The simulation pays a minute at a time; this is the half the player actually
 * notices — the minute's wage floating up off her as it lands.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GainPopTest {

    @get:Rule
    val compose = createComposeRule()

    private var total by mutableIntStateOf(0)

    private fun mount() {
        // The rise is a one-shot, so the clock has to be driven by hand: let it
        // run free and every assertion would land after the pop had faded.
        compose.mainClock.autoAdvance = false
        compose.setContent {
            VPetTheme {
                GainPop(total = total, label = "¥", tint = StatColors.Money)
            }
        }
        compose.waitForIdle()
    }

    /** A wage landing. The idle pass is what lets the effect actually start. */
    private fun pay(newTotal: Int) {
        compose.runOnUiThread { total = newTotal }
        compose.waitForIdle()
    }

    private fun advance(millis: Long) {
        compose.mainClock.advanceTimeBy(millis)
        compose.waitForIdle()
    }

    @Test
    fun `a wage landing floats the amount that just arrived`() {
        mount()
        compose.onNodeWithText("+3 ¥").assertDoesNotExist()

        pay(3)
        advance(200)

        compose.onNodeWithText("+3 ¥").assertExists()
    }

    @Test
    fun `it shows the minute's pay, not the running total`() {
        mount()
        pay(3)
        advance(200)
        compose.onNodeWithText("+3 ¥").assertExists()

        // The next minute pays another three; the pop is about the delta.
        pay(6)
        advance(200)

        compose.onNodeWithText("+3 ¥").assertExists()
        compose.onNodeWithText("+6 ¥").assertDoesNotExist()
    }

    @Test
    fun `it clears itself once the rise is over`() {
        mount()
        pay(3)
        advance(200)
        compose.onNodeWithText("+3 ¥").assertExists()

        advance(2_500)

        compose.onNodeWithText("+3 ¥").assertDoesNotExist()
    }

    @Test
    fun `a new shift resetting the counter is not a payout`() {
        mount()
        pay(40)
        advance(2_500)

        // Clocking off and starting again drops the session total back to zero.
        pay(0)
        advance(200)

        compose.onNodeWithText("+0 ¥").assertDoesNotExist()
        compose.onNodeWithText("+-40 ¥").assertDoesNotExist()
    }
}
