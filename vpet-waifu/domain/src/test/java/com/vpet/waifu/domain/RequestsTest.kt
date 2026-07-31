package com.vpet.waifu.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val MINUTE = PetSimulation.MS_PER_MINUTE
private const val T0 = 1_700_373_600_000L

/**
 * Her own wishes: deterministic, grantable, and never punishing beyond a small
 * sadness when one quietly runs out.
 */
class RequestsTest {

    private val sim = PetSimulation()
    private val tuning = sim.tuning

    private fun snapshot(money: Int = 1_000) = PetSnapshot(
        stats = PetStats(80f, 80f, 70f),
        progress = PetProgress(money, 0),
        lastTickAt = T0,
        lastInteractionAt = T0,
        passiveSince = T0,
        bornAt = T0,
    )

    /**
     * Advances hour by hour, caring for her, until she voices a wish.
     *
     * The care is not decoration: a pet left alone for days falls ill, and an
     * ill pet stops asking for things — so an uncared-for search never finds
     * anything and times out instead.
     */
    private fun untilSheAsks(from: PetSnapshot = snapshot(), start: Long = T0): Pair<PetSnapshot, Long> {
        var s = from
        var t = start
        repeat(24 * 4) {
            t += 60 * MINUTE
            s = sim.advanceTo(s, t)
            if (s.request != null) return s to t
            if (s.isSleeping && s.stats.energy > 90f) s = sim.wake(s, t)
            if (s.stats.hunger < 50f) s = sim.feed(s, t)
            if (s.stats.energy < 40f) s = sim.startSleep(s, t)
        }
        error("she never asked for anything in four days")
    }

    @Test
    fun `she does eventually ask for something`() {
        val (s, t) = untilSheAsks()
        val request = s.request!!

        assertTrue(request.until > t)
        // And the wish is always something the player can actually buy or do.
        if (request.itemId != null) {
            assertTrue(Shop.byId(request.itemId!!)!!.isUnlocked(s.level))
        }
    }

    @Test
    fun `the same slot always voices the same wish`() {
        val slot = Requests.slotOf(T0)

        assertEquals(Requests.forSlot(slot, 5), Requests.forSlot(slot, 5))
    }

    @Test
    fun `a granted food wish is worth bond and joy`() {
        var (s, t) = untilSheAsks()
        // Walk forward until a wish names something buyable.
        while (s.request?.itemId == null) {
            val r = untilSheAsks(s.copy(request = null), t)
            s = r.first
            t = r.second
        }
        val item = Shop.byId(s.request!!.itemId!!)!!
        val moodBefore = s.stats.mood
        val bondBefore = s.bondPoints

        val granted = sim.buy(s, item, t)

        assertNull(granted.request)
        assertTrue(granted.bondPoints >= bondBefore + Bond.REQUEST_GRANTED)
        assertTrue(granted.stats.mood > moodBefore)
    }

    @Test
    fun `a play wish is granted by actually playing a round`() {
        var (s, t) = untilSheAsks()
        while (s.request?.kind != RequestKind.PLAY) {
            val r = untilSheAsks(s.copy(request = null), t)
            s = r.first
            t = r.second
        }
        s = sim.startPlaying(s, t)
        val done = sim.finishPlaying(s, score = 10, nowMillis = t + MINUTE)

        assertNull(done.request)
        assertTrue(done.bondPoints > 0)
    }

    @Test
    fun `buying the wrong thing grants nothing`() {
        var (s, t) = untilSheAsks()
        while (s.request?.kind != RequestKind.FOOD) {
            val r = untilSheAsks(s.copy(request = null), t)
            s = r.first
            t = r.second
        }
        val other = Shop.FOOD.first { it.id != s.request!!.itemId }
        val bought = sim.buy(s, other, t)

        assertNotNull(bought.request)
    }

    @Test
    fun `an ignored wish expires with a small sadness and no more`() {
        val (s, t) = untilSheAsks()
        val moodBefore = sim.advanceTo(s, s.request!!.until - MINUTE).stats.mood

        val after = sim.advanceTo(s, s.request!!.until + MINUTE)

        assertNull("the wish should have expired" , after.request?.takeIf { it.slot == s.request!!.slot })
        assertTrue(after.stats.mood < moodBefore)
        assertTrue(moodBefore - after.stats.mood < tuning.requestExpiredMood + 2f)
        assertEquals(s.bondPoints, after.bondPoints)
    }

    @Test
    fun `one window never asks twice`() {
        val (s, t) = untilSheAsks()
        val slot = s.request!!.slot
        // Grant it, then keep ticking inside the same window.
        val granted = s.copy(request = null)
        val later = sim.advanceTo(granted, minOf(s.request!!.until - MINUTE, t + 30 * MINUTE))

        assertTrue(later.request?.slot != slot || later.request == null)
    }

    @Test
    fun `she does not ask while she is ill`() {
        var s = snapshot().copy(sickSince = T0, request = null, lastRequestSlot = 0)
        var t = T0
        repeat(12) {
            t += 60 * MINUTE
            // Keep her fed so the sickness is the only variable; medicine is
            // deliberately not bought.
            s = sim.advanceTo(s, t)
            if (s.stats.hunger < 40f) s = sim.feed(s, t)
            if (!s.isSick) return@repeat
            assertNull("she asked while ill", s.request)
        }
    }
}
