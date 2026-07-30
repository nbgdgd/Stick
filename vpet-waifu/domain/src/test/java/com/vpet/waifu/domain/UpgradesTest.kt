package com.vpet.waifu.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val MINUTE = PetSimulation.MS_PER_MINUTE
private const val T0 = 1_700_373_600_000L

/**
 * The thing to save towards.
 *
 * The game's real gap was that money had nowhere to go: every consumable in the
 * shop could be bought out by level nine, and the level curve runs to thirty.
 * These pin the properties that make upgrades fill it — they are kept, they
 * cost far more than a snack, and they actually change how she plays.
 */
class UpgradesTest {

    private val sim = PetSimulation()

    private fun rich(money: Int, exp: Int = Progression.expForLevel(20)) = PetSnapshot(
        stats = PetStats(80f, 80f, 70f),
        progress = PetProgress(money, exp),
        lastTickAt = T0,
        lastInteractionAt = T0,
    )

    @Test
    fun `an upgrade is kept, not consumed`() {
        val fridge = Upgrades.byId("fridge")!!
        val after = sim.buyUpgrade(rich(fridge.price), fridge, T0)

        assertTrue(after.owns("fridge"))
        assertEquals(0, after.progress.money)
        // And it cannot be bought twice.
        assertFalse(after.canBuy(fridge))
        assertEquals(after.owned, sim.buyUpgrade(after, fridge, T0).owned)
    }

    @Test
    fun `an empty wallet buys nothing permanent`() {
        val studio = Upgrades.byId("studio")!!
        val broke = rich(studio.price - 1)

        assertFalse(broke.canBuy(studio))
        assertFalse(sim.buyUpgrade(broke, studio, T0).owns("studio"))
    }

    @Test
    fun `the fridge really does slow her down getting hungry`() {
        val fridge = Upgrades.byId("fridge")!!
        val without = sim.advanceTo(rich(0), T0 + 120 * MINUTE)
        val with = sim.advanceTo(sim.buyUpgrade(rich(fridge.price), fridge, T0), T0 + 120 * MINUTE)

        assertTrue(
            "with ${with.stats.hunger} should beat without ${without.stats.hunger}",
            with.stats.hunger > without.stats.hunger,
        )
    }

    @Test
    fun `the bed makes a nap worth more`() {
        val bed = Upgrades.byId("bed")!!
        val tired = rich(bed.price).copy(stats = PetStats(80f, 20f, 70f))
        val without = sim.advanceTo(sim.startSleep(tired, T0), T0 + 20 * MINUTE)
        val with = sim.advanceTo(
            sim.startSleep(sim.buyUpgrade(tired, bed, T0), T0),
            T0 + 20 * MINUTE,
        )

        assertTrue(with.stats.energy > without.stats.energy)
    }

    @Test
    fun `the laptop pays a real premium on a whole shift`() {
        val laptop = Upgrades.byId("laptop")!!
        val cafe = Occupations.byId("cafe")!!
        val end = T0 + (cafe.durationMinutes + 2) * MINUTE

        val without = sim.advanceTo(sim.startOccupation(rich(0), cafe, T0), end).progress.money
        val bought = sim.buyUpgrade(rich(laptop.price), laptop, T0)
        val with = sim.advanceTo(sim.startOccupation(bought, cafe, T0), end).progress.money

        assertTrue("with $with should beat without $without", with > without)
    }

    @Test
    fun `upgrades stack rather than replacing each other`() {
        var state = rich(30_000)
        listOf("laptop", "studio").forEach { id ->
            state = sim.buyUpgrade(state, Upgrades.byId(id)!!, T0)
        }

        val both = state.modifiers().pay
        val one = Upgrades.effectOf(setOf("laptop")).pay
        assertTrue("$both should exceed $one", both > one)
    }

    @Test
    fun `an outfit is worn the moment it is bought`() {
        val sakura = Upgrades.byId("outfit_sakura")!!
        val after = sim.buyUpgrade(rich(sakura.price), sakura, T0)

        assertEquals("outfit_sakura", after.outfit)
    }

    @Test
    fun `she can change back into something she already owns`() {
        val sakura = Upgrades.byId("outfit_sakura")!!
        val dressed = sim.buyUpgrade(rich(sakura.price), sakura, T0)

        val changed = sim.wear(dressed, Upgrades.DEFAULT_OUTFIT, T0)

        assertEquals(Upgrades.DEFAULT_OUTFIT, changed.outfit)
        // But not into one she does not own.
        assertEquals(
            Upgrades.DEFAULT_OUTFIT,
            sim.wear(changed, "outfit_gold", T0).outfit,
        )
    }

    @Test
    fun `outfits are cosmetic and change nothing about the game`() {
        val gold = Upgrades.byId("outfit_gold")!!
        val plain = rich(gold.price)
        val fancy = sim.buyUpgrade(plain, gold, T0)

        assertEquals(plain.modifiers(), fancy.modifiers())
        assertNotEquals(plain.outfit, fancy.outfit)
    }

    // --- the economy ---------------------------------------------------------

    @Test
    fun `owning everything costs far more than the consumable shop ever did`() {
        val everyConsumable = Shop.ALL.sumOf { it.price }

        assertTrue(
            "upgrades ${Upgrades.totalCost} vs shop $everyConsumable",
            Upgrades.totalCost > everyConsumable * 20,
        )
    }

    @Test
    fun `the last upgrade is a long way past the last unlock`() {
        // The complaint this exists to answer: everything worth buying arrived
        // by level nine and the curve runs to thirty.
        val dearest = Upgrades.ALL.maxOf { it.price }
        val bestShift = Occupations.WORK.maxOf { it.payout }

        assertTrue(
            "the priciest upgrade is only ${dearest / bestShift} shifts",
            dearest / bestShift >= 20,
        )
        assertTrue(Upgrades.ALL.any { it.requiredLevel >= 15 })
    }

    @Test
    fun `the starting outfit is free and already hers`() {
        val fresh = PetSnapshot.initial(T0)

        assertTrue(fresh.owns(Upgrades.DEFAULT_OUTFIT))
        assertEquals(0, Upgrades.byId(Upgrades.DEFAULT_OUTFIT)!!.price)
    }
}
