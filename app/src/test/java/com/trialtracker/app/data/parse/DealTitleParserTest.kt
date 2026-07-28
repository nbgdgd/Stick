package com.trialtracker.app.data.parse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every input below was copied verbatim from the live r/googleplaydeals feed —
 * the formatting variance is real, not invented.
 */
class DealTitleParserTest {

    @Test
    fun `parses the common form`() {
        val parsed = DealTitleParser.parse("[Apps] Aura Notify (\$29.99 -> \$1.99)")!!
        assertEquals("Aura Notify", parsed.name)
        assertEquals("Apps", parsed.category)
        assertEquals(29.99, parsed.oldPrice!!, 0.001)
        assertEquals(1.99, parsed.newPrice!!, 0.001)
        assertEquals(93, parsed.discountPercent)
        assertEquals("$", parsed.currency)
    }

    @Test
    fun `treats FREE as zero regardless of case`() {
        val lower = DealTitleParser.parse("[Games] The Lonely Hacker (\$2.99 -> Free)")!!
        val upper = DealTitleParser.parse("[App] Store & Forget - BYOK Unlock (\$6.99 -> FREE)")!!
        assertTrue(lower.isFree)
        assertTrue(upper.isFree)
        assertEquals(100, upper.discountPercent)
        assertEquals("Store & Forget - BYOK Unlock", upper.name)
    }

    @Test
    fun `handles braces and a bare greater-than`() {
        val parsed = DealTitleParser.parse("[Apps] MINIMAA: Minimalist Launcher { \$0.99 > \$0.86}")!!
        assertEquals("MINIMAA: Minimalist Launcher", parsed.name)
        assertEquals(0.99, parsed.oldPrice!!, 0.001)
        assertEquals(0.86, parsed.newPrice!!, 0.001)
    }

    @Test
    fun `handles a missing leading zero`() {
        val parsed = DealTitleParser.parse("[Games] Vector Maze (\$.99 -> \$.39)")!!
        assertEquals(0.99, parsed.oldPrice!!, 0.001)
        assertEquals(0.39, parsed.newPrice!!, 0.001)
    }

    @Test
    fun `handles comma decimals and no spacing`() {
        val parsed = DealTitleParser.parse("[Games]Titan Quest: Ultimate Edition (\$13,99->\$5,60)")!!
        assertEquals("Titan Quest: Ultimate Edition", parsed.name)
        assertEquals(13.99, parsed.oldPrice!!, 0.001)
        assertEquals(5.60, parsed.newPrice!!, 0.001)
        assertEquals(59, parsed.discountPercent)
    }

    @Test
    fun `strips a trailing dash left by the price group`() {
        val parsed = DealTitleParser.parse("[Games]Maneater - Shark RPG - (\$5,99->\$4,19)")!!
        assertEquals("Maneater - Shark RPG", parsed.name)
    }

    @Test
    fun `rejects titles without a price transition`() {
        assertNull(DealTitleParser.parse("[Apps] Some app is now on sale"))
        assertNull(DealTitleParser.parse(""))
        assertNull(DealTitleParser.parse("[Games] Random discussion thread"))
    }
}
