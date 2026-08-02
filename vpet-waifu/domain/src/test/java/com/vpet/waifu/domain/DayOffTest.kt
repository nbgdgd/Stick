package com.vpet.waifu.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val DAY_MS = Events.MILLIS_PER_DAY
private val NOON = Events.dayOf(1_700_373_600_000L) * DAY_MS + 12 * 60 * 60 * 1000L

/**
 * The sink that can be spent twice.
 *
 * Everything permanent is bought once and then the wallet only grows; every
 * consumable is pocket change by level fifteen. The day off is the one purchase
 * the late game can keep making — priced at a serious day's work and rationed
 * by the calendar, so it stays a decision instead of a button that fixes her.
 */
class DayOffTest {

    private val sim = PetSimulation()
    private val dayOff = Shop.byId(Shop.DAY_OFF_ID)!!

    private fun pet(money: Int = 20_000) = PetSnapshot(
        stats = PetStats(20f, 15f, 25f),
        progress = PetProgress(money, Progression.expForLevel(3)),
        lastTickAt = NOON,
        lastInteractionAt = NOON,
        passiveSince = NOON,
        bornAt = NOON,
    )

    @Test
    fun `a day together fills her right up`() {
        val rested = sim.buy(pet(), dayOff, NOON)

        assertEquals(PetStats.MAX, rested.stats.hunger, 0.01f)
        assertEquals(PetStats.MAX, rested.stats.energy, 0.01f)
        assertEquals(PetStats.MAX, rested.stats.mood, 0.01f)
        assertEquals(20_000 - dayOff.price, rested.progress.money)
        assertEquals(Bond.DAY_OFF, rested.bondPoints)
    }

    @Test
    fun `and only once a day`() {
        val rested = sim.buy(pet(), dayOff, NOON)

        assertTrue(rested.dayOffTaken(NOON))
        assertFalse(rested.canBuy(dayOff, NOON + 6 * 60 * 60 * 1000L))
        assertEquals(PurchaseBlock.ALREADY_TODAY, rested.blockedBy(dayOff, NOON))
        // The second attempt changes nothing at all — measured against the
        // same minute merely passing, since the tip jar is ticking either way.
        val later = NOON + 60_000
        assertEquals(
            sim.advanceTo(rested, later).progress.money,
            sim.buy(rested, dayOff, later).progress.money,
        )
    }

    @Test
    fun `tomorrow it is available again`() {
        val rested = sim.buy(pet(), dayOff, NOON)
        val tomorrow = NOON + DAY_MS

        assertTrue(rested.canBuy(dayOff, tomorrow))
        assertNull(rested.blockedBy(dayOff, tomorrow))
    }

    @Test
    fun `a save that has never taken one is not told it already did`() {
        // Zero means "never", and the day index of the epoch is zero too — the
        // trap every "last time this happened" column walks into.
        val fresh = pet()

        assertEquals(0L, fresh.dayOffDay)
        assertTrue(fresh.canBuy(dayOff, NOON))
        assertFalse(fresh.dayOffTaken(NOON))
    }

    @Test
    fun `it costs what a late-game day is worth, and nothing gates it but money`() {
        assertEquals(6_000, dayOff.price)
        assertEquals(1, dayOff.requiredLevel)
        assertTrue(dayOff in Shop.ALL)
        assertFalse(pet(money = dayOff.price - 1).canBuy(dayOff, NOON))
    }

    @Test
    fun `it is not a meal and it is not a gift`() {
        val rested = sim.buy(pet(), dayOff, NOON)

        assertEquals(0, rested.mealsFed)
        assertEquals(0, rested.giftsGiven)
        assertNull(rested.lastMealId)
    }
}
