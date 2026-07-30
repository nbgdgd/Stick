package com.vpet.waifu.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val MINUTE = PetSimulation.MS_PER_MINUTE
private const val T0 = 1_700_373_600_000L

/**
 * Loose change, arriving every few seconds.
 *
 * The two things that matter here are that the wallet is *exact* — no coin is
 * ever lost to rounding across a long watch, and none is ever paid twice — and
 * that it cannot be farmed by leaving. The second is the whole reason the
 * feature has a grace window at all.
 */
class TipJarTest {

    private val sim = PetSimulation()
    private val tuning = sim.tuning

    private fun snapshot(
        money: Int = 0,
        exp: Int = 0,
        passiveSince: Long = T0,
        owned: Set<String> = setOf(Upgrades.DEFAULT_OUTFIT),
    ) = PetSnapshot(
        stats = PetStats(80f, 80f, 70f),
        progress = PetProgress(money, exp),
        lastTickAt = T0,
        lastInteractionAt = T0,
        owned = owned,
        passiveSince = passiveSince,
    )

    @Test
    fun `a coin lands on every tick at level one`() {
        val after = sim.settlePassive(snapshot(), T0 + tuning.passiveTickMillis)

        assertEquals(1, after.progress.money)
        assertEquals(T0 + tuning.passiveTickMillis, after.passiveSince)
    }

    @Test
    fun `part of a tick pays nothing and stays owed`() {
        val part = sim.settlePassive(snapshot(), T0 + tuning.passiveTickMillis - 1)
        assertEquals(0, part.progress.money)
        assertEquals(T0, part.passiveSince)

        // …and the moment the tick completes, it pays — the earlier call did
        // not quietly consume the time.
        val whole = sim.settlePassive(part, T0 + tuning.passiveTickMillis)
        assertEquals(1, whole.progress.money)
    }

    @Test
    fun `settling tick by tick pays exactly the same as settling all at once`() {
        val ticks = 15
        val end = T0 + ticks * tuning.passiveTickMillis

        var stepwise = snapshot()
        repeat(ticks) { i ->
            stepwise = sim.settlePassive(stepwise, T0 + (i + 1) * tuning.passiveTickMillis)
        }
        val atOnce = sim.settlePassive(snapshot(), end)

        assertEquals(atOnce.progress.money, stepwise.progress.money)
        assertEquals(ticks.toLong(), (stepwise.progress.money).toLong())
    }

    @Test
    fun `the fraction of a coin is banked rather than lost`() {
        // A rate below one coin a tick still has to add up: at 0.4 a tick, five
        // ticks are two coins, not zero.
        val slow = PetSimulation(PetTuning(passiveCoinsPerTick = 0.4f, passiveCoinsPerLevel = 0f))
        var s = snapshot()
        repeat(5) { i -> s = slow.settlePassive(s, T0 + (i + 1) * slow.tuning.passiveTickMillis) }

        assertEquals(2, s.progress.money)
    }

    @Test
    fun `leaving for the night pays nothing`() {
        val away = sim.settlePassive(snapshot(), T0 + 8 * 60 * MINUTE)

        assertEquals(0, away.progress.money)
        assertEquals(T0 + 8 * 60 * MINUTE, away.passiveSince)
    }

    @Test
    fun `a gap just inside the grace window still pays`() {
        val inside = sim.settlePassive(snapshot(), T0 + tuning.passiveGraceMillis)

        assertTrue(inside.progress.money > 0)
    }

    @Test
    fun `the bubble's once-a-minute tick keeps the jar filling`() {
        // The floating bubble is her on your screen, so it counts — but it
        // ticks a minute at a time, twenty times slower than the jar. If the
        // grace window were any tighter that whole surface would silently earn
        // nothing.
        var s = snapshot()
        var t = T0
        repeat(10) {
            t += MINUTE
            s = sim.settlePassive(s, t)
        }

        assertEquals(10 * 20, s.progress.money)
    }

    @Test
    fun `the background worker's quarter-hour tick pays nothing`() {
        val s = sim.settlePassive(snapshot(), T0 + 15 * MINUTE)

        assertEquals(0, s.progress.money)
    }

    @Test
    fun `a fresh save starts the clock instead of paying for all of history`() {
        val fresh = sim.settlePassive(snapshot(passiveSince = 0L), T0)

        assertEquals(0, fresh.progress.money)
        assertEquals(T0, fresh.passiveSince)
    }

    @Test
    fun `the jar pays more at higher levels and with better gear`() {
        val novice = snapshot()
        val veteran = snapshot(exp = Progression.expForLevel(15))
        val geared = snapshot(exp = Progression.expForLevel(15), owned = setOf("studio"))

        assertTrue(sim.passivePerTick(veteran) > sim.passivePerTick(novice))
        assertTrue(sim.passivePerTick(geared) > sim.passivePerTick(veteran))
    }

    @Test
    fun `a whole day of the jar is worth less than a day of shifts`() {
        // The jar is a trickle, not a job. Per *minute* it pays far better than
        // any wage — that is the point, it rewards being here — so the bound
        // that matters is the day: everything it can pay in one has to be
        // comfortably less than what actually working for one pays.
        val bestPerMinute = Occupations.WORK.maxOf { it.payPerHour } / 60f
        val workedDay = bestPerMinute * 24 * 60

        assertTrue(sim.passiveDailyCap(snapshot()) < workedDay / 2f)
    }

    @Test
    fun `the day has a ceiling and the jar goes dry at it`() {
        val cap = sim.passiveDailyCap(snapshot())
        var s = snapshot()
        var t = T0
        // Watch for three solid hours, a tick at a time.
        repeat(3 * 60 * 20) {
            t += tuning.passiveTickMillis
            s = sim.settlePassive(s, t)
        }

        assertEquals(cap, s.progress.money)
        assertEquals(0, sim.passiveLeftToday(s, t))
    }

    @Test
    fun `tomorrow refills it`() {
        var s = snapshot()
        var t = T0
        repeat(3 * 60 * 20) {
            t += tuning.passiveTickMillis
            s = sim.settlePassive(s, t)
        }
        val spent = s.progress.money

        // A day later, one tick pays again — and the day it dried up is not
        // paid out retroactively.
        val next = t + 24 * 60 * MINUTE
        s = sim.settlePassive(s.copy(passiveSince = next), next + tuning.passiveTickMillis)

        assertEquals(spent + 1, s.progress.money)
    }

    @Test
    fun `advancing the world settles the jar too`() {
        val after = sim.advanceTo(snapshot(), T0 + 15 * tuning.passiveTickMillis)

        assertEquals(15, after.progress.money)
    }
}
