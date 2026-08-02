package com.vpet.waifu.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val START = 1_700_373_600_000L

/**
 * The one rule the player can act on mid-shift, made visible.
 *
 * Mood decides wages minute by minute, and a pat lifts mood — but until the
 * shift ended and the card said "poor" there was no way to know any of it was
 * happening. These pin the accessor the shift screen reads to the numbers the
 * accrual actually uses, because a preview that can drift from the wallet is
 * worse than no preview.
 */
class PayQualityTest {

    private val sim = PetSimulation()
    private val tuning = sim.tuning
    private val cafe = Occupations.WORK.first()

    private fun pet(mood: Float) = PetSnapshot(
        stats = PetStats(80f, 80f, mood),
        lastTickAt = START,
        lastInteractionAt = START,
    )

    @Test
    fun `the quality on screen is the quality in the wallet`() {
        listOf(95f, 60f, 30f, 5f).forEach { mood ->
            val working = sim.startOccupation(pet(mood), cafe, START)

            assertEquals(sim.qualityFor(mood), sim.currentQuality(working))
            assertEquals(
                sim.multiplierFor(sim.qualityFor(mood), cafe.kind),
                sim.currentPayMultiplier(working),
                0.0001f,
            )
        }
    }

    @Test
    fun `studying in a bad mood is punished harder than working in one`() {
        val lesson = Occupations.STUDY.first()
        val miserable = pet(5f)

        val atWork = sim.currentPayMultiplier(sim.startOccupation(miserable, cafe, START))
        val atSchool = sim.currentPayMultiplier(sim.startOccupation(miserable, lesson, START))

        assertTrue("$atSchool should be worse than $atWork", atSchool < atWork)
    }

    @Test
    fun `off the clock it previews what sending her to work would pay`() {
        val idle = pet(90f)

        assertEquals(OutcomeQuality.GREAT, sim.currentQuality(idle))
        assertEquals(tuning.greatMultiplier, sim.currentPayMultiplier(idle), 0.0001f)
    }

    @Test
    fun `a pat that lifts her over the line lifts the multiplier with it`() {
        val nearly = sim.startOccupation(pet(tuning.greatMoodThreshold - 1f), cafe, START)

        val patted = sim.pet(nearly, START + 10 * PetSimulation.MS_PER_MINUTE)

        assertEquals(OutcomeQuality.GOOD, sim.currentQuality(nearly))
        assertEquals(OutcomeQuality.GREAT, sim.currentQuality(patted))
        assertTrue(sim.currentPayMultiplier(patted) > sim.currentPayMultiplier(nearly))
    }
}
