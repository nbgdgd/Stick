package com.vpet.waifu.ui

import androidx.compose.ui.graphics.Color
import com.vpet.waifu.domain.Occupations
import com.vpet.waifu.domain.Shop
import com.vpet.waifu.domain.UpgradeKind
import com.vpet.waifu.domain.Upgrades
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs

/**
 * That the catalogue looks like a catalogue.
 *
 * The complaint was "the icons are all the same, all one colour" — and they
 * were: one glyph family tinted with one accent, so eleven shop items were
 * eleven identical purple circles and picking one out meant reading the label.
 * Every entry now carries its own colour, and every entry needs its own icon;
 * the failure mode for both is silent, because a missing entry falls back to
 * something perfectly reasonable-looking.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PetLabelsTest {

    @Test
    fun `every job has its own colour`() {
        val tints = Occupations.ALL.associate { it.id to occupationTint(it.id) }

        assertEquals("two jobs share a colour: $tints", Occupations.ALL.size, tints.values.distinct().size)
    }

    @Test
    fun `every shop item has its own colour`() {
        val tints = Shop.ALL.associate { it.id to shopItemTint(it.id) }

        assertEquals("two items share a colour: $tints", Shop.ALL.size, tints.values.distinct().size)
    }

    @Test
    fun `every upgrade has its own colour`() {
        // By family: the tiers of one thing deliberately share its colour.
        val kept = Upgrades.ALL
            .filter { it.kind != UpgradeKind.OUTFIT && it.id != Upgrades.DEFAULT_THEME }
            .map { it.family }
            .distinct()
        val tints = kept.associateWith { upgradeTint(it) }

        assertEquals("two upgrades share a colour: $tints", kept.size, tints.values.distinct().size)
    }

    @Test
    fun `every job has its own icon`() {
        val icons = Occupations.ALL.map { occupationIcon(it.id).name }

        assertEquals("two jobs share an icon: $icons", Occupations.ALL.size, icons.distinct().size)
    }

    @Test
    fun `every shop item has its own icon`() {
        val icons = Shop.ALL.map { shopItemIcon(it.id).name }

        assertEquals("two items share an icon: $icons", Shop.ALL.size, icons.distinct().size)
    }

    /**
     * Distinct hex values are not enough — two shades of the same purple would
     * pass the checks above and still leave the list looking flat.
     *
     * The comparison is *within a list*, not across the whole app: the café's
     * amber and the ramen's orange are never on screen together, and forcing
     * nineteen mutually distant colours out of one wheel would mean reaching
     * for shades that no longer say anything about what they label.
     */
    @Test
    fun `no two colours in a list are so close they read as the same`() {
        val lists = mapOf(
            "jobs" to Occupations.ALL.map { it.id to occupationTint(it.id) },
            "shop" to Shop.ALL.map { it.id to shopItemTint(it.id) },
            "upgrades" to Upgrades.ALL
                .filter { it.kind != UpgradeKind.OUTFIT && it.id != Upgrades.DEFAULT_THEME }
                .map { it.family }
                .distinct()
                .map { it to upgradeTint(it) },
        )

        lists.forEach { (list, entries) ->
            entries.forEachIndexed { i, (idA, a) ->
                entries.drop(i + 1).forEach { (idB, b) ->
                    assertTrue(
                        "$list: $idA and $idB are near-identical colours",
                        distance(a, b) > 0.12f,
                    )
                }
            }
        }
    }

    @Test
    fun `no tint is too dark or too washed out to see`() {
        val all = Occupations.ALL.map { it.id to occupationTint(it.id) } +
            Shop.ALL.map { it.id to shopItemTint(it.id) } +
            Upgrades.ALL.filter { it.kind != UpgradeKind.OUTFIT }.map { it.id to upgradeTint(it.id) }

        all.forEach { (id, c) ->
            val luminance = (c.red + c.green + c.blue) / 3f
            assertTrue("$id is too dark on a dark card", luminance > 0.30f)
            assertTrue("$id is too pale to read as a colour", luminance < 0.95f)
        }
    }

    private fun distance(a: Color, b: Color): Float =
        (abs(a.red - b.red) + abs(a.green - b.green) + abs(a.blue - b.blue)) / 3f
}
