package com.vpet.waifu.domain

/** What the pet is doing. Phase 1 only knows awake and asleep. */
enum class PetActivity { AWAKE, SLEEPING }

/**
 * The visual state machine. Deliberately tiny: one sprite per state, resolved
 * from the snapshot with [PetState.of]. Work, study and play states arrive in
 * later phases.
 */
enum class PetState { IDLE, HUNGRY, SLEEPING;

    companion object {
        fun of(snapshot: PetSnapshot, tuning: PetTuning = PetTuning()): PetState = when {
            snapshot.activity == PetActivity.SLEEPING -> SLEEPING
            snapshot.stats.hunger <= tuning.hungryThreshold -> HUNGRY
            else -> IDLE
        }
    }
}

/**
 * The complete persisted state of the pet.
 *
 * [lastTickAt] is the instant the simulation has been advanced to; everything
 * between it and "now" is unsimulated debt that [PetSimulation.advanceTo] pays
 * off. Storing the instant instead of relying on a timer is what makes the pet
 * keep living while the process is dead.
 */
data class PetSnapshot(
    val stats: PetStats,
    val activity: PetActivity,
    val lastTickAt: Long,
    val lastInteractionAt: Long,
) {
    val isSleeping: Boolean get() = activity == PetActivity.SLEEPING

    /** She ignores taps while asleep — the only thing that reaches her is being woken. */
    val acceptsInteraction: Boolean get() = !isSleeping

    fun canFeed(tuning: PetTuning = PetTuning()): Boolean =
        acceptsInteraction && stats.hunger < tuning.fullThreshold

    fun canSleep(): Boolean = !isSleeping

    fun state(tuning: PetTuning = PetTuning()): PetState = PetState.of(this, tuning)

    companion object {
        fun initial(nowMillis: Long): PetSnapshot = PetSnapshot(
            stats = PetStats.INITIAL,
            activity = PetActivity.AWAKE,
            lastTickAt = nowMillis,
            lastInteractionAt = nowMillis,
        )
    }
}
