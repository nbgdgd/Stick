package com.vpet.waifu.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The table, and the one property it must never lose.
 *
 * The stake this replaces paid +20% for a good shift and took 10% for a bad
 * one, on an outcome the player controlled — which made it a savings account
 * with a theme. These tests pin the three things that stop the new one from
 * drifting back there: no cell of the payout table is break-even, a loss is a
 * total loss, and a bet cannot be placed with money that does not exist.
 */
class StakesTest {

    private val sim = PetSimulation()
    private val shift = Occupations.byId("office")!!

    private fun snapshot(money: Int) = PetSnapshot(
        stats = PetStats(95f, 95f, 70f),
        progress = PetProgress(money = money, exp = 40_000),
        lastTickAt = T,
        lastInteractionAt = T,
        passiveSince = T,
        passiveDay = Events.dayOf(T),
        passivePaidToday = 1_000_000,
    )

    @Test
    fun `the house keeps an edge at every size and every outcome`() {
        StakeTier.entries.forEach { tier ->
            OutcomeQuality.entries.forEach { quality ->
                val expected = tier.chance(quality) * tier.payout
                assertTrue(
                    "$tier on a $quality shift returns $expected per unit staked",
                    expected < 1f,
                )
            }
        }
    }

    @Test
    fun `the edge widens as the bet grows`() {
        // The friendly tier is the closest to fair; going all in is the worst
        // deal on the board. That ordering is what makes "small" the sensible
        // bet and "all in" a decision rather than an optimisation.
        val best = StakeTier.entries.map { it.chance(OutcomeQuality.GREAT) * it.payout }
        assertEquals(best.sortedDescending(), best)
        assertTrue("all-in should be the worst deal", best.last() < best.first())
    }

    @Test
    fun `caring for her is still worth doing`() {
        // Not a certainty any more, but the difference between her best day and
        // her worst is most of the bet: if it were not, the shift would be
        // decoration around a coin toss.
        StakeTier.entries.forEach { tier ->
            assertTrue(
                "$tier barely rewards a good shift",
                tier.chance(OutcomeQuality.GREAT) > tier.chance(OutcomeQuality.BAD) * 3f,
            )
        }
    }

    @Test
    fun `a losing bet returns nothing, at every size`() {
        StakeTier.entries.forEach { tier ->
            assertEquals(
                0,
                Stakes.settle(1_000, tier, OutcomeQuality.GREAT, roll = 0.9999f),
            )
        }
    }

    @Test
    fun `a winning bet pays the whole multiple, stake included`() {
        StakeTier.entries.forEach { tier ->
            val paid = Stakes.settle(1_000, tier, OutcomeQuality.GREAT, roll = 0f)
            assertEquals(tier.winnings(1_000), paid)
            assertTrue("$tier should be worth winning", tier.profit(1_000) > 0)
        }
    }

    @Test
    fun `nothing on the table exceeds the wallet`() {
        listOf(0, 24, 25, 100, 999, 250_000).forEach { wallet ->
            val snap = snapshot(wallet)
            StakeTier.entries.forEach { tier ->
                val amount = Stakes.amountFor(snap, tier)
                assertTrue("$tier of $wallet came to $amount", amount <= wallet)
            }
        }
    }

    @Test
    fun `a wallet too thin to bet is offered nothing`() {
        assertTrue(Stakes.offered(snapshot(Stakes.MINIMUM - 1)).isEmpty())
        assertTrue(Stakes.offered(snapshot(0)).isEmpty())
        assertTrue(Stakes.offered(snapshot(10_000)).size > 1)
    }

    @Test
    fun `the same bet always settles the same way`() {
        // The roll comes off the session, not off the clock, so closing the app
        // and reopening it — or a catch-up tick running twice — cannot re-spin
        // a bet that has already been placed.
        val a = Stakes.rollOf(startedAt = 1_700_000_000_000L, amount = 400)
        val b = Stakes.rollOf(startedAt = 1_700_000_000_000L, amount = 400)
        assertEquals(a, b, 0f)
        assertNotEquals(a, Stakes.rollOf(startedAt = 1_700_000_060_000L, amount = 400))
        assertTrue(a in 0f..1f)
    }

    @Test
    fun `the rolls spread across the whole range`() {
        // A hash that clumps would quietly turn every bet into a certainty one
        // way or the other, which is the failure this whole file exists to stop.
        val rolls = (0 until 2_000).map { Stakes.rollOf(T + it * 60_000L, 500) }
        val buckets = rolls.groupingBy { (it * 10).toInt().coerceIn(0, 9) }.eachCount()
        assertEquals("every tenth of the range should be hit", 10, buckets.size)
        buckets.forEach { (bucket, count) ->
            assertTrue("bucket $bucket had $count of 2000", count in 120..280)
        }
    }

    @Test
    fun `a bet needs a shift to be riding on`() {
        val idle = snapshot(10_000)
        assertEquals(idle.progress.money, sim.stake(idle, StakeTier.LARGE, T).progress.money)
    }

    @Test
    fun `going all in leaves her with nothing to spend`() {
        val working = sim.startOccupation(snapshot(4_000), shift, T)
        val allIn = sim.stake(working, StakeTier.ALL_IN, T)

        assertEquals(0, allIn.progress.money)
        assertEquals(working.progress.money, allIn.session?.stake)
        // Which is the real cost of the tier: the medicine and the boost that
        // could have saved the shift are now both out of reach.
        assertTrue(Shop.ALL.none { allIn.canBuy(it, T) })
    }

    @Test
    fun `a hammered table drains a fortune`() {
        // Two hundred shifts, every one of them going great, betting medium
        // every time. If this ever comes out ahead the casino has become an
        // income and the rest of the economy is decoration.
        var wallet = 1_000_000L
        repeat(200) { round ->
            val amount = (wallet * StakeTier.MEDIUM.walletFraction).toLong().coerceAtLeast(25L)
            wallet -= amount
            val roll = Stakes.rollOf(T + round * 3_600_000L, amount.toInt())
            if (Stakes.wins(StakeTier.MEDIUM, OutcomeQuality.GREAT, roll)) {
                wallet += StakeTier.MEDIUM.winnings(amount.toInt())
            }
        }
        assertTrue("a million became $wallet", wallet < 1_000_000L)
    }

    private companion object {
        const val T = 1_700_373_600_000L
    }
}
