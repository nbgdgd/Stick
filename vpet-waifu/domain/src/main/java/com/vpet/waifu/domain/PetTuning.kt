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
    /** Energy drain while awake and idle. */
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

    /** A single home-cooked meal — the free action on the bubble. */
    val feedHunger: Float = 35f,
    val feedMood: Float = 5f,

    /** A head pat at full effect, and how long it takes to recharge to full. */
    val petMood: Float = 8f,
    val petFullEffectMinutes: Float = 5f,
    /** Spamming pats still does something, just very little. */
    val petMinimumMultiplier: Float = 0.15f,

    // --- work & study --------------------------------------------------------

    /** Working makes her hungrier than lounging around. */
    val busyHungerMultiplier: Float = 1.3f,
    /** A shift is a slow mood drain on top of the usual drift. */
    val workMoodPerMinute: Float = 0.2f,
    val studyMoodPerMinute: Float = 0.25f,
    /** Below this she is too tired to be sent anywhere. */
    val minimumEnergyToWork: Float = 15f,
    /** Work also teaches her something: EXP per minute on the clock. */
    val workExpPerMinute: Float = 0.35f,

    /** Mood at clock-out decides the payout. */
    val greatMoodThreshold: Float = 75f,
    val goodMoodThreshold: Float = 40f,
    val poorMoodThreshold: Float = 20f,
    val greatMultiplier: Float = 1.3f,
    val goodMultiplier: Float = 1f,
    val poorWorkMultiplier: Float = 0.7f,
    val badWorkMultiplier: Float = 0.45f,
    /** Studying in a bad mood fails harder than working in one. */
    val poorStudyMultiplier: Float = 0.55f,
    val badStudyMultiplier: Float = 0.3f,

    // --- effects -------------------------------------------------------------

    /** Multipliers applied while the matching [EffectKind] is live. */
    val hungerSurgeMultiplier: Float = 2.5f,
    val exhaustionMultiplier: Float = 1.6f,

    // --- thresholds the sprite FSM reads -------------------------------------

    val hungryThreshold: Float = 30f,
    val tiredThreshold: Float = 25f,
    val happyThreshold: Float = 80f,
    /** Feeding is pointless above this — the UI disables the button. */
    val fullThreshold: Float = 95f,

    // --- reactions -----------------------------------------------------------

    val eatingEmoteMillis: Long = 5_000,
    val lovedEmoteMillis: Long = 3_500,
    val celebrateEmoteMillis: Long = 6_000,

    /**
     * Upper bound on how many minutes a single catch-up run simulates.
     *
     * Hunger and energy saturate within ~100 minutes, so 12 hours is far past
     * the point where more iterations change the outcome; the cap just keeps a
     * "phone was off for a week" resume from spinning through 10k steps.
     */
    val maxCatchUpMinutes: Long = 12 * 60,
)
