package com.vpet.waifu.domain

/**
 * Every balance number in one place, per **real minute** of wall-clock time.
 *
 * It is a data class rather than a set of constants so that tests can dial a
 * value up or down without touching the simulation, and so that a later phase
 * can load a different curve per difficulty.
 */
data class PetTuning(
    /** Hunger drains constantly, awake or asleep. */
    val hungerDecayPerMinute: Float = 1f,
    /** Energy drain while awake. */
    val energyDecayPerMinute: Float = 1f,
    /** Energy gained while sleeping — sleeping is twice as fast as living. */
    val energyRecoveryPerMinute: Float = 2f,
    /** How fast mood walks towards its target. */
    val moodDriftPerMinute: Float = 0.5f,

    /** Mood target is the average of hunger and energy, minus the neglect penalty. */
    val moodHungerWeight: Float = 0.5f,
    val moodEnergyWeight: Float = 0.5f,
    /** One point of mood target is lost per this many minutes without attention… */
    val neglectMinutesPerPoint: Float = 15f,
    /** …up to this cap, so an ignored pet is sad but never instantly miserable. */
    val maxNeglectPenalty: Float = 30f,

    /** A single feeding. */
    val feedHunger: Float = 35f,
    val feedMood: Float = 5f,

    /** A head pat at full effect, and how long it takes to recharge to full. */
    val petMood: Float = 8f,
    val petFullEffectMinutes: Float = 5f,
    /** Spamming pats still does something, just very little. */
    val petMinimumMultiplier: Float = 0.15f,

    /** Below this, the FSM shows the hungry sprite. */
    val hungryThreshold: Float = 30f,
    /** Feeding is pointless above this — the UI disables the button. */
    val fullThreshold: Float = 95f,

    /**
     * Upper bound on how many minutes a single catch-up run simulates.
     *
     * Hunger and energy saturate within ~100 minutes, so 12 hours is far past
     * the point where more iterations change the outcome; the cap just keeps a
     * "phone was off for a week" resume from spinning through 10k steps.
     */
    val maxCatchUpMinutes: Long = 12 * 60,
)
