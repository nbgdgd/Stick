package com.vpet.waifu.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private const val T0 = 1_700_373_600_000L

/** The arcade finally leaves a mark: one best score per game, only climbing. */
class HighScoreTest {

    private val sim = PetSimulation()

    private fun snapshot() = PetSnapshot(
        stats = PetStats(80f, 80f, 70f),
        progress = PetProgress(0, 0),
        lastTickAt = T0,
        lastInteractionAt = T0,
        passiveSince = T0,
        bornAt = T0,
    )

    private fun play(s: PetSnapshot, game: MiniGame, score: Int, at: Long): PetSnapshot =
        sim.finishPlaying(sim.startPlaying(s, at), score, at + 60_000L, game)

    @Test
    fun `a best score is remembered per game`() {
        var s = play(snapshot(), MiniGame.CATCH, 12, T0)
        s = play(s, MiniGame.RHYTHM, 80, T0 + 10 * 60_000L)

        assertEquals(12, s.bestScores[MiniGame.CATCH])
        assertEquals(80, s.bestScores[MiniGame.RHYTHM])
        assertNull(s.bestScores[MiniGame.MEMORY])
    }

    @Test
    fun `records only climb`() {
        var s = play(snapshot(), MiniGame.CATCH, 20, T0)
        s = play(s, MiniGame.CATCH, 8, T0 + 10 * 60_000L)

        assertEquals(20, s.bestScores[MiniGame.CATCH])

        s = play(s, MiniGame.CATCH, 25, T0 + 20 * 60_000L)
        assertEquals(25, s.bestScores[MiniGame.CATCH])
    }

    @Test
    fun `a scoreless round leaves no record`() {
        val s = play(snapshot(), MiniGame.MEMORY, 0, T0)

        assertNull(s.bestScores[MiniGame.MEMORY])
    }
}
