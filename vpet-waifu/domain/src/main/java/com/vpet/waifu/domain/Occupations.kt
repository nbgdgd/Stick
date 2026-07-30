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
    /** Mood the whole session costs, on top of the usual drift. */
    val moodCost: Float = 3f,
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
 *  - **cafe** — the best hourly rate in the game, and it barely touches her
 *    mood, but half an hour at a time caps what it can ever earn in a day.
 *  - **shop** — a shade worse per hour, cheap on energy, unremarkable.
 *  - **office** — the worst rate and by far the worst for her mood. It exists
 *    because two hours is two hours: you set it going and stop thinking.
 *  - **idol** — pays like the cafe over three hours and pays *mood*, not costs
 *    it, but it eats almost all her energy, so it is a whole evening committed.
 *
 * The same idea in study: school is the efficient one, university the one that
 * gets a lot done at once while making her miserable.
 */
object Occupations {

    val WORK: List<Occupation> = listOf(
        Occupation(
            "cafe", OccupationKind.WORK, requiredLevel = 1,
            durationMinutes = 30, energyCost = 15f, payout = 105, moodCost = 1f,
        ),
        Occupation(
            "shop", OccupationKind.WORK, requiredLevel = 3,
            durationMinutes = 60, energyCost = 24f, payout = 195, moodCost = 5f,
        ),
        Occupation(
            "office", OccupationKind.WORK, requiredLevel = 6,
            durationMinutes = 120, energyCost = 46f, payout = 360, moodCost = 16f,
        ),
        Occupation(
            "idol", OccupationKind.WORK, requiredLevel = 10,
            durationMinutes = 180, energyCost = 88f, payout = 620, moodCost = -12f,
        ),
    )

    val STUDY: List<Occupation> = listOf(
        Occupation(
            "school", OccupationKind.STUDY, requiredLevel = 1,
            durationMinutes = 30, energyCost = 12f, payout = 62, moodCost = 3f,
        ),
        Occupation(
            "course", OccupationKind.STUDY, requiredLevel = 4,
            durationMinutes = 60, energyCost = 22f, payout = 115, moodCost = 7f,
        ),
        Occupation(
            "university", OccupationKind.STUDY, requiredLevel = 8,
            durationMinutes = 120, energyCost = 40f, payout = 215, moodCost = 20f,
        ),
    )

    val ALL: List<Occupation> = WORK + STUDY

    fun byId(id: String?): Occupation? = ALL.firstOrNull { it.id == id }
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
