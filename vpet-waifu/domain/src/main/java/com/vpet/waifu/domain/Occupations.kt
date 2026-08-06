package com.vpet.waifu.domain

enum class OccupationKind { WORK, STUDY }

/**
 * A timed activity she can be sent off to do.
 *
 * Ids are plain strings, not string resources: the domain must stay free of
 * Android, so the UI owns the translation from `"cafe"` to a display name.
 */
data class Occupation(
    val id: String,
    val kind: OccupationKind,
    val requiredLevel: Int,
    val durationMinutes: Int,
    /** Total energy the whole session costs, spread evenly across it. */
    val energyCost: Float,
    /** Money for [OccupationKind.WORK], EXP for [OccupationKind.STUDY]. */
    val payout: Int,
    /**
     * Mood the whole session costs — negative means the shift *lifts* her
     * spirits, and every job now does: watching her cheer up while the coins
     * arrive is the point of watching at all. Better jobs lift more.
     */
    val moodCost: Float = -5f,
) {
    val energyPerMinute: Float get() = energyCost / durationMinutes

    val moodPerMinute: Float get() = moodCost / durationMinutes

    /** What an hour of it is worth, which is how a player actually compares two. */
    val payPerHour: Float get() = payout * 60f / durationMinutes

    fun isUnlocked(level: Int): Boolean = level >= requiredLevel
}

/**
 * The catalog.
 *
 * Deliberately *not* ordered by rate. Every tier used to pay strictly more per
 * minute than the one before it, which meant the newest unlock obsoleted
 * everything else and the list was really a single job with a changing name.
 * Now each has something it is best at and something it is worst at:
 *
 *  - **cafe** — the best hourly rate in the game, but half an hour at a time
 *    caps what it can ever earn in a day, and it only mildly cheers her up.
 *  - **shop** — a shade worse per hour, cheap on energy, unremarkable.
 *  - **office** — the worst rate but the gentlest energy drain, and two hours
 *    is two hours: you set it going and stop thinking.
 *  - **idol** — the biggest total and pure euphoria, but it eats almost all
 *    her energy, so it is a whole evening committed.
 *
 * The same idea in study: school is the efficient one, university the one that
 * gets a lot done at once while making her miserable.
 *
 * **Every duration here is half what it was**, and so is every payout, energy
 * cost and mood cost — the rate per hour is untouched, only the granularity
 * changed. The old shortest job was half an hour and the longest three, which
 * assumed a player who opens the app twice a day; the actual rhythm is a glance
 * every twenty minutes, and a game whose shortest commitment outlasts the
 * session is a game you close mid-task. Cutting the grain in half without
 * touching the wage means the same money for the same hour, arriving at a pace
 * somebody is present for. The relative shape survives intact: the café is
 * still the best rate, the office still the one you set going and forget, the
 * idol stage still an evening committed.
 */
object Occupations {

    val WORK: List<Occupation> = listOf(
        // Mood per minute climbs with the tier — a café shift is pleasant, the
        // idol stage is euphoric — while the café keeps the best hourly wage
        // and the office the gentlest energy drain, so no tier obsoletes another.
        Occupation(
            "cafe", OccupationKind.WORK, requiredLevel = 1,
            durationMinutes = 15, energyCost = 8f, payout = 53, moodCost = -3f,
        ),
        Occupation(
            "shop", OccupationKind.WORK, requiredLevel = 3,
            durationMinutes = 30, energyCost = 12f, payout = 98, moodCost = -8f,
        ),
        Occupation(
            "office", OccupationKind.WORK, requiredLevel = 6,
            durationMinutes = 60, energyCost = 23f, payout = 180, moodCost = -28f,
        ),
        Occupation(
            "idol", OccupationKind.WORK, requiredLevel = 10,
            durationMinutes = 90, energyCost = 44f, payout = 310, moodCost = -50f,
        ),
    )

