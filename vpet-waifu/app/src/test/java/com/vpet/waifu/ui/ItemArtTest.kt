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
    fun `room and gear have portraits, outfits use their swatches`() {
        val furnishings = Upgrades.ALL.filter { it.kind != UpgradeKind.OUTFIT }
        val arts = furnishings.map { upgradeArtRes(it.id) }
        arts.forEach { assertNotNull("furnishing without art", it) }
        assertEquals(furnishings.size, arts.toSet().size)
        Upgrades.OUTFITS.forEach { assertNull(upgradeArtRes(it.id)) }
    }
}
