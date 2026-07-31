package com.vpet.waifu.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val MINUTE = PetSimulation.MS_PER_MINUTE
private const val T0 = 1_700_373_600_000L

/**
 * The stake the game was missing: neglect has a consequence that must be
 * repaired, not merely waited out at zero cost.
 */
class SicknessTest {

    private val sim = PetSimulation()
    private val tuning = sim.tuning
    private val medicine = Shop.byId(Shop.MEDICINE_ID)!!

    private fun snapshot(hunger: Float = 80f, energy: Float = 80f, money: Int = 500) = PetSnapshot(
        stats = PetStats(hunger, energy, 70f),
        progress = PetProgress(money, 0),
        lastTickAt = T0,
        lastInteractionAt = T0,
        passiveSince = T0,
        bornAt = T0,
    )

    private fun neglectedUntilSick(): Pair<PetSnapshot, Long> {
        // Start her empty and walk time forward until the threshold trips.
        var s = snapshot(hunger = 0.5f, energy = 80f)
        var t = T0
        repeat((tuning.sickAfterRunDownMinutes + 10).toInt()) {
            t += MINUTE
            s = sim.advanceTo(s, t)
            if (s.isSick) return s to t
        }
        error("she never fell ill")
    }

    @Test
    fun `long enough at rock bottom makes her ill`() {
        val (sick, _) = neglectedUntilSick()

        assertTrue(sick.isSick)
    }

    @Test
    fun `a cared-for pet never falls ill`() {
        // A whole day of ordinary decay, fed before anything empties.
        var s = snapshot()
        var t = T0
        repeat(24 * 6) {
            t += 10 * MINUTE
            s = sim.advanceTo(s, t)
            if (s.stats.hunger < 40f) s = sim.feed(s, t)
            if (s.stats.energy < 40f) s = sim.startSleep(s, t)
            if (s.isSleeping && s.stats.energy > 95f) s = sim.wake(s, t)
        }

        assertFalse(s.isSick)
        assertEquals(0f, s.runDownMinutes, 30f)
    }

    @Test
    fun `while ill she cannot work, study or play`() {
        val (sick, t) = neglectedUntilSick()

        assertFalse(sick.canStart(Occupations.WORK.first(), tuning))
        assertFalse(sick.canStart(Occupations.STUDY.first(), tuning))
        assertEquals(PetActivity.AWAKE, sim.startPlaying(sick, t).activity)
    }

    @Test
    fun `but she can still be fed and patted`() {
        val (sick, t) = neglectedUntilSick()

        assertTrue(sim.feed(sick, t).stats.hunger > sick.stats.hunger)
        assertTrue(sick.acceptsPat)
    }

    @Test
    fun `medicine cures her on the spot and is remembered`() {
        val (sick, t) = neglectedUntilSick()
        val cured = sim.buy(sick, medicine, t)

        assertFalse(cured.isSick)
        assertEquals(1, cured.sicknessesNursed)
        assertEquals(Bond.NURSED, cured.bondPoints)
        assertTrue(cured.canStart(Occupations.WORK.first(), tuning))
    }

    @Test
    fun `medicine cannot be bought while she is healthy`() {
        val healthy = snapshot()

        assertFalse(healthy.canBuy(medicine, T0))
        assertEquals(PurchaseBlock.NOT_SICK, healthy.blockedBy(medicine, T0))
        // And buying it anyway changes nothing.
        assertEquals(healthy.progress.money, sim.buy(healthy, medicine, T0).progress.money)
    }

    @Test
    fun `she is visibly sick, above being hungry`() {
        val (sick, t) = neglectedUntilSick()

        assertEquals(PetState.SICK, sick.state(t))
    }

    @Test
    fun `an abandoned save recovers by itself in a day`() {
        val (sick, t) = neglectedUntilSick()
        // Feed her so hunger stops re-tripping the counter, then wait it out.
        val fed = sim.buy(sim.feed(sick, t), Shop.byId("bento")!!, t)
        val later = sim.advanceTo(fed, t + (tuning.sickRecoveryMinutes + 60) * MINUTE)

        assertFalse(later.isSick)
        // No medicine was bought, so no nursing is remembered.
        assertEquals(0, later.sicknessesNursed)
    }

    @Test
    fun `being ill drains mood on top of everything else`() {
        val (sick, t) = neglectedUntilSick()
        val fed = sim.buy(sim.feed(sick, t), Shop.byId("bento")!!, t)

        val healthySnapshot = fed.copy(sickSince = 0L)
        val sickLater = sim.advanceTo(fed, t + 60 * MINUTE)
        val healthyLater = sim.advanceTo(healthySnapshot, t + 60 * MINUTE)

        assertTrue(sickLater.stats.mood < healthyLater.stats.mood)
    }

    @Test
    fun `recovering between lapses burns the counter back down`() {
        // Forty bad minutes, then good care for long enough to recover, then
        // forty more: never ill, because the counter measures *recent* neglect.
        var s = snapshot(hunger = 0.5f)
        var t = T0
        repeat(40) { t += MINUTE; s = sim.advanceTo(s, t) }
        s = sim.buy(sim.feed(s, t), Shop.byId("bento")!!, t)
        repeat(120) { t += MINUTE; s = sim.advanceTo(s, t) }
        s = s.copy(stats = PetStats.coerced(0.5f, s.stats.energy, s.stats.mood))
        repeat(40) { t += MINUTE; s = sim.advanceTo(s, t) }

        assertFalse(s.isSick)
    }
}
