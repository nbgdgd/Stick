package com.vpet.waifu.domain

import kotlin.math.abs

/** What a day's quest asks for. Each maps to a counter the save already keeps. */
enum class QuestKind { FEED, SHIFT, LESSON, GAME, GIFT, EARN, PAT }

/** One of the day's three jobs. */
data class DailyQuest(
    val kind: QuestKind,
    val target: Int,
    val money: Int,
    val exp: Int,
)

/**
 * Three small things to do today.
 *
 * The game has a weekly goal and a login streak, and between them a day with no
 * shape at all: the weekly goal moves by a few percent whatever you do, and the
 * streak is paid for opening the app rather than for playing it. A day needs its
 * own arc — three jobs, done in twenty minutes, gone at midnight.
 *
 * Deterministic from the day index, like every other calendar-driven system
 * here: no stored roll, no RNG to reseed, and the same three quests on the same
 * date whichever device the save is opened on. What *is* stored is the counter
 * each quest started from, because progress has to mean "since this morning"
 * rather than "since you adopted her".
 *
 * Rewards are paid in money **and** EXP on purpose. Money alone would make the
 * quests a second tip jar; EXP alone would make them a second lesson. Together
 * they are the only thing in the game that reliably pays both, which is what
 * makes a day of odd jobs feel like it moved you forward on every axis.
 */
object Quests {

    /** How many land each day. Three is a list you can hold in your head. */
    const val PER_DAY = 3

    /**
     * The kinds a day may draw from.
     *
     * [QuestKind.PAT] is deliberately last and deliberately cheap to satisfy:
     * every other kind needs her free, and a day where all three quests are
     * blocked because she is on a long shift would be a system that punishes
     * the player for using the rest of the game.
     */
    private val POOL: List<QuestKind> = QuestKind.entries

    /** The three quests [day] carries, in a stable order. */
    fun forDay(day: Long, level: Int): List<DailyQuest> {
        // Draw without replacement so a day never asks the same thing twice.
        val pool = POOL.toMutableList()
        val picked = ArrayList<QuestKind>(PER_DAY)
        var seed = day
        repeat(PER_DAY) {
            seed = scramble(seed + 0x9E37)
            picked += pool.removeAt((seed % pool.size).toInt())
        }
        return picked.map { kind -> DailyQuest(kind, targetFor(kind, level), moneyFor(kind, level), expFor(kind, level)) }
    }

    /**
     * How much of [kind] a day asks for.
     *
     * Scaled by level only where the underlying action gets cheaper with level
     * — earning scales hard because wages do, while "feed her twice" is two
     * meals at level one and at level thirty. A quest that grows with you on
     * every axis is just a wall that moves.
     */
    fun targetFor(kind: QuestKind, level: Int): Int = when (kind) {
        QuestKind.FEED -> 2
        QuestKind.SHIFT -> 1
        QuestKind.LESSON -> 1
        QuestKind.GAME -> 2
        QuestKind.GIFT -> 1
        QuestKind.EARN -> 250 + level * 55
        QuestKind.PAT -> 8
    }

    /** What finishing it pays. */
    fun moneyFor(kind: QuestKind, level: Int): Int = when (kind) {
        // The two that already pay well on their own get the smaller purse.
        QuestKind.SHIFT, QuestKind.EARN -> 70 + level * 10
        QuestKind.GIFT -> 120 + level * 14
        else -> 90 + level * 12
    }

    fun expFor(kind: QuestKind, level: Int): Int = when (kind) {
        // Studying is the EXP activity, so its quest pays in money instead —
        // otherwise the reward is indistinguishable from the thing you did.
        QuestKind.LESSON -> 12 + level * 2
        else -> 26 + level * 4
    }

    /**
     * The lifetime counter [kind] measures.
     *
     * Progress is a delta against the value stored at first sight of the day,
     * exactly like [WeeklyGoals] — it means no quest needs its own event hook
     * anywhere in the simulation, and a counter that is already tested keeps
     * being the only source of truth.
     */
    fun counterFor(snapshot: PetSnapshot, kind: QuestKind): Int = when (kind) {
        QuestKind.FEED -> snapshot.mealsFed
        QuestKind.SHIFT -> snapshot.shiftsWorked
        QuestKind.LESSON -> snapshot.lessonsDone
        QuestKind.GAME -> snapshot.gamesPlayed
        QuestKind.GIFT -> snapshot.giftsGiven
        QuestKind.EARN -> snapshot.totalEarned
        QuestKind.PAT -> snapshot.patsGiven
    }

    /** Progress on the quest at [index] today, clamped to its target. */
    fun progress(snapshot: PetSnapshot, index: Int): Int {
        val quest = snapshot.questsToday().getOrNull(index) ?: return 0
        val base = snapshot.questBaselines.getOrElse(index) { 0 }
        return (counterFor(snapshot, quest.kind) - base).coerceIn(0, quest.target)
    }

    fun isDone(snapshot: PetSnapshot, index: Int): Boolean {
        val quest = snapshot.questsToday().getOrNull(index) ?: return false
        return progress(snapshot, index) >= quest.target
    }

    /** Whether the reward for [index] has already been taken. */
    fun isClaimed(snapshot: PetSnapshot, index: Int): Boolean =
        snapshot.questClaimed and (1 shl index) != 0

    /** Quests finished but not yet collected — what the badge counts. */
    fun claimable(snapshot: PetSnapshot): List<Int> =
        (0 until PER_DAY).filter { isDone(snapshot, it) && !isClaimed(snapshot, it) }

    private fun scramble(value: Long): Long {
        var x = value * -7046029254386353131L
        x = x xor (x ushr 32)
        x *= -4658895280553007687L
        x = x xor (x ushr 29)
        return abs(x)
    }
}
