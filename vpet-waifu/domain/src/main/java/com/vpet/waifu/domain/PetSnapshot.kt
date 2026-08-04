package com.vpet.waifu.domain

/** What she is doing right now. Timed activities also carry an [ActivitySession]. */
enum class PetActivity { AWAKE, SLEEPING, WORKING, STUDYING, PLAYING }

/** Why a shop item is greyed out. */
enum class PurchaseBlock { LEVEL, MONEY, BUSY, STILL_PAYING, NOT_SICK, ALREADY_TODAY }

/** A short-lived reaction that overrides the idle animation. */
enum class Emote { EATING, LOVED, CELEBRATING }

/**
 * The animation state. One entry per distinct pose the character rig knows how
 * to draw; [PetState.of] is the whole state machine.
 */
enum class PetState {
    IDLE, HAPPY, HUNGRY, TIRED, SLEEPING, EATING, LOVED, WORKING, STUDYING, PLAYING, CELEBRATING, SICK;

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
            // Illness beats appetite: a sick pet looking merely peckish is how
            // the player misses that something is actually wrong.
            if (snapshot.isSick) return SICK
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
    val owned: Set<String> = setOf(Upgrades.DEFAULT_OUTFIT, Upgrades.DEFAULT_THEME),
    /** Which owned outfit she is wearing. */
    val outfit: String = Upgrades.DEFAULT_OUTFIT,
    /** Which owned theme the room is decorated in. */
    val theme: String = Upgrades.DEFAULT_THEME,
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
    /** Attachment — see [Bond]. Points, plus the day-and-total pair behind the daily cap. */
    val bondPoints: Int = 0,
    val bondDay: Long = 0L,
    val bondToday: Int = 0,
    /** When she fell ill, or 0 while healthy — see [PetSimulation] for how she falls ill. */
    val sickSince: Long = 0L,
    /** Recent minutes spent at rock bottom; crossing a threshold is what makes her ill. */
    val runDownMinutes: Float = 0f,
    /** What she is asking for right now, if anything. */
    val request: PetRequest? = null,
    /** The last request slot that voiced a wish, so one window never asks twice. */
    val lastRequestSlot: Long = 0L,
    /** How far her story has come, and the last chapter the player has seen. */
    val storyChapter: Int = 0,
    val storySeen: Int = 0,
    /** The path she committed to, if she has — see [Focus]. Permanent. */
    val focus: Focus? = null,
    /** The life lived so far, for the profile page and the achievements. */
    val shiftsWorked: Int = 0,
    val lessonsDone: Int = 0,
    val gamesPlayed: Int = 0,
    val mealsFed: Int = 0,
    val giftsGiven: Int = 0,
    val sicknessesNursed: Int = 0,
    val totalEarned: Int = 0,
    /** The day she was adopted. */
    val bornAt: Long = 0L,
    /** Her diary — see [Journal]. */
    val journal: List<JournalEntry> = emptyList(),
    /** Best round per mini-game. */
    val bestScores: Map<MiniGame, Int> = emptyMap(),
    /** The week the current goal belongs to, and the counter value it started from. */
    val goalWeek: Long = 0L,
    val goalBaseline: Int = 0,
    val goalRewarded: Boolean = false,
    /** The largest day-count anniversary already celebrated. */
    val celebratedMilestone: Int = 0,
    /** The check-in streak — see [PetSimulation.claimDaily]. */
    val streakDays: Int = 0,
    val bestStreak: Int = 0,
    /** The day index the streak was last credited on. */
    val lastLoginDay: Long = 0L,
    /** The day index the day off was last taken on, or 0 for never. */
    val dayOffDay: Long = 0L,
    /** What the check-in just paid, until the player has been told. */
    val pendingDaily: Int = 0,
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

    /**
     * Nothing has happened here yet.
     *
     * Deliberately about what the player *did*, not about the clock: a save
     * left alone for a week still has its shifts and its meals in it, while a
     * genuinely new one has none however long ago it was created. This is what
     * decides whether the app may offer to read a backup back in — offering it
     * over a game in progress would be alarming, and offering it over a fresh
     * start is the whole point.
     */
    val isFresh: Boolean
        get() = progress.exp == 0 &&
            totalEarned == 0 &&
            shiftsWorked == 0 &&
            lessonsDone == 0 &&
            mealsFed == 0 &&
            bondPoints == 0

