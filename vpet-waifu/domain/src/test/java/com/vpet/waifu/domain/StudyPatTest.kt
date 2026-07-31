package com.vpet.waifu.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val MINUTE = PetSimulation.MS_PER_MINUTE
private const val T0 = 1_700_373_600_000L

/**
 * Sitting with her while she studies.
 *
 * A pat while she is at her books is worth EXP as well as mood — but it must
 * not be a way to *farm* EXP, which a fixed reward on an unlimited action
 * always is. The rule is the pat cooldown that already existed: the value is
 * floored, so a masher's 0.2 multiplier rounds to nothing and only a pat left
 * to recharge carries a point.
 */
class StudyPatTest {

    private val sim = PetSimulation()
    private val tuning = sim.tuning

    private fun studying(lastTouchedMinutesAgo: Float): PetSnapshot {
        val school = Occupations.STUDY.first()
        val base = PetSnapshot(
            stats = PetStats(80f, 80f, 70f),
            lastTickAt = T0,
            lastInteractionAt = T0,
            passiveSince = T0,
        )
        return sim.startOccupation(base, school, T0)
            .copy(lastInteractionAt = T0 - (lastTouchedMinutesAgo * MINUTE).toLong())
    }

    @Test
    fun `a considered pat while she studies teaches her something`() {
        val exp = sim.patExp(studying(lastTouchedMinutesAgo = 5f), T0)

        assertEquals(tuning.patExp.toInt(), exp)
    }

    @Test
    fun `mashing it teaches her nothing`() {
        // The multiplier bottoms out at 0.2, and 0.2 of three points floors to
        // zero. That is the whole anti-farm rule, and it is arithmetic rather
        // than a special case.
        assertEquals(0, sim.patExp(studying(lastTouchedMinutesAgo = 0f), T0))
    }

    @Test
    fun `there is a floor below which a pat is not worth a point`() {
        // Somewhere between "just touched" and "left alone" the first point
        // lands. Wherever that is, it must be at least a minute of patience.
        val firstPaying = generateSequence(0f) { it + 0.1f }
            .takeWhile { it <= tuning.petFullEffectMinutes }
            .first { sim.patExp(studying(it), T0) > 0 }

        assertTrue("a pat pays EXP after only $firstPaying minutes", firstPaying >= 1f)
    }

    @Test
    fun `patting her anywhere else teaches her nothing`() {
        val idle = PetSnapshot(
            stats = PetStats(80f, 80f, 70f),
            lastTickAt = T0,
            lastInteractionAt = T0 - 10 * MINUTE,
            passiveSince = T0,
        )
        val working = sim.startOccupation(idle, Occupations.WORK.first(), T0)
            .copy(lastInteractionAt = T0 - 10 * MINUTE)

        assertEquals("idle", 0, sim.patExp(idle, T0))
        assertEquals("working", 0, sim.patExp(working, T0))
    }

    @Test
    fun `the pat actually banks the EXP it advertises`() {
        val before = studying(lastTouchedMinutesAgo = 5f)
        val promised = sim.patExp(before, T0)

        val after = sim.pet(before, T0)

        assertEquals(before.progress.exp + promised, after.progress.exp)
        assertTrue("and the mood too", after.stats.mood > before.stats.mood)
    }

    @Test
    fun `babysitting a whole lesson beats leaving her to it, but not by much`() {
        // Tap her every time it is worth a point, for a two-hour degree course.
        // The bonus has to be a reward for being there, not a replacement for
        // the session — so it stays well under what the session itself pays.
        val uni = Occupations.STUDY.last()
        var s = sim.startOccupation(
            PetSnapshot(
                stats = PetStats(100f, 100f, 70f),
                // University unlocks at level 8; without the standing behind
                // it, startOccupation quietly refuses and the whole loop
                // measures an idle pet.
                progress = PetProgress(0, Progression.expForLevel(uni.requiredLevel)),
                lastTickAt = T0,
                lastInteractionAt = T0,
                passiveSince = T0,
            ),
            uni,
            T0,
        )
        var t = T0
        var fromPats = 0
        // Two short of the bell, so she is still in the lesson at the end.
        repeat(uni.durationMinutes * 2 - 2) {
            t += MINUTE / 2
            s = sim.advanceTo(s, t)
            val worth = sim.patExp(s, t)
            if (worth > 0) {
                fromPats += worth
                s = sim.pet(s, t)
            }
        }

        assertEquals("she should still be studying", PetActivity.STUDYING, s.activity)
        assertTrue("pats paid nothing at all", fromPats > 0)
        assertTrue("pats paid $fromPats on a course worth ${uni.payout}", fromPats < uni.payout / 2)
    }
}
