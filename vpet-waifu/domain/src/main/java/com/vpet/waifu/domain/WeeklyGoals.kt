package com.vpet.waifu.domain

import kotlin.math.abs

/** What this week asks for. */
enum class GoalKind { SHIFTS, LESSONS, GAMES, EARN }

/**
 * The week's goal: repeating content that never runs out.
 *
 * The story ends — that is the point of it — and what remains afterwards used
 * to be nothing but the tick of the stats. A weekly goal is the endgame's
 * heartbeat: every seven days a new target, deterministic like everything else
 * driven by the calendar, scaled to her level, paid in money *and* bond so it
 * stays worth doing after the last upgrade is bought.
 *
 * Unlocked once the room stops being empty (story chapter 4) so the early game
 * is not buried under one more system.
 */
object WeeklyGoals {

    const val UNLOCK_CHAPTER = 4

    private const val WEEK_MILLIS = 7L * 24 * 60 * 60 * 1000

    const val BOND_REWARD = 5

    fun weekOf(millis: Long): Long = Math.floorDiv(millis, WEEK_MILLIS)

    /** Which kind of goal [week] carries. Stable for the whole week. */
    fun kindFor(week: Long): GoalKind =
        GoalKind.entries[(scramble(week) % GoalKind.entries.size).toInt()]

    /** The target, scaled so a higher level is asked for more. */
    fun targetFor(kind: GoalKind, level: Int): Int = when (kind) {
        GoalKind.SHIFTS -> 3 + level / 8
        GoalKind.LESSONS -> 2 + level / 10
        GoalKind.GAMES -> 5
        GoalKind.EARN -> 400 + level * 90
    }

    /** What meeting it pays. */
    fun rewardFor(kind: GoalKind, level: Int): Int = when (kind) {
        GoalKind.EARN -> 150 + level * 25
        else -> 200 + level * 30
    }

    /** The lifetime counter this kind measures, so progress is a plain delta. */
    fun counterFor(snapshot: PetSnapshot, kind: GoalKind): Int = when (kind) {
        GoalKind.SHIFTS -> snapshot.shiftsWorked
        GoalKind.LESSONS -> snapshot.lessonsDone
        GoalKind.GAMES -> snapshot.gamesPlayed
        GoalKind.EARN -> snapshot.totalEarned
    }

    fun unlocked(snapshot: PetSnapshot): Boolean = snapshot.storyChapter >= UNLOCK_CHAPTER

    /** Progress inside the current week, clamped to the target. */
    fun progress(snapshot: PetSnapshot): Int {
        val kind = kindFor(snapshot.goalWeek)
        return (counterFor(snapshot, kind) - snapshot.goalBaseline)
            .coerceIn(0, targetFor(kind, snapshot.level))
    }

    private fun scramble(value: Long): Long {
        var x = value * -7046029254386353131L
        x = x xor (x ushr 32)
        x *= -4658895280553007687L
        x = x xor (x ushr 29)
        return abs(x)
    }
}

/**
 * Day-count anniversaries — the calendar the save file was already carrying.
 *
 * Her adoption day is stored from the very first launch, and nothing ever
 * celebrated it. Now the milestones do: a week together, a month, a hundred
 * days, a year — each lands once, with a gift and a diary line.
 */
object Anniversaries {

    val MILESTONES: List<Int> = listOf(7, 30, 100, 200, 365, 500, 730, 1000)

    const val MOOD_GIFT = 15f

    fun moneyGift(days: Int): Int = 100 + days

    fun daysTogether(bornAt: Long, nowMillis: Long): Int {
        if (bornAt <= 0L) return 0
        return ((nowMillis - bornAt) / (24L * 60 * 60 * 1000)).toInt()
    }

    /** The next milestone [days] has reached that has not been celebrated yet. */
    fun due(days: Int, celebrated: Int): Int? =
        MILESTONES.lastOrNull { it in (celebrated + 1)..days }
}
