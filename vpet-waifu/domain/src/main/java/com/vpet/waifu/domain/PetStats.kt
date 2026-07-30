package com.vpet.waifu.domain

/**
 * The three Phase-1 stats, each held in [MIN]..[MAX].
 *
 * Stats are floats because the simulation ticks with sub-point rates (mood
 * drifts by half a point per minute, for example). The UI rounds them for
 * display; the model keeps the precision so that long catch-up runs don't
 * accumulate rounding error.
 */
data class PetStats(
    val hunger: Float,
    val energy: Float,
    val mood: Float,
) {
    init {
        require(hunger in MIN..MAX) { "hunger out of range: $hunger" }
        require(energy in MIN..MAX) { "energy out of range: $energy" }
        require(mood in MIN..MAX) { "mood out of range: $mood" }
    }

    /** Returns a copy with every value coerced back into range. */
    fun adjusted(
        hungerBy: Float = 0f,
        energyBy: Float = 0f,
        moodBy: Float = 0f,
    ): PetStats = PetStats(
        hunger = clamp(hunger + hungerBy),
        energy = clamp(energy + energyBy),
        mood = clamp(mood + moodBy),
    )

    companion object {
        const val MIN = 0f
        const val MAX = 100f

        /** A freshly adopted pet: fed, rested, cheerful. */
        val INITIAL = PetStats(hunger = 80f, energy = 80f, mood = 70f)

        fun clamp(value: Float): Float = value.coerceIn(MIN, MAX)

        /** Builds stats from untrusted input (e.g. a database row) without throwing. */
        fun coerced(hunger: Float, energy: Float, mood: Float): PetStats =
            PetStats(clamp(hunger), clamp(energy), clamp(mood))
    }
}