    val STUDY: List<Occupation> = listOf(
        Occupation(
            "school", OccupationKind.STUDY, requiredLevel = 1,
            durationMinutes = 15, energyCost = 6f, payout = 31, moodCost = 2f,
        ),
        Occupation(
            "course", OccupationKind.STUDY, requiredLevel = 4,
            durationMinutes = 30, energyCost = 11f, payout = 58, moodCost = 4f,
        ),
        Occupation(
            "university", OccupationKind.STUDY, requiredLevel = 8,
            durationMinutes = 60, energyCost = 20f, payout = 108, moodCost = 10f,
        ),
    )

    val ALL: List<Occupation> = WORK + STUDY

    fun byId(id: String?): Occupation? = ALL.firstOrNull { it.id == id }
}

/**
 * The milestones inside a shift.
 *
 * Wages already accrue every simulated minute, which is the right arithmetic
 * and an invisible one: a number in a corner creeping up by ones is not an
 * event, and a shift with no events is a progress bar you watch. A checkpoint
 * is the event — a third of the way, two thirds of the way, something happens.
 *
 * What it pays is **mood, and only mood**. Two other designs were tried and
 * both were quietly wrong:
 *
 *  - *A slice of the wage, held back and handed over in lumps.* Rounding a
 *    slice off an integer payout loses coins, so the shift stops being worth
 *    what the catalogue says it is; and a flat lump on a shift whose wage
 *    scales with her mood pays full rate at the milestones of a miserable
 *    shift.
 *  - *A point of attachment.* A shift already grants [Bond.SHIFT] at the end,
 *    so this was paying twice for one shift — and bond is the gauge that is
 *    supposed to take weeks. Worse, it was load-bearing elsewhere: the extra
 *    points tipped her over the first story chapter's threshold, and the two
 *    chapters that then fired put 150 coins in the wallet that no shift had
 *    earned. A reward that silently accelerates a different system is not a
 *    small reward, it is a bug with a friendly name.
 *
 * Mood has neither problem, and it is not a token either: it feeds straight
 * into [PetSimulation.qualityFor], so on a long shift — where mood drains far
 * enough to threaten the tier the wage is multiplied by — two nudges are a real
 * defence of the payout rather than a decoration.
 */
object Shifts {

    /** How far through the shift each checkpoint lands. */
    val MARKS: List<Float> = listOf(1f / 3f, 2f / 3f)

    /** What crossing one is worth, in mood. */
    const val MOOD = 4f

    /** How many checkpoints a session at [progress] has passed. */
    fun passed(progress: Float): Int = MARKS.count { progress >= it }
}

/** How well a finished session went — drives the payout and the result card. */
enum class OutcomeQuality { GREAT, GOOD, POOR, BAD }

/**
 * A session in progress. [endsAt] is absolute so the countdown survives the
 * process being killed, and the catch-up tick can finish a session that
 * completed while the phone was in a pocket.
 */
data class ActivitySession(
    val occupationId: String,
    val startedAt: Long,
    val endsAt: Long,
    /**
     * Pay earned so far, including the fraction of a coin not yet handed over.
     *
     * Wages accrue every simulated minute, but the wallet holds whole units, so
     * the remainder is banked here and moved across as it crosses an integer.
     * Keeping the fraction is what stops a long shift from quietly losing most
     * of its value to rounding, a minute at a time.
     */
    val accruedPay: Float = 0f,
    val paidOut: Int = 0,
    val accruedExp: Float = 0f,
    val paidExp: Int = 0,
    /** How many of [Shifts.MARKS] have already been handed over. */
    val checkpointsPaid: Int = 0,
    /** Money staked on this shift going well — see [Stakes]. */
    val stake: Int = 0,
) {
    fun remainingMillis(nowMillis: Long): Long = (endsAt - nowMillis).coerceAtLeast(0)

    fun progress(nowMillis: Long): Float {
        val total = (endsAt - startedAt).toFloat()
        if (total <= 0f) return 1f
        return ((nowMillis - startedAt) / total).coerceIn(0f, 1f)
    }
}

/** The result of a session, kept until the UI acknowledges it. */
data class ActivityOutcome(
    val occupationId: String,
    val kind: OccupationKind,
    val money: Int,
    val exp: Int,
    val quality: OutcomeQuality,
    val cancelled: Boolean,
    val completedAt: Long,
)
