package com.vpet.waifu.domain

/** What she is doing right now. Timed activities also carry an [ActivitySession]. */
enum class PetActivity { AWAKE, SLEEPING, WORKING, STUDYING, PLAYING }

/** A short-lived reaction that overrides the idle animation. */
enum class Emote { EATING, LOVED, CELEBRATING }

/**
 * The animation state. One entry per distinct pose the character rig knows how
 * to draw; [PetState.of] is the whole state machine.
 */
enum class PetState {
    IDLE, HAPPY, HUNGRY, TIRED, SLEEPING, EATING, LOVED, WORKING, STUDYING, PLAYING, CELEBRATING;

    /** Timed activities and sleep block interaction; everything else allows it. */
    val isBusy: Boolean
        get() = this == WORKING || this == STUDYING || this == SLEEPING

    companion object {
        fun of(snapshot: PetSnapshot, nowMillis: Long, tuning: PetTuning = PetTuning()): PetState {
            // Order matters: what she is *doing* beats how she feels, and a
            // reaction to the player beats her resting mood.
            when (snapshot.activity) {
                PetActivity.SLEEPING -> return SLEEPING
                PetActivity.WORKING -> return WORKING
                PetActivity.STUDYING -> return STUDYING
                PetActivity.PLAYING -> return PLAYING
                PetActivity.AWAKE -> Unit
            }
            if (nowMillis < snapshot.emoteUntil) {
                when (snapshot.emote) {
                    Emote.EATING -> return EATING
                    Emote.LOVED -> return LOVED
                    Emote.CELEBRATING -> return CELEBRATING
                    null -> Unit
                }
            }
            return when {
                snapshot.stats.hunger <= tuning.hungryThreshold -> HUNGRY
                snapshot.stats.energy <= tuning.tiredThreshold -> TIRED
                snapshot.stats.mood >= tuning.happyThreshold -> HAPPY
                else -> IDLE
            }
        }
    }
}

/**
 * The complete persisted state of the pet.
 *
 * [lastTickAt] is the instant the simulation has been advanced to; everything
 * between it and "now" is unsimulated debt that [PetSimulation.advanceTo] pays
 * off. Storing the instant instead of relying on a timer is what makes the pet
 * keep living — and finish her shift — while the process is dead.
 */
data class PetSnapshot(
    val stats: PetStats,
    val progress: PetProgress = PetProgress(),
    val activity: PetActivity = PetActivity.AWAKE,
    val session: ActivitySession? = null,
    val effects: List<ActiveEffect> = emptyList(),
    val lastOutcome: ActivityOutcome? = null,
    val emote: Emote? = null,
    val emoteUntil: Long = 0L,
    val lastTickAt: Long,
    val lastInteractionAt: Long,
) {
    val isSleeping: Boolean get() = activity == PetActivity.SLEEPING

    val isBusy: Boolean
        get() = activity == PetActivity.WORKING || activity == PetActivity.STUDYING

    /** She ignores taps while asleep or on the clock. */
    val acceptsInteraction: Boolean get() = activity == PetActivity.AWAKE

    val occupation: Occupation? get() = Occupations.byId(session?.occupationId)

    val level: Int get() = progress.level

    fun hasEffect(kind: EffectKind, nowMillis: Long): Boolean =
        effects.any { it.kind == kind && it.isActive(nowMillis) }

    fun canFeed(tuning: PetTuning = PetTuning()): Boolean =
        acceptsInteraction && stats.hunger < tuning.fullThreshold

    fun canSleep(): Boolean = activity == PetActivity.AWAKE

    fun canStart(occupation: Occupation, tuning: PetTuning = PetTuning()): Boolean =
        acceptsInteraction &&
            occupation.isUnlocked(level) &&
            stats.energy >= tuning.minimumEnergyToWork

    fun canBuy(item: ShopItem): Boolean =
        item.isUnlocked(level) && progress.canAfford(item.price) &&
            (item.category != ShopCategory.FOOD || acceptsInteraction)

    fun state(nowMillis: Long, tuning: PetTuning = PetTuning()): PetState =
        PetState.of(this, nowMillis, tuning)

    companion object {
        fun initial(nowMillis: Long): PetSnapshot = PetSnapshot(
            stats = PetStats.INITIAL,
            lastTickAt = nowMillis,
            lastInteractionAt = nowMillis,
        )
    }
}
