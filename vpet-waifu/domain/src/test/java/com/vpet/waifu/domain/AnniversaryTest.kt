package com.vpet.waifu.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val MINUTE = PetSimulation.MS_PER_MINUTE
private const val T0 = 1_700_373_600_000L
private const val DAY = 24L * 60 * MINUTE

/** The calendar the save file always carried, finally celebrated. */
class AnniversaryTest {

    private val sim = PetSimulation()

    private fun snapshot() = PetSnapshot(
        stats = PetStats(80f, 80f, 70f),
        progress = PetProgress(0, 0),
        lastTickAt = T0,
        lastInteractionAt = T0,
        passiveSince = T0,
        bornAt = T0,
    )

    @Test
    fun `a week together is celebrated with a gift`() {
        val s = sim.advanceTo(snapshot(), T0 + 7 * DAY + MINUTE)

        assertEquals(7, s.celebratedMilestone)
        assertTrue("no gift landed", s.progress.money >= Anniversaries.moneyGift(7))
        assertTrue(s.journal.any { it.kind == JournalKind.ANNIVERSARY && it.amount == 7 })
    }

    @Test
    fun `each milestone lands exactly once`() {
        var s = sim.advanceTo(snapshot(), T0 + 7 * DAY + MINUTE)
        val money = s.progress.money
        s = sim.advanceTo(s, T0 + 8 * DAY)

        assertEquals(7, s.celebratedMilestone)
        assertTrue(s.journal.count { it.kind == JournalKind.ANNIVERSARY } == 1)
        // The tip jar is off during this idle stretch and no gift repeats.
        assertEquals(money, s.progress.money)
    }

    @Test
    fun `a long absence lands only the newest milestone, not the backlog`() {
        // Away past both 7 and 30: `due` picks the largest reached, so the
        // return is one celebration, not a queue of them.
        val s = sim.advanceTo(snapshot(), T0 + 31 * DAY)

        assertEquals(30, s.celebratedMilestone)
        assertEquals(1, s.journal.count { it.kind == JournalKind.ANNIVERSARY })
    }

    @Test
    fun `the milestones climb into years`() {
        assertTrue(Anniversaries.MILESTONES.contains(365))
        assertEquals(Anniversaries.MILESTONES, Anniversaries.MILESTONES.sorted())
    }
}
