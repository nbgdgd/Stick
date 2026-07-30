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
) {
    val energyPerMinute: Float get() = energyCost / durationMinutes

    fun isUnlocked(level: Int): Boolean = level >= requiredLevel
}

/**
 * The Phase-2 catalog. Later jobs pay disproportionately more but cost more
 * energy and lock up more of the day, so the choice is real rather than "always
 * pick the newest one".
 */
object Occupations {

    val WORK: List<Occupation> = listOf(
        Occupation("cafe", OccupationKind.WORK, requiredLevel = 1, durationMinutes = 30, energyCost = 22f, payout = 70),
        Occupation("shop", OccupationKind.WORK, requiredLevel = 3, durationMinutes = 60, energyCost = 40f, payout = 170),
        Occupation("office", OccupationKind.WORK, requiredLevel = 6, durationMinutes = 120, energyCost = 65f, payout = 420),
        Occupation("idol", OccupationKind.WORK, requiredLevel = 10, durationMinutes = 180, energyCost = 85f, payout = 950),
    )

    val STUDY: List<Occupation> = listOf(
        Occupation("school", OccupationKind.STUDY, requiredLevel = 1, durationMinutes = 30, energyCost = 16f, payout = 45),
        Occupation("course", OccupationKind.STUDY, requiredLevel = 4, durationMinutes = 60, energyCost = 32f, payout = 120),
        Occupation("university", OccupationKind.STUDY, requiredLevel = 8, durationMinutes = 120, energyCost = 55f, payout = 300),
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
