package com.vpet.waifu.domain

/** What she is doing right now. Timed activities also carry an [ActivitySession]. */
enum class PetActivity { AWAKE, SLEEPING, WORKING, STUDYING, PLAYING }

/** Why a shop item is greyed out. */
enum class PurchaseBlock { LEVEL, MONEY, BUSY, STILL_PAYING }

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
    /** Everything bought once and kept — see [Upgrades]. */
    val owned: Set<String> = setOf(Upgrades.DEFAULT_OUTFIT),
    /** Which owned outfit she is wearing. */
    val outfit: String = Upgrades.DEFAULT_OUTFIT,
    /** Today's event, if one has landed. */
    val event: PetEvent? = null,
    /** The last thing she was fed, and how many times running. */
    val lastMealId: String? = null,
    val repeatedMeals: Int = 0,
    /** The instant the tip jar was last settled, and the fraction of a coin left over. */
    val passiveSince: Long = 0L,
    val passiveBank: Float = 0f,
    /** Which day the jar is counting, and how much of that day's allowance is spent. */
    val passiveDay: Long = 0L,
    val passivePaidToday: Int = 0,
) {
    val isSleeping: Boolean get() = activity == PetActivity.SLEEPING

    val isBusy: Boolean
        get() = activity == PetActivity.WORKING || activity == PetActivity.STUDYING

    /** Feeding, sending her out, starting a game: all need her free and awake. */
    val acceptsInteraction: Boolean get() = activity == PetActivity.AWAKE

    /**
     * A pat, though, lands whenever she is awake — including on the clock.
     *
     * Tapping her was gated on being idle, which meant the one thing the player
     * does most often silently did nothing for the two hours she was at the
     * office. Cheering someone on at work is not an interruption; only sleep is
     * off limits, because waking her is a different action with a button of its
     * own.
     */
    val acceptsPat: Boolean get() = activity != PetActivity.SLEEPING

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

    /**
     * Whether [item] can be bought right now.
     *
     * The last clause is what stops the cash advance from being an infinite
     * money printer. An advance costs 160 and pays 480, and the bill is three
     * hours of doubled hunger drain — but buying a second one merely *replaced*
     * that timer, so tapping it twenty times in a row banked 6,400 coins and
     * still cost exactly three hours. A debt she has not finished paying blocks
     * the next one, so the price is now unavoidably real.
     */
    fun canBuy(item: ShopItem, nowMillis: Long = 0L): Boolean =
        item.isUnlocked(level) && progress.canAfford(item.price) &&
            (item.category != ShopCategory.FOOD || acceptsInteraction) &&
            (item.effect == null || !hasEffect(item.effect, nowMillis))

    /** Why [item] cannot be bought, for the shop card to explain. */
    fun blockedBy(item: ShopItem, nowMillis: Long): PurchaseBlock? = when {
        !item.isUnlocked(level) -> PurchaseBlock.LEVEL
        !progress.canAfford(item.price) -> PurchaseBlock.MONEY
        item.category == ShopCategory.FOOD && !acceptsInteraction -> PurchaseBlock.BUSY
        item.effect != null && hasEffect(item.effect, nowMillis) -> PurchaseBlock.STILL_PAYING
        else -> null
    }

    fun owns(upgradeId: String): Boolean = upgradeId in owned

    fun canBuy(upgrade: Upgrade): Boolean =
        !owns(upgrade.id) && upgrade.isUnlocked(level) && progress.canAfford(upgrade.price)

    /** Everything her permanent purchases and today's event add up to. */
    fun modifiers(): UpgradeEffect =
        Upgrades.effectOf(owned) * (event?.kind?.effect() ?: UpgradeEffect.NONE)

    fun state(nowMillis: Long, tuning: PetTuning = PetTuning()): PetState =
        PetState.of(this, nowMillis, tuning)

    companion object {
        fun initial(nowMillis: Long): PetSnapshot = PetSnapshot(
            stats = PetStats.INITIAL,
            lastTickAt = nowMillis,
            lastInteractionAt = nowMillis,
            passiveSince = nowMillis,
        )
    }
}
