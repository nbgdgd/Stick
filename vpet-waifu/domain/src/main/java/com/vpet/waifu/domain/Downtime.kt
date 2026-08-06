package com.vpet.waifu.domain

import kotlin.math.abs

/**
 * Things to do while she is busy.
 *
 * A shift is the longest stretch of the game and, until now, the emptiest: she
 * leaves, every button greys out, and the app becomes a countdown. That is
 * backwards — the moment the player has committed to waiting is the moment they
 * most need something to do.
 *
 * Everything in this file is deliberately playable **while she is out**, and
 * deliberately small. None of it is a second job: a chore pays a fraction of a
 * shift, a find is loose change, and the stake on a shift is capped. They exist
 * to make the wait feel occupied, not to replace the thing being waited for.
 */

// --- chores ------------------------------------------------------------------

/**
 * A job around the flat that does not need her.
 *
 * Each has its own cooldown so the set can be worked through in a rotation
 * rather than mashed: come back in twenty minutes and there is something to do
 * again, which is roughly the rhythm of checking a phone.
 *
 * [requires] gates a chore on owning the thing it is about — there is no
 * watering the plant before there is a plant. It reads the same `owned` set the
 * shop writes, so a chore unlocks the moment its upgrade is bought, with no
 * separate bookkeeping.
 */
data class Chore(
    val id: String,
    val cooldownMinutes: Int,
    val money: Int,
    val exp: Int,
    val mood: Float = 0f,
    val requires: String? = null,
)

object Chores {

    val ALL: List<Chore> = listOf(
        // The always-available one, and the cheapest. Every flat has washing up.
        Chore("dishes", cooldownMinutes = 15, money = 18, exp = 4),
        // Tidying lifts the room, which lifts her when she gets home.
        Chore("tidy", cooldownMinutes = 20, money = 26, exp = 5, mood = 2f),
        // Laundry is the long one and pays for it.
        Chore("laundry", cooldownMinutes = 35, money = 48, exp = 9),
        // Two that arrive with the room upgrades they belong to.
        Chore("plant", cooldownMinutes = 25, money = 22, exp = 7, mood = 1f, requires = "fridge"),
        Chore("cat", cooldownMinutes = 20, money = 20, exp = 6, mood = 4f, requires = "cat"),
    )

    fun byId(id: String?): Chore? = ALL.firstOrNull { it.id == id }

    /** Whether the flat contains the thing this chore is about. */
    fun isUnlocked(chore: Chore, snapshot: PetSnapshot): Boolean =
        chore.requires == null || snapshot.owns(chore.requires)

    /** When [chore] may next be done, or 0 if it is ready now. */
    fun readyAt(chore: Chore, snapshot: PetSnapshot): Long {
        val done = snapshot.choreDoneAt[chore.id] ?: return 0L
        return done + chore.cooldownMinutes * 60_000L
    }

    fun isReady(chore: Chore, snapshot: PetSnapshot, nowMillis: Long): Boolean =
        isUnlocked(chore, snapshot) && nowMillis >= readyAt(chore, snapshot)

    /** Everything that can be done this second. */
    fun ready(snapshot: PetSnapshot, nowMillis: Long): List<Chore> =
        ALL.filter { isReady(it, snapshot, nowMillis) }
}

// --- finds -------------------------------------------------------------------

/** What turned up down the back of the sofa. */
enum class FindKind { COIN, BOOK, SNACK }

/**
 * Something small, found at an hour nobody chose.
 *
 * The interval is random inside [MIN_MINUTES]..[MAX_MINUTES] rather than fixed,
 * and that is the entire point of it: a fixed timer is an alarm clock, and an
 * alarm clock is a job. A window wide enough that you cannot predict it is a
 * reason to glance at the room, which is what the home screen is for.
 *
 * The next time is rolled from the previous one and stored, so it survives the
 * process being killed and cannot be re-rolled by reopening the app until a
 * better prize turns up.
 */
object Finds {

    const val MIN_MINUTES = 40
    const val MAX_MINUTES = 90

    /** How long a find waits to be noticed before it is gone. */
    const val LINGER_MINUTES = 30

    fun kindOf(readyAt: Long): FindKind =
        FindKind.entries[(scramble(readyAt / 60_000L) % FindKind.entries.size).toInt()]

    /** Money, EXP and mood a find of [kind] is worth at [level]. */
    fun rewardFor(kind: FindKind, level: Int): Arcade.Payout = when (kind) {
        FindKind.COIN -> Arcade.Payout(money = 55 + level * 9, exp = 0)
        FindKind.BOOK -> Arcade.Payout(money = 0, exp = 22 + level * 4)
        FindKind.SNACK -> Arcade.Payout(money = 20 + level * 3, exp = 6 + level)
    }

    fun moodFor(kind: FindKind): Float = if (kind == FindKind.SNACK) 4f else 1f

    /** Whether a find is sitting on the home screen right now. */
    fun isWaiting(snapshot: PetSnapshot, nowMillis: Long): Boolean {
        val at = snapshot.findReadyAt
        if (at <= 0L) return false
        return nowMillis >= at && nowMillis < at + LINGER_MINUTES * 60_000L
    }

    /** The instant the next one turns up, rolled from [fromMillis]. */
    fun nextAfter(fromMillis: Long): Long {
        val span = (MAX_MINUTES - MIN_MINUTES + 1).toLong()
        val minutes = MIN_MINUTES + (scramble(fromMillis) % span)
        return fromMillis + minutes * 60_000L
    }

    private fun scramble(value: Long): Long {
        var x = value * -7046029254386353131L
        x = x xor (x ushr 32)
        x *= -4658895280553007687L
        x = x xor (x ushr 29)
        return abs(x)
    }
}

// --- staking a shift ---------------------------------------------------------

/**
 * Money put aside on the outcome of a shift.
 *
 * The one mechanic here that is a *decision* rather than a chore. It pays +20%
 * on a good shift and takes 10% on a bad one, which is positive expected value
 * — and that is fine, because the cost is not the odds. The cost is that the
 * money is locked for the whole shift, in a game where the reason to have money
 * mid-shift is to buy the boost that saves it. Staking your wallet at the start
 * of an idol run means having nothing when her mood falls through the floor an
 * hour in.
 *
 * The outcome is not a coin toss either: it is [her mood at clock-out], the same
 * number that decides the shift's own payout. So the stake is a bet on your own
 * care rather than on a dice roll, and the way to win it is to look after her
 * while she works.
 */
object Stakes {

    const val WIN_RATE = 0.20f
    const val LOSS_RATE = 0.10f

    /** The most that may be staked, so it can never become the whole economy. */
    fun maxStake(snapshot: PetSnapshot): Int =
        minOf(250 + snapshot.level * 120, snapshot.progress.money)

    /** A stake pays out when the shift ends well — GREAT or GOOD. */
    fun wins(quality: OutcomeQuality): Boolean =
        quality == OutcomeQuality.GREAT || quality == OutcomeQuality.GOOD

    /** What [amount] returns, in full — the stake back plus or minus its share. */
    fun settle(amount: Int, quality: OutcomeQuality): Int =
        if (wins(quality)) {
            amount + (amount * WIN_RATE).toInt()
        } else {
            amount - (amount * LOSS_RATE).toInt()
        }
}