    val isSick: Boolean get() = sickSince > 0L

    val bondLevel: Int get() = Bond.levelFor(bondPoints)

    val occupation: Occupation? get() = Occupations.byId(session?.occupationId)

    val level: Int get() = progress.level

    fun hasEffect(kind: EffectKind, nowMillis: Long): Boolean =
        effects.any { it.kind == kind && it.isActive(nowMillis) }

    fun canFeed(tuning: PetTuning = PetTuning()): Boolean =
        acceptsInteraction && stats.hunger < tuning.fullThreshold

    fun canSleep(): Boolean = activity == PetActivity.AWAKE

    fun canStart(occupation: Occupation, tuning: PetTuning = PetTuning()): Boolean =
        acceptsInteraction &&
            !isSick &&
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
            servable(item) &&
            (item.effect == null || !hasEffect(item.effect, nowMillis)) &&
            // Medicine is for the sick; sold to the healthy it is a coin sink
            // wearing a cross.
            (item.id != Shop.MEDICINE_ID || isSick) &&
            (item.id != Shop.DAY_OFF_ID || !dayOffTaken(nowMillis))

    /**
     * Whether she can take [item] right now, given what she is doing.
     *
     * Food needs her free: she cannot sit down to a bowl of ramen halfway
     * through a shift. The energy drink is the exception the item was written
     * for — its entire purpose is pushing through the shift she is already on,
     * and refusing to sell it until she clocks off left it doing nothing that
     * a nap does not do cheaper. Sleep is still off limits, for the drink as
     * for everything else: waking her is a separate decision with a button of
     * its own, not something a purchase should do behind the player's back.
     */
    private fun servable(item: ShopItem): Boolean =
        item.category != ShopCategory.FOOD ||
            acceptsInteraction ||
            (item.id == Shop.ENERGY_DRINK_ID && acceptsPat)

    /** Why [item] cannot be bought, for the shop card to explain. */
    fun blockedBy(item: ShopItem, nowMillis: Long): PurchaseBlock? = when {
        !item.isUnlocked(level) -> PurchaseBlock.LEVEL
        !progress.canAfford(item.price) -> PurchaseBlock.MONEY
        !servable(item) -> PurchaseBlock.BUSY
        item.effect != null && hasEffect(item.effect, nowMillis) -> PurchaseBlock.STILL_PAYING
        item.id == Shop.MEDICINE_ID && !isSick -> PurchaseBlock.NOT_SICK
        item.id == Shop.DAY_OFF_ID && dayOffTaken(nowMillis) -> PurchaseBlock.ALREADY_TODAY
        else -> null
    }

    /**
     * Whether today's day off has already been taken.
     *
     * Zero means "never", exactly as it does for [sickSince]: the day index of
     * the epoch is not a day anybody is playing on, and reading it as a real
     * date would lock the very first purchase behind a calendar day that ended
     * in 1970.
     */
    fun dayOffTaken(nowMillis: Long): Boolean =
        dayOffDay > 0L && dayOffDay == Events.dayOf(nowMillis)

    fun owns(upgradeId: String): Boolean = upgradeId in owned

    fun canBuy(upgrade: Upgrade): Boolean =
        !owns(upgrade.id) && upgrade.isUnlocked(level) && progress.canAfford(upgrade.price)

    /** Everything her purchases, today's event, her path and your history add up to. */
    fun modifiers(): UpgradeEffect =
        Upgrades.effectOf(owned) *
            (event?.kind?.effect() ?: UpgradeEffect.NONE) *
            (focus?.effect() ?: UpgradeEffect.NONE) *
            UpgradeEffect(neglect = Bond.neglectSoftening(bondPoints))

    fun state(nowMillis: Long, tuning: PetTuning = PetTuning()): PetState =
        PetState.of(this, nowMillis, tuning)

    companion object {
        fun initial(nowMillis: Long): PetSnapshot = PetSnapshot(
            stats = PetStats.INITIAL,
            lastTickAt = nowMillis,
            lastInteractionAt = nowMillis,
            passiveSince = nowMillis,
            bornAt = nowMillis,
        )
    }
}
