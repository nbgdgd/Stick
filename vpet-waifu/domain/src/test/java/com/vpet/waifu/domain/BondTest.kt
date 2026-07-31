package com.vpet.waifu.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val MINUTE = PetSimulation.MS_PER_MINUTE
private const val T0 = 1_700_373_600_000L

/**
 * Attachment: the number that only grows.
 *
 * The properties that matter are exactly two — care makes it rise, and nothing
 * whatsoever makes it fall — plus the daily cap that turns it from a grind bar
 * into shared history.
 */
class BondTest {

    private val sim = PetSimulation()

    private fun snapshot(
        money: Int = 500,
        lastInteractionAt: Long = T0 - 10 * MINUTE,
    ) = PetSnapshot(
        stats = PetStats(80f, 80f, 70f),
        progress = PetProgress(money, 0),
        lastTickAt = T0,
        lastInteractionAt = lastInteractionAt,
        passiveSince = T0,
        bornAt = T0,
    )

    @Test
    fun `feeding her bonds`() {
        val after = sim.feed(snapshot(), T0)

        assertEquals(Bond.FEED, after.bondPoints)
    }

    @Test
    fun `a considered pat bonds, a mash does not`() {
        val considered = sim.pet(snapshot(lastInteractionAt = T0 - 10 * MINUTE), T0)
        assertEquals(Bond.PAT, considered.bondPoints)

        val mashed = sim.pet(snapshot(lastInteractionAt = T0), T0)
        assertEquals(0, mashed.bondPoints)
    }

    @Test
    fun `a gift bonds more than a meal`() {
        val flowers = Shop.byId("flowers")!!
        val after = sim.buy(snapshot(), flowers, T0)

        assertEquals(Bond.GIFT, after.bondPoints)
        assertTrue(Bond.GIFT > Bond.FEED)
    }

    @Test
    fun `a finished shift bonds`() {
        val cafe = Occupations.WORK.first()
        var s = sim.startOccupation(snapshot(), cafe, T0)
        s = sim.advanceTo(s, T0 + (cafe.durationMinutes + 1) * MINUTE)

        assertTrue(s.bondPoints >= Bond.SHIFT)
        assertEquals(1, s.shiftsWorked)
    }

    @Test
    fun `one very long evening is worth the same as a good half hour`() {
        // A hundred meals in one sitting: the cap holds.
        var s = snapshot(money = 100_000)
        val onigiri = Shop.byId("onigiri")!!
        repeat(100) { s = sim.buy(s, onigiri, T0 + it * 1_000L) }

        assertEquals(Bond.DAILY_CAP, s.bondPoints)
    }

    @Test
    fun `tomorrow the cap resets`() {
        var s = snapshot(money = 100_000)
        val onigiri = Shop.byId("onigiri")!!
        repeat(20) { s = sim.buy(s, onigiri, T0 + it * 1_000L) }
        val today = s.bondPoints

        val tomorrow = T0 + 24 * 60 * MINUTE
        s = sim.buy(sim.advanceTo(s, tomorrow), onigiri, tomorrow)

        assertTrue(s.bondPoints > today)
    }

    @Test
    fun `nothing in the game lowers it`() {
        // Two days of total neglect: mood collapses, hunger empties, she even
        // falls ill — and the bond does not move a point.
        var s = sim.feed(snapshot(), T0)
        val bonded = s.bondPoints
        s = sim.advanceTo(s, T0 + 2 * 24 * 60 * MINUTE)

        assertEquals(bonded, s.bondPoints)
    }

    @Test
    fun `the levels climb and the titles have room`() {
        assertEquals(0, Bond.levelFor(0))
        assertEquals(1, Bond.levelFor(Bond.pointsForLevel(1)))
        assertEquals(Bond.MAX_LEVEL, Bond.levelFor(Bond.pointsForLevel(Bond.MAX_LEVEL)))
        // Reaching the top takes at least a month of capped days.
        assertTrue(Bond.pointsForLevel(Bond.MAX_LEVEL) / Bond.DAILY_CAP >= 25)
    }

    @Test
    fun `high bond softens neglect but never removes it`() {
        val atMax = Bond.neglectSoftening(Bond.pointsForLevel(Bond.MAX_LEVEL))

        assertTrue(atMax < 1f)
        assertTrue(atMax >= 0.6f)
        assertEquals(1f, Bond.neglectSoftening(0), 0.001f)
    }
}
