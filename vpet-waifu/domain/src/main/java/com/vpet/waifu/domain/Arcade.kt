package com.vpet.waifu.domain

import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * What a round of the arcade is worth.
 *
 * The arcade used to pay `score / pointsPerCoin` and nothing else — linear
 * money, no EXP at all. Two things were wrong with that. Linear pay means the
 * only question a player ever asks is "can I keep playing", so the ceiling is
 * whatever their thumb can stand; and paying no EXP made the arcade the one
 * activity that could not move the level bar, which quietly told the player it
 * was a toy rather than part of the game.
 *
 * So: a curve, and two of them.
 *
 * [MONEY_EXPONENT] is below one, which means the first half of a good round is
 * worth more per point than the second — turning up and playing badly still
 * pays, and a perfect round is better but not overwhelmingly so. [EXP_EXPONENT]
 * is *flatter still*, and that gap is the whole design: money rewards the
 * result, EXP rewards the attempt. A player who is bad at rhythm games still
 * levels up by playing them, and a player farming money still has a reason to
 * pick the game they are actually good at.
 *
 * Past [SOFT_CAP] the curve goes logarithmic. Without it, one player with an
 * unusually good thumb turns the best mini-game into the entire economy — the
 * same hole the cash advance and the tip jar each had to be dug out of. Above
 * 150% of a good round you are still rewarded, just never enough to make the
 * rest of the game pointless.
 */
object Arcade {

    /** Where the curve stops being a power law and starts being a logarithm. */
    const val SOFT_CAP = 1.5f

    /** Rewards the result: a great round pays visibly more than a poor one. */
    const val MONEY_EXPONENT = 0.8f

    /** Rewards the attempt: flatter, so a bad round still teaches her something. */
    const val EXP_EXPONENT = 0.6f

    /** How fast the reward still climbs once the soft cap is passed. */
    const val OVERSHOOT_SLOPE = 0.35f

    /** The first round of each game each day is worth half again as much. */
    const val FIRST_OF_DAY_BONUS = 1.5f

    /** What [LUCKY] doubles. Kept here so the number is next to the curve. */
    const val LUCKY_MULTIPLIER = 2f

    /**
     * Score, as a fraction of a good round, shaped by [exponent].
     *
     * Split out from the payouts because it is the part worth testing on its
     * own: it must be continuous at the soft cap (a player crossing 150% should
     * not see the reward jump or stall) and monotonic everywhere.
     */
    fun shape(ratio: Float, exponent: Float): Float {
        if (ratio <= 0f) return 0f
        if (ratio <= SOFT_CAP) return ratio.pow(exponent)
        // Continuous by construction: the logarithm starts from the value the
        // power law ends at, and ln(1) is zero.
        val atCap = SOFT_CAP.pow(exponent)
        return atCap + OVERSHOOT_SLOPE * ln(1f + (ratio - SOFT_CAP))
    }

    /** What [score] on [game] pays, before the first-of-day and lucky bonuses. */
    fun basePayout(game: MiniGame, score: Int): Payout {
        val ratio = score.toFloat() / game.targetScore
        return Payout(
            money = (game.baseReward * shape(ratio, MONEY_EXPONENT)).roundToInt(),
            exp = (game.baseExp * shape(ratio, EXP_EXPONENT)).roundToInt(),
        )
    }

    /**
     * The full payout, with every multiplier that applies to this round.
     *
     * [firstOfDay] is per game rather than per day, which is the point: three
     * games each worth half again as much once a day is an invitation to play
     * all three, where one flat daily bonus would just be collected on whichever
     * one you already liked.
     */
    fun payout(
        game: MiniGame,
        score: Int,
        firstOfDay: Boolean,
        lucky: Boolean = false,
    ): Payout {
        val base = basePayout(game, score)
        var multiplier = 1f
        if (firstOfDay) multiplier *= FIRST_OF_DAY_BONUS
        if (lucky) multiplier *= LUCKY_MULTIPLIER
        if (multiplier == 1f) return base
        return Payout(
            money = (base.money * multiplier).roundToInt(),
            exp = (base.exp * multiplier).roundToInt(),
        )
    }

    /**
     * Whether [score] counts as a clean round for the combo streak.
     *
     * Deliberately below the target: the streak buff is meant to reward playing
     * well three times running, not playing perfectly three times running, and
     * a bar set at "a good round" would only ever be cleared by people who did
     * not need the help.
     */
    fun isCleanRound(game: MiniGame, score: Int): Boolean =
        score >= (game.targetScore * COMBO_THRESHOLD).roundToInt()

    /** Fraction of a good round that keeps a combo alive. */
    const val COMBO_THRESHOLD = 0.6f

    /** Clean rounds in a row that earn the combo reward. */
    const val COMBO_LENGTH = 3

    /** Money and EXP, together, because the UI always shows them together. */
    data class Payout(val money: Int, val exp: Int) {
        val isEmpty: Boolean get() = money == 0 && exp == 0
    }
}
