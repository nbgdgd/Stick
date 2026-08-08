package com.vpet.waifu.ui

import com.vpet.waifu.R
import com.vpet.waifu.domain.Occupations
import com.vpet.waifu.domain.Shop
import com.vpet.waifu.domain.UpgradeKind
import com.vpet.waifu.domain.Upgrades
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Every sellable thing has its own drawn portrait.
 *
 * The lookups fall back to a default for unknown ids, which is right for a
 * hostile string but wrong for a real catalog entry — a new item added to the
 * domain without art would silently ship as a rice ball. Distinctness pins it.
 */
class ItemArtTest {

    @Test
    fun `every shop item has its own portrait`() {
        val arts = Shop.ALL.map { shopItemArtRes(it.id) }
        assertEquals("duplicate or fallback art in shop", Shop.ALL.size, arts.toSet().size)
        // The fallback is the rice ball; only the rice ball itself may use it.
        assertEquals(
            listOf("onigiri"),
            Shop.ALL.filter { shopItemArtRes(it.id) == R.drawable.art_onigiri }.map { it.id },
        )
    }

    @Test
    fun `every occupation has its own portrait`() {
        val arts = Occupations.ALL.map { occupationArtRes(it.id) }
        assertEquals(Occupations.ALL.size, arts.toSet().size)
        assertEquals(
            listOf("cafe"),
            Occupations.ALL.filter { occupationArtRes(it.id) == R.drawable.art_cafe }.map { it.id },
        )
    }

    @Test
    fun `every purchasable upgrade has its own portrait`() {
        // The default room is the one thing here nobody buys, so it is the one
        // thing without a picture; everything else — including the six outfits
        // that used to share a single coat hanger — carries its own.
        //
        // One picture per *family*, not per tier: a second fridge is the same
        // fridge, improved, and drawing five of them would be five drawings of
        // one object. What the test still forbids is two different things
        // sharing a picture.
        val bought = Upgrades.ALL.filter { it.id != Upgrades.DEFAULT_THEME }
        bought.forEach { assertNotNull("purchasable upgrade without art", upgradeArtRes(it.id)) }

        val families = bought.map { it.family }.distinct()
        assertEquals(
            "two upgrades share a portrait",
            families.size,
            families.map { upgradeArtRes(it) }.toSet().size,
        )
        // …and every tier of one family answers with the family's own picture.
        bought.forEach {
            assertEquals("${'$'}{it.id} strayed from its family", upgradeArtRes(it.family), upgradeArtRes(it.id))
        }
        assertNull(upgradeArtRes(Upgrades.DEFAULT_THEME))
    }
}
