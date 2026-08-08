package com.vpet.waifu.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val T = 1_700_373_600_000L

/**
 * The room, redecorated — the top of the money curve.
 *
 * Themes are the answer to "I own everything and the money keeps piling up".
 * They are cosmetic on purpose: an 80,000 purchase that also carried a
 * multiplier would mean the players who cannot reach it play a worse game.
 */
class ThemesTest {

    private val sim = PetSimulation()

    private fun rich(money: Int, level: Int = 20) = PetSnapshot(
        stats = PetStats(80f, 80f, 70f),
        progress = PetProgress(money, Progression.expForLevel(level)),
        lastTickAt = T,
        lastInteractionAt = T,
    )

    @Test
    fun `the starting room is free and already hers`() {
        val fresh = PetSnapshot.initial(T)

        assertTrue(fresh.owns(Upgrades.DEFAULT_THEME))
        assertEquals(Upgrades.DEFAULT_THEME, fresh.theme)
        assertEquals(0, Upgrades.byId(Upgrades.DEFAULT_THEME)!!.price)
    }

    @Test
    fun `a theme is applied the moment it is bought`() {
        val cozy = Upgrades.byId("theme_cozy")!!

        val decorated = sim.buyUpgrade(rich(cozy.price), cozy, T)

        assertEquals("theme_cozy", decorated.theme)
        assertTrue(decorated.owns("theme_cozy"))
        assertEquals(0, decorated.progress.money)
    }

    @Test
    fun `she can move back into a room she already owns`() {
        val cozy = Upgrades.byId("theme_cozy")!!
        val decorated = sim.buyUpgrade(rich(cozy.price), cozy, T)

        val plain = sim.applyTheme(decorated, Upgrades.DEFAULT_THEME, T)

        assertEquals(Upgrades.DEFAULT_THEME, plain.theme)
        // But not into one she has not paid for.
        assertEquals(Upgrades.DEFAULT_THEME, sim.applyTheme(plain, "theme_sakura", T).theme)
        // …and the two wardrobes do not reach into each other.
        assertEquals(plain.theme, sim.wear(plain, "theme_cozy", T).theme)
        assertEquals(plain.outfit, sim.applyTheme(plain, "outfit_gold", T).outfit)
    }

    @Test
    fun `a theme changes nothing about how she plays`() {
        val sakura = Upgrades.byId("theme_sakura")!!
        val plain = rich(sakura.price)
        val decorated = sim.buyUpgrade(plain, sakura, T)

        assertEquals(plain.modifiers(), decorated.modifiers())
        assertEquals(UpgradeEffect.NONE, Upgrades.effectOf(setOf("theme_cozy", "theme_night")))
        assertNotEquals(plain.theme, decorated.theme)
    }

    @Test
    fun `they are the last thing left to want that does nothing`() {
        val themes = Upgrades.THEMES.filter { it.price > 0 }
        // Measured against the *entry* price of the things that do something,
        // not against their ceiling. The mechanical ladder now runs to seven
        // figures, so nothing cosmetic will ever be the dearest object in the
        // game again — what still has to be true is that redecorating is a
        // serious purchase rather than pocket change beside a first upgrade.
        val firstTiers = Upgrades.ALL
            .filter { it.kind != UpgradeKind.THEME && it.tier == 1 && it.price > 0 }
            .maxOf { it.price }

        assertTrue(
            "a theme should out-price the dearest first tier of ${'$'}firstTiers",
            themes.minOf { it.price } < themes.maxOf { it.price } &&
                themes.maxOf { it.price } > firstTiers * 3,
        )
        assertEquals(listOf(8, 14, 20), themes.map { it.requiredLevel })
    }

    @Test
    fun `an unaffordable room stays undecorated`() {
        val night = Upgrades.byId("theme_night")!!
        val broke = rich(night.price - 1)

        assertEquals(Upgrades.DEFAULT_THEME, sim.buyUpgrade(broke, night, T).theme)
        // …and so does one she has not levelled into.
        assertEquals(
            Upgrades.DEFAULT_THEME,
            sim.buyUpgrade(rich(night.price, level = 13), night, T).theme,
        )
    }
}
