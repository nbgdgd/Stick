package com.vpet.waifu.ui.character

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What tells the two drawn characters apart.
 *
 * Both bugs this pins were found by looking at a render, not by reading the
 * code: the classic rig was drawn in the current character's silver hair, and
 * then in her clothes. Either one on its own leaves the old character as the
 * new one wearing her proportions, which is not what "bring the old one back"
 * meant.
 */
class PetSkinTest {

    @Test
    fun `the classic character keeps her own hair and eyes in every outfit`() {
        listOf(null, "outfit_uniform", "outfit_cocoa", "outfit_midnight", "outfit_gold").forEach { id ->
            val palette = PetPalette.forOutfit(id, classic = true)
            assertEquals("hair changed for $id", PetPalette.Classic.hair, palette.hair)
            assertEquals("eyes changed for $id", PetPalette.Classic.iris, palette.iris)
        }
    }

    @Test
    fun `her starting outfit is her own uniform, not the current character's`() {
        val classic = PetPalette.forOutfit(null, classic = true)
        assertEquals(PetPalette.Classic.uniform, classic.uniform)
        assertEquals(PetPalette.Classic.skirt, classic.skirt)
        assertNotEquals(PetPalette.Default.uniform, classic.uniform)
    }

    @Test
    fun `a bought outfit still changes her clothes`() {
        val cocoa = PetPalette.forOutfit("outfit_cocoa", classic = true)
        assertEquals(PetPalette.forOutfit("outfit_cocoa").uniform, cocoa.uniform)
        assertEquals(PetPalette.Classic.hair, cocoa.hair)
    }

    @Test
    fun `a stored id naming a pack that is gone falls back to a drawn character`() {
        assertEquals(PetSkin.Modern, PetSkin.of("some-removed-pack", null))
        assertEquals(PetSkin.Classic, PetSkin.of(PetSkin.CLASSIC_ID, null))
        assertEquals(PetSkin.Modern, PetSkin.of("", null))
    }

    @Test
    fun `the bubble anchor follows the character, not the stage`() {
        val box = 1233f to 732f
        val modern = PetSkin.Modern.headTopPx(box.first, box.second)
        val classic = PetSkin.Classic.headTopPx(box.first, box.second)

        // Both rigs letterbox a whole field, so their heads sit near the top of
        // the box; a sheet is floor-anchored and scaled down, so it sits far
        // lower. That gap is the entire reason the anchor exists.
        assertTrue("modern head unexpectedly low: $modern", modern < box.second * 0.2f)
        assertTrue("classic head unexpectedly low: $classic", classic < box.second * 0.25f)
    }
}
