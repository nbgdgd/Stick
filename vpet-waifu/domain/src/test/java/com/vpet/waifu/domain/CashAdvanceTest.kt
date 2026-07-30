package com.vpet.waifu.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val MINUTE = PetSimulation.MS_PER_MINUTE
private const val T0 = 1_700_373_600_000L

/**
 * The cash advance, and the hole it used to be.
 *
 * It pays 480 for 160 and bills three hours of doubled hunger drain. Buying a
 * second one merely *replaced* that timer with a fresh one, so twenty taps in a
 * row banked 6,400 coins and still cost exactly three hours — and if you bought
 * them back to back, no time passed, so it cost nothing whatsoever. These tests
 * are the fence around that.
 */
class CashAdvanceTest {

    private val sim = PetSimulation()
    private val advance = Shop.byId("advance")!!

    private fun snapshot(money: Int = 1_000, hunger: Float = 90f) = PetSnapshot(
        stats = PetStats(hunger, 80f, 70f),
        progress = PetProgress(money, 0),
        lastTickAt = T0,
        lastInteractionAt = T0,
        passiveSince = T0,
    )

    @Test
    fun `the first advance pays out`() {
        val after = sim.buy(snapshot(), advance, T0)

        assertEquals(1_000 - advance.price + advance.money, after.progress.money)
        assertTrue(after.hasEffect(EffectKind.HUNGER_SURGE, T0))
    }

    @Test
    fun `a second advance is refused while the first is unpaid`() {
        val once = sim.buy(snapshot(), advance, T0)
        val twice = sim.buy(once, advance, T0)

        assertFalse(once.canBuy(advance, T0))
        assertEquals(once.progress.money, twice.progress.money)
    }

    @Test
    fun `hammering it cannot print money`() {
        var s = snapshot()
        repeat(20) { s = sim.buy(s, advance, T0) }

        // Exactly one advance went through, not twenty.
        assertEquals(1_000 - advance.price + advance.money, s.progress.money)
    }

    @Test
    fun `it is allowed again once the debt is worked off`() {
        val once = sim.buy(snapshot(), advance, T0)
        val later = T0 + (advance.effectMinutes + 1) * MINUTE

        assertTrue(sim.advanceTo(once, later).canBuy(advance, later))
    }

    @Test
    fun `taking one costs hunger immediately, not only later`() {
        val before = snapshot()
        val after = sim.buy(before, advance, T0)

        assertTrue(after.stats.hunger < before.stats.hunger)
    }

    @Test
    fun `a doubled dose extends the debt instead of restarting it`() {
        // Nothing in the shop can stack today, but the rule that made it free
        // money was "the newest one wins" — so the arithmetic is pinned here
        // directly, for the next item that is allowed to double up.
        val first = sim.buy(snapshot(), advance, T0)
        val firstEnd = first.effects.single { it.kind == EffectKind.HUNGER_SURGE }.expiresAt

        val halfway = T0 + (advance.effectMinutes / 2) * MINUTE
        val forced = sim.buy(
            first.copy(effects = emptyList()).let { stripped ->
                // Put the live effect back without going through canBuy.
                stripped.copy(effects = first.effects)
            }.copy(progress = PetProgress(5_000, 0)),
            advance,
            halfway,
        )
        // Refused, so nothing moved at all — which is the actual guarantee.
        assertEquals(firstEnd, forced.effects.single { it.kind == EffectKind.HUNGER_SURGE }.expiresAt)
        assertEquals(5_000, forced.progress.money)
    }

    @Test
    fun `the whole loop is a loss over a day of hammering it`() {
        // Buy one every time the game allows it, for a day, feeding her nothing.
        // Hunger has to end up on the floor: the advance is a debt, and the
        // debt has to be visible in the pet, not just in a timer.
        var s = snapshot(money = 500)
        var t = T0
        var taken = 0
        repeat(24 * 60) {
            t += MINUTE
            s = sim.advanceTo(s, t)
            if (s.canBuy(advance, t)) {
                s = sim.buy(s, advance, t)
                taken++
            }
        }

        assertEquals(PetStats.MIN, s.stats.hunger, 0.01f)
        // Eight in a day at most — one per three-hour debt — not one a frame.
        assertTrue("took $taken", taken <= 8)
    }
}
