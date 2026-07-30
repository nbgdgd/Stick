package com.vpet.waifu.ui.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.vpet.waifu.domain.MiniGame
import com.vpet.waifu.domain.PetSnapshot
import com.vpet.waifu.ui.theme.VPetTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val T0 = 1_700_000_000_000L

/**
 * The half of "she is stuck playing" that lives in the UI.
 *
 * The round is owned by a coroutine inside the screen. Leaving the tab cancels
 * it, and before the fix that cancellation simply dropped the round on the
 * floor: the pet stayed PLAYING and every other action in the app was locked
 * out behind it. These tests mount the real screen and take it away mid-round.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GameScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val idle = PetSnapshot.initial(T0)

    /** Mounts the screen behind a switch that can rip it out of the tree. */
    private fun mount(
        onStart: () -> Unit = {},
        onFinish: (Int, MiniGame) -> Unit = { _, _ -> },
    ): () -> Unit {
        var visible by mutableStateOf(true)
        // The character animates off `withFrameNanos` forever, so the clock has
        // to be driven by hand or `waitForIdle` would never return.
        compose.mainClock.autoAdvance = false
        compose.setContent {
            VPetTheme {
                if (visible) {
                    GameScreen(
                        snapshot = idle,
                        nowMillis = T0,
                        onStart = onStart,
                        onFinish = onFinish,
                    )
                }
            }
        }
        return { visible = false }
    }

    private fun startRound() {
        compose.onNodeWithText("Start").performClick()
        // Far enough in to have spawned targets, nowhere near the 20s bell.
        compose.mainClock.advanceTimeBy(2_000)
    }

    @Test
    fun `leaving the tab mid-round settles it instead of abandoning it`() {
        var finished: Int? = null
        val leave = mount(onFinish = { score, _ -> finished = score })

        startRound()
        assertNull("the round should still be running", finished)

        leave()
        compose.mainClock.advanceTimeBy(100)

        assertEquals("leaving the tab must end the round", 0, finished)
    }

    @Test
    fun `each game in the picker can be chosen, played and settled`() {
        // Three games sharing one round machine: the failure modes are a board
        // that renders but never starts, and a picker that selects a game the
        // round loop then ignores. One mount, three rounds — the harness allows
        // exactly one setContent per test.
        var playedGame: MiniGame? = null
        val leave = mount(onFinish = { _, g -> playedGame = g })

        MiniGame.entries.forEach { game ->
            playedGame = null
            compose.onNodeWithText(pickerLabel(game)).performClick()
            compose.mainClock.advanceTimeBy(200)
            compose.onNodeWithText(startLabel()).performClick()
            compose.mainClock.advanceTimeBy(1_500)
            assertNull("$game should still be running", playedGame)

            compose.onNodeWithText("Stop").performClick()
            compose.mainClock.advanceTimeBy(200)
            assertEquals("$game must settle when it is stopped", game, playedGame)
        }

        leave()
    }

    /** The button says "Start" until a round has been played, then "Play again". */
    private fun startLabel(): String =
        runCatching { compose.onNodeWithText("Start").assertExists(); "Start" }
            .getOrDefault("Play again")

    private fun pickerLabel(game: MiniGame) = when (game) {
        MiniGame.CATCH -> "Catch"
        MiniGame.RHYTHM -> "Rhythm"
        MiniGame.MEMORY -> "Memory"
    }

    @Test
    fun `the stop button ends the round on the spot`() {
        var started = 0
        var finished: Int? = null
        val leave = mount(onStart = { started++ }, onFinish = { score, _ -> finished = score })

        startRound()
        assertEquals(1, started)

        compose.onNodeWithText("Stop").performClick()
        compose.mainClock.advanceTimeBy(100)

        assertEquals("stop must settle the round", 0, finished)
        // And the screen is back to offering another go rather than stuck.
        compose.onNodeWithText("Play again").assertExists()

        leave()
    }
}
