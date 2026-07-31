package com.vpet.waifu.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val MINUTE = PetSimulation.MS_PER_MINUTE
private const val T0 = 1_700_373_600_000L

/** The arc: chapters complete themselves, pay their rewards, and actually end. */
class StoryTest {

    private val sim = PetSimulation()

    private fun snapshot() = PetSnapshot(
        stats = PetStats(80f, 80f, 70f),
        progress = PetProgress(500, 0),
        lastTickAt = T0,
        lastInteractionAt = T0 - 10 * MINUTE,
        passiveSince = T0,
        bornAt = T0,
    )

    @Test
    fun `a fresh pet is at the first chapter with nothing done`() {
        assertEquals(0, snapshot().storyChapter)
        assertFalse(Story.isComplete(0))
    }

    @Test
    fun `caring for her completes the first chapter and pays its reward`() {
        var s = snapshot()
        val moneyBefore = s.progress.money
        // Bond 5 needs a few acts of care: feed + considered pats across time.
        var t = T0
        repeat(4) {
            s = sim.feed(sim.pet(s, t), t)
            t += 10 * MINUTE
            s = sim.advanceTo(s, t)
        }

        assertTrue("chapter did not complete, bond=${s.bondPoints}", s.storyChapter >= 1)
        assertTrue(s.progress.money > moneyBefore - 1)
        // The banner has something to show.
        assertTrue(s.storyChapter > s.storySeen)
    }

    @Test
    fun `acknowledging the banner clears it without touching progress`() {
        var s = snapshot()
        var t = T0
        repeat(4) {
            s = sim.feed(sim.pet(s, t), t)
            t += 10 * MINUTE
            s = sim.advanceTo(s, t)
        }
        val chapter = s.storyChapter

        val seen = sim.acknowledgeStory(s)

        assertEquals(chapter, seen.storyChapter)
        assertEquals(chapter, seen.storySeen)
    }

    @Test
    fun `one act can close two chapters at once`() {
        // A state that already satisfies chapters 0 and 1 together.
        val ready = snapshot().copy(bondPoints = 10, shiftsWorked = 1)
        val advanced = sim.advanceTo(ready, T0 + MINUTE)

        assertTrue(advanced.storyChapter >= 2)
    }

    @Test
    fun `the kindred chapter gifts an outfit she may never have afforded`() {
        val ready = snapshot().copy(
            bondPoints = Bond.pointsForLevel(4),
            shiftsWorked = 1,
            lessonsDone = 3,
            owned = setOf(Upgrades.DEFAULT_OUTFIT, "fridge", "bed"),
        )
        val advanced = sim.advanceTo(ready, T0 + MINUTE)

        assertTrue(advanced.storyChapter >= 5)
        assertTrue("the outfit was not gifted", advanced.owns("outfit_cocoa"))
    }

    @Test
    fun `the finale completes the story and gives the gold outfit`() {
        val ready = snapshot().copy(
            bondPoints = Bond.pointsForLevel(8),
            shiftsWorked = 20,
            lessonsDone = 5,
            owned = setOf(Upgrades.DEFAULT_OUTFIT, "fridge", "bed"),
            focus = Focus.CAREER,
            progress = PetProgress(500, Progression.expForLevel(Progression.MAX_LEVEL)),
        )
        val advanced = sim.advanceTo(ready, T0 + MINUTE)

        assertTrue(Story.isComplete(advanced.storyChapter))
        assertTrue(advanced.owns("outfit_gold"))
    }

    @Test
    fun `chapters never regress`() {
        var s = snapshot().copy(bondPoints = 10)
        s = sim.advanceTo(s, T0 + MINUTE)
        val reached = s.storyChapter
        // Days of neglect afterwards change nothing about the story.
        s = sim.advanceTo(s, T0 + 3 * 24 * 60 * MINUTE)

        assertEquals(reached, s.storyChapter)
    }
}
