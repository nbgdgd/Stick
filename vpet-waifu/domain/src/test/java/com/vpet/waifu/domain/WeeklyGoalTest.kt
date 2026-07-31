package com.vpet.waifu.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val MINUTE = PetSimulation.MS_PER_MINUTE
private const val T0 = 1_700_373_600_000L
private const val WEEK = 7L * 24 * 60 * MINUTE

/** The endgame's heartbeat: a new target every week, forever. */
class WeeklyGoalTest {

    private val sim = PetSimulation()

    private fun unlocked(shifts: Int = 0) = PetSnapshot(
        stats = PetStats(80f, 80f, 70f),
        progress = PetProgress(500, 0),
        lastTickAt = T0,
        lastInteractionAt = T0,
        passiveSince = T0,
        bornAt = T0,
        storyChapter = WeeklyGoals.UNLOCK_CHAPTER,
        shiftsWorked = shifts,
        lessonsDone = 5,
        gamesPlayed = 5,
        totalEarned = 1_000,
        // The tip jar is parked at its daily cap so the only money that can
        // move in these tests is the goal's own reward.
        passiveDay = Events.dayOf(T0),
        passivePaidToday = 1_000_000,
    )

    @Test
    fun `locked before the room stops being empty`() {
        val early = unlocked().copy(storyChapter = WeeklyGoals.UNLOCK_CHAPTER - 1)
        val after = sim.advanceTo(early, T0 + MINUTE)

        assertEquals(0L, after.goalWeek)
    }

    @Test
    fun `the week rolling over resets the baseline`() {
        val s = sim.advanceTo(unlocked(shifts = 7), T0 + MINUTE)

        assertEquals(WeeklyGoals.weekOf(T0 + MINUTE), s.goalWeek)
        assertFalse(s.goalRewarded)
        assertEquals(0, WeeklyGoals.progress(s))
    }

    @Test
    fun `meeting the target pays once and only once`() {
        var s = sim.advanceTo(unlocked(), T0 + MINUTE)
        val kind = WeeklyGoals.kindFor(s.goalWeek)
        val target = WeeklyGoals.targetFor(kind, s.level)
        val reward = WeeklyGoals.rewardFor(kind, s.level)

        // Push the matching counter past the target by hand.
        s = when (kind) {
            GoalKind.SHIFTS -> s.copy(shiftsWorked = s.shiftsWorked + target)
            GoalKind.LESSONS -> s.copy(lessonsDone = s.lessonsDone + target)
            GoalKind.GAMES -> s.copy(gamesPlayed = s.gamesPlayed + target)
            GoalKind.EARN -> s.copy(totalEarned = s.totalEarned + target)
        }
        val moneyBefore = s.progress.money
        s = sim.advanceTo(s, T0 + 2 * MINUTE)

        assertTrue(s.goalRewarded)
        assertEquals(moneyBefore + reward, s.progress.money)
        assertTrue("a goal is worth bond", s.bondPoints >= WeeklyGoals.BOND_REWARD)

        // Ticking on changes nothing more this week.
        val again = sim.advanceTo(s, T0 + 3 * MINUTE)
        assertEquals(s.progress.money, again.progress.money)
    }

    @Test
    fun `next week brings a fresh goal`() {
        var s = sim.advanceTo(unlocked(), T0 + MINUTE)
        val firstWeek = s.goalWeek
        s = sim.advanceTo(s, T0 + WEEK + MINUTE)

        assertEquals(firstWeek + 1, s.goalWeek)
        assertFalse(s.goalRewarded)
    }

    @Test
    fun `the kind is stable within a week and varies across them`() {
        val week = WeeklyGoals.weekOf(T0)
        assertEquals(WeeklyGoals.kindFor(week), WeeklyGoals.kindFor(week))

        val kinds = (0L..11L).map { WeeklyGoals.kindFor(week + it) }.distinct()
        assertTrue("twelve weeks of the same goal: $kinds", kinds.size >= 3)
    }
}
