package com.vpet.waifu.domain

/** The single thing worth doing next. */
enum class AdviceKind { MEDICINE, FEED, SLEEP, PAT, PLAY, WORK, STUDY, QUEST, CHORE, FIND }

/**
 * Why an action is being suggested — the honest half of the recommendation.
 *
 * A game that highlights a button without saying why is not helping, it is
 * steering, and a player who works that out stops trusting every highlight it
 * ever shows. Each advice carries the reason it won, and the UI is expected to
 * put it in words next to the badge.
 */
enum class AdviceReason {
    /** A stat is in the red and everything else can wait. */
    CRITICAL,
    /** She is ill; nothing else in the game works properly until that is fixed. */
    SICK,
    /** Free money or EXP is sitting there uncollected. */
    UNCOLLECTED,
    /** Nothing is wrong — this is simply the best return on the time. */
    EFFICIENT,
}

/** One scored candidate. [occupationId] is set only for [AdviceKind.WORK]/[AdviceKind.STUDY]. */
data class Advice(
    val kind: AdviceKind,
    val reason: AdviceReason,
    val score: Float,
    val occupationId: String? = null,
    /** Which stat drove a [AdviceReason.CRITICAL] pick, for the explanation. */
    val stat: StatKind? = null,
)

enum class StatKind { HUNGER, ENERGY, MOOD }

/**
 * Every weight the recommendation turns on, in one place.
 *
 * Separated from the scoring so the balance can be moved without reading a line
 * of the algorithm, and separated from the UI so moving it never risks a
 * layout. This is the file to edit when the advice feels wrong.
 */
data class AdvisorWeights(
    /** Below this a stat is "in the red" and outranks anything profitable. */
    val criticalStat: Float = 30f,
    /** How much a point of a critical stat is worth against a point of anything else. */
    val criticalMultiplier: Float = 4f,
    /** Coins that count as one point of score. */
    val moneyPerPoint: Float = 90f,
    /** EXP that counts as one point of score. */
    val expPerPoint: Float = 55f,
    /** Points charged per hour an action occupies her. */
    val timeCostPerHour: Float = 1.4f,
    /** A stat point repaired, in score. */
    val statPerPoint: Float = 0.05f,
    /** Anything below this is not worth a badge at all. */
    val floor: Float = 0.15f,
)

/**
 * What to do next, and why.
 *
 * The rule the whole thing exists to enforce: **a stat in the red beats a good
 * wage.** Left to a pure returns calculation the game would cheerfully send a
 * starving pet to a double shift, because that is what pays — and the player
 * following the advice would watch her fall ill for doing as they were told.
 * So critical stats are weighted [AdvisorWeights.criticalMultiplier] times
 * heavier, and the only thing above them is medicine, because being ill
 * degrades every other action in the game.
 *
 * When nothing is wrong it falls through to plain efficiency: value earned per
 * hour committed. That is the number a player would work out on paper, which is
 * the point — the advice should be something they can check, not something they
 * have to believe.
 *
 * Returns null when she is busy or asleep, or when nothing clears the floor.
 * One badge or none: an interface where three things glow is an interface that
 * has recommended nothing.
 */
object Advisor {

    fun best(
        snapshot: PetSnapshot,
        nowMillis: Long,
        tuning: PetTuning = PetTuning(),
        weights: AdvisorWeights = AdvisorWeights(),
    ): Advice? = candidates(snapshot, nowMillis, tuning, weights)
        .filter { it.score >= weights.floor }
        .maxByOrNull { it.score }

