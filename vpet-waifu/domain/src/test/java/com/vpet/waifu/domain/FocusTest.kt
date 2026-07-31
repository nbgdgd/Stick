package com.vpet.waifu.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val T0 = 1_700_373_600_000L

/** The one irreversible decision: gated, permanent, and a genuine trade-off. */
class FocusTest {

    private val sim = PetSimulation()

    private fun snapshot(level: Int = Focus.UNLOCK_LEVEL) = PetSnapshot(
        stats = PetStats(80f, 80f, 70f),
        progress = PetProgress(500, Progression.expForLevel(level)),
        lastTickAt = T0,
        lastInteractionAt = T0,
        passiveSince = T0,
        bornAt = T0,
    )

    @Test
    fun `too early, and she is not ready to choose`() {
        val young = snapshot(level = Focus.UNLOCK_LEVEL - 1)

        assertNull(sim.chooseFocus(young, Focus.CAREER, T0).focus)
    }

    @Test
    fun `at the level, the choice sticks`() {
        val chosen = sim.chooseFocus(snapshot(), Focus.SCHOLAR, T0)

        assertEquals(Focus.SCHOLAR, chosen.focus)
    }

    @Test
    fun `and it can never be changed`() {
        val chosen = sim.chooseFocus(snapshot(), Focus.SCHOLAR, T0)
        val secondTry = sim.chooseFocus(chosen, Focus.CAREER, T0)

        assertEquals(Focus.SCHOLAR, secondTry.focus)
    }

    @Test
    fun `every path gives something and costs something`() {
        Focus.entries.forEach { focus ->
            val e = focus.effect()
            val gains = listOf(e.pay, e.study, e.sleepSpeed, e.play).count { it > 1f } +
                (if (e.neglect < 1f) 1 else 0)
            val costs = listOf(e.pay, e.study).count { it < 1f }

            assertTrue("$focus gives nothing", gains >= 1)
            assertTrue("$focus is a free lunch", costs >= 1)
        }
    }

    @Test
    fun `the career woman out-earns the scholar, and vice versa`() {
        val career = snapshot().copy(focus = Focus.CAREER)
        val scholar = snapshot().copy(focus = Focus.SCHOLAR)

        assertTrue(career.modifiers().pay > scholar.modifiers().pay)
        assertTrue(scholar.modifiers().study > career.modifiers().study)
    }
}
