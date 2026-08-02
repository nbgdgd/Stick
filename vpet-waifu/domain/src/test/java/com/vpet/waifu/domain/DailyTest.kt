package com.vpet.waifu.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val DAY = Events.MILLIS_PER_DAY

/** Noon on a day boundary, so a "+1 day" in a test is exactly one day index on. */
private val T0 = Events.dayOf(1_700_373_600_000L) * DAY + 12 * 60 * 60 * 1000L

/**
 * The reason to come back tomorrow.
 *
 * The game paid for everything except turning up, which meant a player who
 * missed a day had no particular reason to open it on the next one. The streak
 * is the answer, and the comeback branch is what stops the answer being cruel:
 * the arithmetic of a day away is a starving, miserable pet, and being met by
 * that is how people stop playing pet games for good.
 */
class DailyTest {

    private val sim = PetSimulation()

    private fun pet(at: Long = T0) = PetSnapshot(
        stats = PetStats(80f, 80f, 70f),
        lastTickAt = at,
        lastInteractionAt = at,
        passiveSince = at,
        bornAt = at,
    )

    @Test
    fun `the first check-in pays day one and starts the streak`() {
        val before = pet().progress.money

        val claimed = sim.claimDaily(pet(), T0)

        assertEquals(1, claimed.streakDays)
        assertEquals(1, claimed.bestStreak)
        assertEquals(Events.dayOf(T0), claimed.lastLoginDay)
        assertEquals(sim.dailyReward(1), claimed.pendingDaily)
        assertEquals(before + sim.dailyReward(1), claimed.progress.money)
        // It is earnings, not a gift from nowhere — the profile counts it.
        assertEquals(sim.dailyReward(1), claimed.totalEarned)
    }

    @Test
    fun `opening the app again the same day pays nothing`() {
        val once = sim.claimDaily(pet(), T0)

        val twice = sim.claimDaily(once, T0 + 6 * 60 * 60 * 1000L)

        assertEquals(once.progress.money, twice.progress.money)
        assertEquals(1, twice.streakDays)
        assertEquals(1, twice.journal.count { it.kind == JournalKind.DAILY })
    }

    @Test
    fun `a week of days in a row climbs to the cap and stops there`() {
        var state = pet()
        val paid = (0 until 9).map { day ->
            state = sim.claimDaily(state, T0 + day * DAY)
            state.pendingDaily
        }

        assertEquals(listOf(225, 300, 375, 450, 525, 600, 675, 675, 675), paid)
        assertEquals(9, state.streakDays)
        assertEquals(675, sim.dailyReward(PetSimulation.DAILY_STREAK_CAP))
    }

    @Test
    fun `a missed day starts the run again, but the best is remembered`() {
        var state = pet()
        repeat(4) { state = sim.claimDaily(state, T0 + it * DAY) }
        assertEquals(4, state.streakDays)

        val afterGap = sim.claimDaily(state, T0 + 6 * DAY)

        assertEquals(1, afterGap.streakDays)
        assertEquals(4, afterGap.bestStreak)
        assertEquals(sim.dailyReward(1), afterGap.pendingDaily)
    }

    @Test
    fun `the check-in writes a diary line and a card to dismiss`() {
        val claimed = sim.claimDaily(pet(), T0)

        val entry = claimed.journal.last { it.kind == JournalKind.DAILY }
        assertEquals(sim.dailyReward(1), entry.amount)
        assertEquals("1", entry.detail)
        assertTrue(claimed.pendingDaily > 0)
        assertEquals(0, sim.acknowledgeDaily(claimed).pendingDaily)
    }

    // --- coming home ---------------------------------------------------------

    @Test
    fun `a day away is a reunion, not a corpse`() {
        val away = pet()
        val backAt = T0 + 2 * DAY

        val abandoned = sim.advanceTo(away, backAt)
        val greeted = sim.claimDaily(away, backAt)

        assertTrue("the simulation should have run her down", abandoned.stats.hunger < 20f)
        assertTrue(greeted.stats.hunger >= PetSimulation.COMEBACK_FLOOR)
        assertTrue(greeted.stats.mood >= PetSimulation.COMEBACK_FLOOR)
        assertTrue(greeted.journal.any { it.kind == JournalKind.COMEBACK })
        assertEquals(Bond.COMEBACK, greeted.bondPoints)
    }

    @Test
    fun `she is fine, not fresh — the floor never lifts a pet who was fed`() {
        val cared = pet().copy(stats = PetStats(95f, 95f, 95f))

        val greeted = sim.claimDaily(cared, T0 + 2 * DAY)

        // The catch-up has already dropped her from 95; what matters is that
        // the comeback did not hand her back a full bar she had not earned.
        assertTrue(greeted.stats.hunger <= 95f)
        assertTrue(greeted.stats.hunger >= PetSimulation.COMEBACK_FLOOR)
    }

    @Test
    fun `a check-in the next morning is not a comeback`() {
        val overnight = sim.claimDaily(pet(), T0 + 14 * 60 * 60 * 1000L)

        assertFalse(overnight.journal.any { it.kind == JournalKind.COMEBACK })
        assertEquals(0, overnight.bondPoints)
    }

    @Test
    fun `the comeback bond obeys the daily cap like every other kindness`() {
        val capped = pet().copy(bondDay = Events.dayOf(T0 + 2 * DAY), bondToday = Bond.DAILY_CAP)

        val greeted = sim.claimDaily(capped, T0 + 2 * DAY)

        assertEquals(0, greeted.bondPoints)
    }
}