    /** Every action worth scoring, already filtered to what is possible now. */
    fun candidates(
        snapshot: PetSnapshot,
        nowMillis: Long,
        tuning: PetTuning = PetTuning(),
        weights: AdvisorWeights = AdvisorWeights(),
    ): List<Advice> {
        val out = ArrayList<Advice>()
        val stats = snapshot.stats

        // Illness first, and on its own scale. Everything below is an
        // optimisation; this is the one that is broken.
        if (snapshot.isSick) {
            val medicine = Shop.byId(Shop.MEDICINE_ID)
            if (medicine != null && snapshot.canBuy(medicine, nowMillis)) {
                return listOf(Advice(AdviceKind.MEDICINE, AdviceReason.SICK, score = 100f))
            }
        }

        // Free money already earned and merely uncollected outranks anything
        // that has to be worked for — it costs nothing but a tap.
        if (Quests.claimable(snapshot).isNotEmpty()) {
            out += Advice(AdviceKind.QUEST, AdviceReason.UNCOLLECTED, score = 12f)
        }
        if (Finds.isWaiting(snapshot, nowMillis)) {
            out += Advice(AdviceKind.FIND, AdviceReason.UNCOLLECTED, score = 10f)
        }

        // A chore is always available, even mid-shift, so it is the fallback
        // that keeps the badge from disappearing while she is out.
        if (Chores.ready(snapshot, nowMillis).isNotEmpty()) {
            out += Advice(AdviceKind.CHORE, AdviceReason.EFFICIENT, score = 0.5f)
        }

        // Everything past here needs her free.
        if (!snapshot.acceptsInteraction) return out

        if (stats.hunger < tuning.fullThreshold) {
            out += statAdvice(
                AdviceKind.FEED, StatKind.HUNGER, stats.hunger,
                repaired = tuning.feedHunger, minutes = 0f, weights,
            )
        }
        if (stats.energy < 95f) {
            // Sleep is scored on what a full night would restore rather than on
            // a fixed number, so a nearly-rested pet is not sent to bed.
            val restored = (100f - stats.energy).coerceAtMost(45f)
            val minutes = restored / tuning.energyRecoveryPerMinute
            out += statAdvice(AdviceKind.SLEEP, StatKind.ENERGY, stats.energy, restored, minutes, weights)
        }
        if (stats.mood < 90f) {
            out += statAdvice(
                AdviceKind.PAT, StatKind.MOOD, stats.mood,
                repaired = tuning.petMood, minutes = 0f, weights,
            )
            // The arcade is the mood lever that pays; scored with its money and
            // EXP so it can beat a pat on merit rather than by category.
            val game = MiniGame.entries.maxByOrNull { bestGuessValue(it, snapshot, weights) }
            if (game != null && !snapshot.isSick) {
                val payout = Arcade.payout(game, game.targetScore, firstOfDay = !snapshot.playedToday(game))
                out += Advice(
                    kind = AdviceKind.PLAY,
                    reason = if (stats.mood < weights.criticalStat) AdviceReason.CRITICAL else AdviceReason.EFFICIENT,
                    score = value(payout.money, payout.exp, weights) +
                        statScore(StatKind.MOOD, stats.mood, game.moodGain(game.targetScore), weights) -
                        timeCost(game.durationSeconds / 60f, weights),
                    stat = StatKind.MOOD.takeIf { stats.mood < weights.criticalStat },
                )
            }
        }

        // Jobs, each on its own merits. A locked or unaffordable-in-energy job
        // is simply absent rather than scored badly, so the list never suggests
        // something the player cannot tap.
        Occupations.ALL.filter { snapshot.canStart(it, tuning) }.forEach { job ->
            val hours = job.durationMinutes / 60f
            val money = if (job.kind == OccupationKind.WORK) job.payout else 0
            val exp = if (job.kind == OccupationKind.STUDY) {
                job.payout
            } else {
                (tuning.workExpPerMinute * job.durationMinutes).toInt()
            }
            out += Advice(
                kind = if (job.kind == OccupationKind.WORK) AdviceKind.WORK else AdviceKind.STUDY,
                reason = AdviceReason.EFFICIENT,
                score = value(money, exp, weights) - timeCost(hours, weights) -
                    // A job that will leave her exhausted is worth less than one
                    // that will not, which is what stops the idol shift from
                    // being the permanent answer.
                    statScore(StatKind.ENERGY, stats.energy - job.energyCost, -job.energyCost, weights),
                occupationId = job.id,
            )
        }

        return out
    }

    private fun statAdvice(
        kind: AdviceKind,
        stat: StatKind,
        current: Float,
        repaired: Float,
        minutes: Float,
        weights: AdvisorWeights,
    ): Advice = Advice(
        kind = kind,
        reason = if (current < weights.criticalStat) AdviceReason.CRITICAL else AdviceReason.EFFICIENT,
        score = statScore(stat, current, repaired, weights) - timeCost(minutes / 60f, weights),
        stat = stat.takeIf { current < weights.criticalStat },
    )

    /**
     * What repairing [repaired] points of a stat sitting at [current] is worth.
     *
     * The multiplier is applied to the part of the repair that lands *below*
     * the critical line, not to the whole of it. Otherwise a full meal for a pet
     * at 29% hunger would score four times a full meal for one at 31%, and the
     * advice would flicker between two completely different suggestions as a
     * single point drifted past a threshold.
     */
    private fun statScore(
        stat: StatKind,
        current: Float,
        repaired: Float,
        weights: AdvisorWeights,
    ): Float {
        if (repaired <= 0f) {
            // A cost, not a repair: charged at the plain rate.
            return repaired * weights.statPerPoint * -1f
        }
        val belowLine = (weights.criticalStat - current).coerceIn(0f, repaired)
        val above = repaired - belowLine
        return (belowLine * weights.criticalMultiplier + above) * weights.statPerPoint
    }

    private fun value(money: Int, exp: Int, weights: AdvisorWeights): Float =
        money / weights.moneyPerPoint + exp / weights.expPerPoint

    private fun timeCost(hours: Float, weights: AdvisorWeights): Float =
        hours * weights.timeCostPerHour

    /** Rough worth of a game, used only to pick which one to suggest. */
    private fun bestGuessValue(game: MiniGame, snapshot: PetSnapshot, weights: AdvisorWeights): Float {
        val payout = Arcade.payout(game, game.targetScore, firstOfDay = !snapshot.playedToday(game))
        return value(payout.money, payout.exp, weights)
    }
}
