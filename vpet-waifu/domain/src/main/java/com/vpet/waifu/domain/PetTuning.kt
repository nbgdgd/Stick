package com.vpet.waifu.domain

/**
 * Every balance number in one place, per **real minute** of wall-clock time.
 *
 * It is a data class rather than a set of constants so that tests can dial a
 * value up or down without touching the simulation, and so that a later phase
 * can load a different curve per difficulty.
 */
data class PetTuning(
    /**
     * Hunger drains constantly, awake or asleep.
     *
     * The rates below are per real minute and deliberately gentle: at a point a
     * minute a full pet emptied in an hour and a half, which made the game a
     * chore rather than something to check in on. These give her most of a
     * working day before anything is urgent.
     */
    val hungerDecayPerMinute: Float = 0.35f,
    /** Energy drain while awake and idle. */
    val energyDecayPerMinute: Float = 0.30f,
    /** Energy gained while sleeping — sleeping is twice as fast as living. */
    val energyRecoveryPerMinute: Float = 2.5f,
    /** How fast mood walks towards its target. */
    val moodDriftPerMinute: Float = 0.4f,

    /** Mood target is the average of hunger and energy, minus the neglect penalty. */
    val moodHungerWeight: Float = 0.5f,
    val moodEnergyWeight: Float = 0.5f,
    /** One point of mood target is lost per this many minutes without attention… */
    val neglectMinutesPerPoint: Float = 25f,
    /** …up to this cap, so an ignored pet is sad but never instantly miserable. */
    val maxNeglectPenalty: Float = 25f,

    /** A single home-cooked meal — the free action on the bubble. */
    val feedHunger: Float = 30f,
    val feedMood: Float = 5f,

    /** A head pat at full effect, and how long it takes to recharge to full. */
    val petMood: Float = 8f,
    val petFullEffectMinutes: Float = 5f,
    /**
     * Spamming pats still does something, just very little.
     *
     * 0.2 of [petMood] is +1.6, which is what a tap on a category tile or a tab
     * is worth once you are leaning on it — small enough that mashing is not a
     * strategy, large enough that the bar visibly answers every tap.
     */
    val petMinimumMultiplier: Float = 0.2f,
    /**
     * EXP a full-value pat is worth while she is studying.
     *
     * Floored on the way out, so mashing (which pins the multiplier at
     * [petMinimumMultiplier]) earns exactly nothing and only a pat left long
     * enough to be worth a third of full value carries a point.
     */
    val patExp: Float = 3f,

    // --- work & study --------------------------------------------------------

    /** Working makes her hungrier than lounging around. */
    val busyHungerMultiplier: Float = 1.4f,
    /** A shift is a slow mood drain on top of the usual drift. */
    val workMoodPerMinute: Float = 0.10f,
    val studyMoodPerMinute: Float = 0.13f,
    /** Below this she is too tired to be sent anywhere. */
    val minimumEnergyToWork: Float = 12f,
    /** Work also teaches her something: EXP per minute on the clock. */
    val workExpPerMinute: Float = 0.5f,

    // --- the tip jar ---------------------------------------------------------

    /**
     * Loose change, arriving on its own every few seconds.
     *
     * This is the idle half of the game: money that turns up for watching her
     * rather than for sending her somewhere. It is deliberately **only paid
     * while something is driving the clock at this rate** — see
     * [passiveGraceMillis] — so it cannot be farmed by closing the app for a
     * night, and it never competes with a shift running in your pocket.
     */
    val passiveTickMillis: Long = 3_000,
    /** Coins per tick at level 1… */
    val passiveCoinsPerTick: Float = 1f,
    /** …plus this much for every level above it, before upgrades. */
    val passiveCoinsPerLevel: Float = 0.125f,
    /**
     * A gap larger than this means nothing was ticking, so nothing is owed.
     *
     * Without it the tip jar would quietly become the best job in the game:
     * eight hours in a pocket is 9,600 ticks, which is thirty idol concerts.
     *
     * Ninety seconds rather than sixty because the floating bubble ticks once a
     * minute and it should count — she is on your screen, which is the whole
     * test this window is trying to apply. Sixty exactly would have made it a
     * coin toss against clock jitter. The background worker runs every fifteen
     * minutes and is nowhere near it.
     */
    val passiveGraceMillis: Long = 90_000,
    /**
     * How much of a day the jar is worth, in minutes of watching.
     *
     * The grace window stops the jar paying for time nobody spent watching, but
     * on its own it still leaves "leave the app open on a charger all day" as
     * the best-paid activity in the game. An hour's worth a day, then it is dry
     * until tomorrow: enough that checking in is always worth something,
     * nowhere near enough to replace sending her to work.
     */
    val passiveMinutesPerDay: Int = 60,

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

    /**
     * How long a mini-game round may stay open.
     *
     * A round lasts twenty seconds, so anything beyond this means the screen
     * that started it is gone. Without a ceiling the pet would sit in PLAYING
     * for good, with every other action locked behind it.
     */
    val maxPlayMinutes: Float = 3f,

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
