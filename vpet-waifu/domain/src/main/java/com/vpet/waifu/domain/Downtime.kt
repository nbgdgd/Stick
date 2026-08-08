package com.vpet.waifu.domain

import kotlin.math.abs
import kotlin.math.roundToInt

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
 * How much is on the table, and at what odds.
 *
 * Four sizes rather than a slider, because the question the player is answering
 * is "how brave am I", not "what is the exact optimal number". Each step up
 * stakes more of the wallet, pays more when it lands, and lands less often —
 * which is what makes it a decision instead of arithmetic.
 *
 * The odds are read off *how the shift went*: a shift she finished in a great
 * mood is roughly four times likelier to pay than one she was miserable
 * through. Looking after her is still the whole game. What changed is that it
 * now buys better odds rather than a certainty — because a bet you win by
 * playing properly is not a bet, it is an allowance, and the old one paid a
 * guaranteed +20% to anyone who fed her.
 *
 * Every cell of this table is below break-even. At the friendliest tier, on the
 * best possible shift, a hundred staked returns ninety-five on average; at the
 * worst it returns eighty. That is the house edge, and it is what stops the
 * table from becoming the way to make money — no amount of skill turns a
 * negative expectation positive, which is exactly the property a casino needs
 * and the old stake did not have.
 */
enum class StakeTier(
    /** How much of the wallet this puts on the table. */
    val walletFraction: Float,
    /** What a winning bet returns, as a multiple of the stake. */
    val payout: Float,
    private val greatOdds: Float,
    private val goodOdds: Float,
    private val poorOdds: Float,
    private val badOdds: Float,
) {
    /** A tenth of the wallet, even odds-ish, pays not quite double. */
    SMALL(0.10f, 1.9f, 0.50f, 0.40f, 0.26f, 0.12f),

    /** A quarter. */
    MEDIUM(0.25f, 2.6f, 0.35f, 0.28f, 0.18f, 0.08f),

    /** Half of everything. */
    LARGE(0.50f, 4.0f, 0.22f, 0.17f, 0.11f, 0.05f),

    /**
     * All of it.
     *
     * Nine times the stake, and it lands about one time in twelve on her best
     * day. This is the one that can end a month of saving in an afternoon, and
     * it is meant to be: a high-risk tier that cannot really hurt is a large
     * button that does nothing.
     */
    ALL_IN(1.00f, 9.0f, 0.09f, 0.07f, 0.045f, 0.02f),
    ;

    /** The chance this lands, given how the shift ended. */
    fun chance(quality: OutcomeQuality): Float = when (quality) {
        OutcomeQuality.GREAT -> greatOdds
        OutcomeQuality.GOOD -> goodOdds
        OutcomeQuality.POOR -> poorOdds
        OutcomeQuality.BAD -> badOdds
    }

    /** What a stake of [amount] pays if it lands. */
    fun winnings(amount: Int): Int = (amount * payout).roundToInt()

    /** Profit on top of the stake — what the player actually gains. */
    fun profit(amount: Int): Int = winnings(amount) - amount

    /** Whether this is big enough to deserve being asked twice. */
    val isHighRisk: Boolean get() = this == LARGE || this == ALL_IN
}

/**
 * Money put on the outcome of a shift.
 *
 * The stake leaves the wallet the moment it is placed and, if it does not land,
 * it is simply gone — no consolation percentage, no "most of it back". That
 * softening is what made the old version a formality: losing cost ten percent
 * of the stake, which is less than the tip jar pays while you wait.
 *
 * There is still a real cost beyond the odds, and it is the reason the stake
 * belongs to a *shift* rather than to a spin: the money is locked for the whole
 * session, in a game where the reason to have money mid-shift is to buy the
 * boost that saves it. Going all in at the start of an idol run means having
 * nothing when her mood falls through the floor an hour later — which is also
 * the thing most likely to lose the bet.
 */
object Stakes {

    /** Nothing may be staked that the player does not have. */
    fun maxStake(snapshot: PetSnapshot): Int = snapshot.progress.money.coerceAtLeast(0)

    /**
     * The smallest bet worth offering.
     *
     * A tenth of a small wallet rounds to nothing, and a button that stakes 3 ¥
     * is a button that teaches the player the mechanic is pointless.
     */
    const val MINIMUM = 25

    /** What [tier] would put on the table right now, or 0 if it cannot. */
    fun amountFor(snapshot: PetSnapshot, tier: StakeTier): Int {
        val wallet = maxStake(snapshot)
        if (wallet < MINIMUM) return 0
        return (wallet * tier.walletFraction).roundToInt().coerceIn(MINIMUM, wallet)
    }

    /** Every size that can actually be placed with the wallet as it stands. */
    fun offered(snapshot: PetSnapshot): List<Pair<StakeTier, Int>> =
        StakeTier.entries.map { it to amountFor(snapshot, it) }
            .filter { (_, amount) -> amount > 0 }
            // Two buttons staking the same money because the wallet is tiny is
            // one button drawn twice.
            .distinctBy { (_, amount) -> amount }

    /**
     * The draw, fixed for the life of this bet.
     *
     * Derived from the session rather than rolled at settlement, so the result
     * cannot change by closing the app and reopening it, cannot be re-rolled by
     * a catch-up tick running twice, and is the same whether the shift finishes
     * on screen or hours later in a pocket. The player cannot see it before
     * settlement, which is all the secrecy a bet needs.
     */
    fun rollOf(startedAt: Long, amount: Int): Float =
        (scramble(startedAt * 31L + amount) % 100_000L) / 100_000f

    /** Whether the bet landed. */
    fun wins(tier: StakeTier, quality: OutcomeQuality, roll: Float): Boolean =
        roll < tier.chance(quality)

    /**
     * What comes back to the wallet: the winnings, or nothing at all.
     *
     * Deliberately total rather than incremental — the stake was already taken
     * when it was placed, so this is the entire settlement and there is no
     * second place that could quietly hand some of it back.
     */
    fun settle(amount: Int, tier: StakeTier, quality: OutcomeQuality, roll: Float): Int =
        if (wins(tier, quality, roll)) tier.winnings(amount) else 0

    private fun scramble(value: Long): Long {
        var x = value * -7046029254386353131L
        x = x xor (x ushr 32)
        x *= -4658895280553007687L
        x = x xor (x ushr 29)
        return abs(x)
    }
}
