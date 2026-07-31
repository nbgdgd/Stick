package com.vpet.waifu.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

private const val MINUTE = PetSimulation.MS_PER_MINUTE
private const val T0 = 1_700_373_600_000L

/**
 * The boosts: timed help that can be bought while she is on the clock.
 *
 * Their whole reason to exist is the moment mid-shift when the grind drags —
 * so the first thing pinned here is that the shop stays open for them while
 * she works, and the second is that each one actually does what its label
 * says, measured against an identical run without it.
 */
class BoostsTest {

    private val sim = PetSimulation()
    private val shift = Occupations.byId("shop")!!
    private val lesson = Occupations.byId("school")!!

    private val haste = Shop.byId("haste_shot")!!
    private val overtime = Shop.byId("overtime_pass")!!
    private val focusTea = Shop.byId("focus_tea")!!
    private val secondWind = Shop.byId("second_wind")!!
    private val goodVibes = Shop.byId("good_vibes")!!

    /** Rich, experienced, cared for — and with the tip jar parked for the day. */
    private fun snapshot(mood: Float = 70f) = PetSnapshot(
        stats = PetStats(95f, 95f, mood),
        progress = PetProgress(money = 2_000, exp = 5_000),
        lastTickAt = T0,
        lastInteractionAt = T0,
        passiveSince = T0,
        passiveDay = Events.dayOf(T0),
        passivePaidToday = 1_000_000,
    )

    /** Ticks minute by minute, returning the state at [minutes] past T0. */
    private fun runFor(start: PetSnapshot, minutes: Int): PetSnapshot {
        var s = start
        for (m in 1..minutes) s = sim.advanceTo(s, T0 + m * MINUTE)
        return s
    }

    private fun firstFreeMinute(start: PetSnapshot, limit: Int): Int {
        var s = start
        for (m in 1..limit) {
            s = sim.advanceTo(s, T0 + m * MINUTE)
            if (s.activity != PetActivity.WORKING) return m
        }
        return limit
    }

    // --- the door stays open ------------------------------------------------

    @Test
    fun `boosts can be bought while she is on a shift`() {
        val working = sim.startOccupation(snapshot(), shift, T0)
        assertEquals(PetActivity.WORKING, working.activity)

        assertTrue(working.canBuy(secondWind, T0))
        assertTrue(working.canBuy(haste, T0))
        // Food is still locked behind her being free — that rule must survive.
        val ramen = Shop.byId("ramen")!!
        assertFalse(working.canBuy(ramen, T0))
        assertEquals(PurchaseBlock.BUSY, working.blockedBy(ramen, T0))
    }

    @Test
    fun `buying a boost does not interrupt the shift`() {
        val working = sim.startOccupation(snapshot(), shift, T0)
        val boosted = sim.buy(working, secondWind, T0)

        assertEquals(PetActivity.WORKING, boosted.activity)
        assertNotNull(boosted.session)
        assertTrue(boosted.hasEffect(EffectKind.SECOND_WIND, T0))
    }

    @Test
    fun `a running boost refuses a second dose until it ends`() {
        val boosted = sim.buy(snapshot(), goodVibes, T0)

        assertFalse(boosted.canBuy(goodVibes, T0))
        assertEquals(PurchaseBlock.STILL_PAYING, boosted.blockedBy(goodVibes, T0))

        val after = T0 + (goodVibes.effectMinutes + 1) * MINUTE
        assertTrue(sim.advanceTo(boosted, after).canBuy(goodVibes, after))
    }

    // --- each boost does what the label says --------------------------------

    @Test
    fun `haste finishes the shift in half the time at full pay`() {
        val baseline = sim.startOccupation(snapshot(), shift, T0)
        val hasted = sim.buy(baseline, haste, T0)

        // A 60-minute shift under a 30-minute haste: every covered minute
        // counts double, so the bell rings at the 30-minute mark.
        val doneAt = firstFreeMinute(hasted, limit = 70)
        assertTrue("finished at minute $doneAt", doneAt in 29..31)
        assertEquals(shift.durationMinutes.toLong(), 60L)

        // …and at the full shift's pay, not half of it. The haste run spent
        // its price up front, so compare what the shift itself brought in.
        val baselineEnd = runFor(baseline, 65)
        val hastedEnd = runFor(hasted, 65)
        val earnedBaseline = baselineEnd.progress.money - 2_000
        val earnedHasted = hastedEnd.progress.money - (2_000 - haste.price)
        assertTrue(
            "haste paid $earnedHasted vs $earnedBaseline",
            abs(earnedHasted - earnedBaseline) <= 8,
        )
    }

    @Test
    fun `overtime pays half again on the minutes it covers`() {
        val baseline = runFor(sim.startOccupation(snapshot(), shift, T0), 65)
        val boosted = runFor(
            sim.buy(sim.startOccupation(snapshot(), shift, T0), overtime, T0),
            65,
        )

        val earnedBaseline = baseline.progress.money - 2_000
        val earnedBoosted = boosted.progress.money - (2_000 - overtime.price)
        // 45 of the 60 minutes at ×1.5 is ~38% more for the whole shift.
        assertTrue(
            "overtime paid $earnedBoosted vs $earnedBaseline",
            earnedBoosted >= earnedBaseline + 50,
        )
    }

    @Test
    fun `focus tea grows study EXP by half`() {
        val baseline = runFor(sim.startOccupation(snapshot(), lesson, T0), 35)
        val boosted = runFor(
            sim.buy(sim.startOccupation(snapshot(), lesson, T0), focusTea, T0),
            35,
        )

        val expBaseline = baseline.progress.exp - 5_000
        val expBoosted = boosted.progress.exp - 5_000
        // The whole 30-minute lesson is covered, so the gap is a clean ×1.5.
        assertTrue(
            "focus earned $expBoosted vs $expBaseline",
            expBoosted >= expBaseline + (expBaseline * 4) / 10,
        )
    }

    @Test
    fun `second wind halves the energy drain`() {
        val baseline = runFor(snapshot(), 60)
        val boosted = runFor(sim.buy(snapshot(), secondWind, T0), 60)

        assertTrue(
            "energy ${boosted.stats.energy} vs ${baseline.stats.energy}",
            boosted.stats.energy > baseline.stats.energy + 5f,
        )
    }

    @Test
    fun `good vibes lift her mood over the hour`() {
        val baseline = runFor(snapshot(mood = 40f), 60)
        val boosted = runFor(sim.buy(snapshot(mood = 40f), goodVibes, T0), 60)

        assertTrue(
            "mood ${boosted.stats.mood} vs ${baseline.stats.mood}",
            boosted.stats.mood > baseline.stats.mood + 10f,
        )
    }

    // --- catalog sanity ------------------------------------------------------

    @Test
    fun `every boost carries a timed effect and nothing instant`() {
        Shop.BOOSTS.forEach { item ->
            assertEquals(ShopCategory.BOOST, item.category)
            val effect = item.effect
            assertNotNull("${item.id} has no effect", effect)
            assertTrue("${item.id} effect is not a boost", effect!! in BOOST_EFFECTS)
            assertTrue("${item.id} has no duration", item.effectMinutes > 0)
            // Boosts change rates, never stats directly — an instant payload
            // would make them food or pills wearing a different label.
            assertEquals(0f, item.hunger, 0f)
            assertEquals(0f, item.energy, 0f)
            assertEquals(0f, item.mood, 0f)
            assertEquals(0, item.money)
            assertEquals(0, item.exp)
        }
    }
}
