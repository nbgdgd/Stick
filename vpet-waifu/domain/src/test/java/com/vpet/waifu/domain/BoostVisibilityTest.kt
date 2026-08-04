package com.vpet.waifu.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the shift card promises has to match what the shift pays.
 *
 * The boosts were applied correctly in the simulation from the day they were
 * added, and the card went on showing the mood multiplier alone — so buying an
 * overtime pass raised the wages and changed nothing anybody could see. A
 * player has no way to tell that apart from a boost that does nothing, and the
 * reasonable conclusion is that the item is broken.
 */
class BoostVisibilityTest {

    private val simulation = PetSimulation()
    private val tuning = PetTuning()
    private val now = 1_800_000_000_000L

    private fun working(vararg effects: EffectKind): PetSnapshot {
        val job = Occupations.WORK.first()
        return PetSnapshot.initial(now).copy(
            stats = PetStats(hunger = 80f, energy = 80f, mood = 95f),
            session = ActivitySession(
                occupationId = job.id,
                startedAt = now,
                endsAt = now + job.durationMinutes * 60_000L,
            ),
            effects = effects.map { ActiveEffect(it, now + 45 * 60_000L) },
        )
    }

    @Test
    fun `an overtime pass shows up in the pay multiplier`() {
        val plain = simulation.payMultiplierNow(working(), now)
        val boosted = simulation.payMultiplierNow(working(EffectKind.OVERTIME), now)

        assertEquals(plain * tuning.overtimeMultiplier, boosted, 0.001f)
        assertTrue("the boost left the shown multiplier untouched", boosted > plain)
    }

    @Test
    fun `focus tea shows up in the study multiplier`() {
        val plain = simulation.studyMultiplierNow(working(), now)
        val boosted = simulation.studyMultiplierNow(working(EffectKind.FOCUS), now)

        assertEquals(plain * tuning.focusMultiplier, boosted, 0.001f)
    }

    @Test
    fun `a boost that has expired stops being shown`() {
        val stale = working().copy(
            effects = listOf(ActiveEffect(EffectKind.OVERTIME, now - 1)),
        )
        assertEquals(
            simulation.payMultiplierNow(working(), now),
            simulation.payMultiplierNow(stale, now),
            0.001f,
        )
    }

    @Test
    fun `the job preview counts a boost that is already running`() {
        val job = Occupations.WORK.first()
        val plain = simulation.projectedPayout(working(), job, now)
        val boosted = simulation.projectedPayout(working(EffectKind.OVERTIME), job, now)

        assertTrue("preview ignored a running boost: $plain vs $boosted", boosted > plain)
    }

    @Test
    fun `the wrong boost for the job changes nothing`() {
        // Focus tea is a study item. On a shift it must not inflate the wages —
        // showing it as pay would be the same lie in the other direction.
        assertEquals(
            simulation.payMultiplierNow(working(), now),
            simulation.payMultiplierNow(working(EffectKind.FOCUS), now),
            0.001f,
        )
    }
}
