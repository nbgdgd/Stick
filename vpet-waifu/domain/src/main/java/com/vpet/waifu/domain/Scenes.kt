package com.vpet.waifu.domain

import kotlin.math.abs

/**
 * A small moment with two ways to answer.
 *
 * Everything else in the game is a button that does a known thing. This is the
 * one place the player is asked a question — and the design rule that makes it
 * safe to ask is that **neither answer is wrong**. Both pay; they pay in
 * different currencies. Telling her to rest costs the money she would have
 * earned and buys mood and attachment; telling her to push on is the reverse.
 * A player who picks by instinct is never punished for it, and a player who
 * picks deliberately is optimising rather than guessing.
 *
 * That matters more here than anywhere else in the file tree, because a
 * two-option prompt with a hidden right answer is how a game teaches people to
 * distrust its prompts — and this one is meant to be the moment she reads as a
 * person rather than a set of bars.
 *
 * One per day, chosen from the date like every other calendar-driven thing.
 */
enum class SceneKind {
    /** She is tired and there is still a shift in her. */
    LONG_DAY,

    /** She found something in a shop window. */
    WINDOW_SHOPPING,

    /** A neighbour asked her for a hand. */
    NEIGHBOUR,

    /** She has been reading the same page for an hour. */
    STUCK_ON_A_PAGE,

    /** It is raining and neither of you has anywhere to be. */
    RAINY_AFTERNOON,
}

/**
 * What one answer is worth.
 *
 * Deliberately expressed in the same four currencies every other reward in the
 * game uses, so a scene cannot smuggle in a fifth kind of prize that nothing
 * else can give and nothing else can take away.
 */
data class SceneOutcome(
    val money: Int = 0,
    val exp: Int = 0,
    val mood: Float = 0f,
    val energy: Float = 0f,
    val bond: Int = 0,
)

object Scenes {

    /** The scene [day] carries. Stable for the whole day, like the event. */
    fun forDay(day: Long): SceneKind =
        SceneKind.entries[(scramble(day) % SceneKind.entries.size).toInt()]

    /**
     * What each answer gives, at [level].
     *
     * The pairs are built to be close in total worth and different in *shape*
     * — see [ScenesTest], which is what actually holds that line.
     *
     * Every option carries something that scales with level, and that is not
     * decoration. The first draft gave the caring answers only mood and bond,
     * which do not scale, against money that does: at level one "go and lie
     * down" was worth twice "one more hour", and by level twenty-five it was
     * worth half. Both ends are a scene with an obvious answer, which is the
     * one thing this file exists not to be. So the kind reply pays EXP as well
     * — she rested, and something of the day stuck — and the mix is what makes
     * the two different rather than whether they grow at all.
     */
    fun outcomeOf(kind: SceneKind, option: SceneOption, level: Int): SceneOutcome =
        when (kind) {
            SceneKind.LONG_DAY -> when (option) {
                // "Go and lie down." Costs the evening, buys the relationship.
                SceneOption.FIRST -> SceneOutcome(exp = 10 + level * 2, mood = 12f, energy = 14f, bond = 2)
                // "One more hour." Pays, and she feels it.
                SceneOption.SECOND -> SceneOutcome(money = 160 + level * 9, mood = -4f, energy = -8f)
            }
            SceneKind.WINDOW_SHOPPING -> when (option) {
                // Buy her the thing. The money goes; what comes back is her —
                // and the afternoon out, which she learns something from.
                SceneOption.FIRST -> SceneOutcome(
                    money = -(40 + level * 2), exp = 26 + level * 4, mood = 18f, bond = 3,
                )
                // Talk her out of it, and put it aside instead.
                SceneOption.SECOND -> SceneOutcome(money = 95 + level * 7, exp = 10 + level * 2, mood = -3f)
            }
            SceneKind.NEIGHBOUR -> when (option) {
                // Help for nothing. Word gets round, and she learns the job.
                SceneOption.FIRST -> SceneOutcome(exp = 30 + level * 5, mood = 8f, bond = 2)
                // Help for a fee.
                SceneOption.SECOND -> SceneOutcome(money = 100 + level * 8, energy = -6f)
            }
            SceneKind.STUCK_ON_A_PAGE -> when (option) {
                // Sit with her until it clicks.
                SceneOption.FIRST -> SceneOutcome(exp = 30 + level * 4, mood = 6f, bond = 1)
                // Tell her to leave it — and put the evening to use instead.
                SceneOption.SECOND -> SceneOutcome(money = 40 + level * 3, mood = 14f, energy = 10f)
            }
            SceneKind.RAINY_AFTERNOON -> when (option) {
                // Do nothing at all, together. She still takes something from it.
                SceneOption.FIRST -> SceneOutcome(exp = 14 + level * 2, mood = 16f, energy = 8f, bond = 3)
                // Use the afternoon.
                SceneOption.SECOND -> SceneOutcome(
                    money = 95 + level * 5, exp = 22 + level * 3, mood = -2f,
                )
            }
        }

    private fun scramble(value: Long): Long {
        var x = (value + 0x5EED) * -7046029254386353131L
        x = x xor (x ushr 32)
        x *= -4658895280553007687L
        x = x xor (x ushr 29)
        return abs(x)
    }
}

/** Which of the two replies was picked. */
enum class SceneOption { FIRST, SECOND }
