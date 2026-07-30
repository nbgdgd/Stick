package com.vpet.waifu.domain

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The whole game loop, as a pure function of (snapshot, now).
 *
 * Nothing here touches Android, a database or a clock: callers pass the current
 * time in. That makes every balance rule reproducible in a unit test and lets
 * the exact same code run from four places — the frame ticker inside the
 * overlay service, the 15-minute WorkManager job, the widget refresh, and the
 * catch-up that happens the moment the app is reopened.
 *
 * Every action advances the world first, so an action can never be used to
 * dodge decay that was already owed, and a shift that finished while the phone
 * was in a pocket pays out the moment anything looks at the save file.
 */
class PetSimulation(val tuning: PetTuning = PetTuning()) {

    /**
     * Pays off all whole minutes between [PetSnapshot.lastTickAt] and [nowMillis].
     *
     * Leftover seconds stay owed: `lastTickAt` moves by whole minutes only, so
     * ticking sixty times a second never drops fractions of a minute.
     */
    fun advanceTo(snapshot: PetSnapshot, nowMillis: Long): PetSnapshot {
        val owedMinutes = (nowMillis - snapshot.lastTickAt) / MS_PER_MINUTE
        if (owedMinutes <= 0) return snapshot.withExpiredEffectsDropped(nowMillis)

        // A long absence saturates the stats long before the cap, so simulating
        // the tail of the window (rather than the head) keeps the neglect
        // penalty aligned with real timestamps at no extra cost.
        val simulated = min(owedMinutes, tuning.maxCatchUpMinutes)
        var clock = nowMillis - simulated * MS_PER_MINUTE
        var current = snapshot

        repeat(simulated.toInt()) {
            clock += MS_PER_MINUTE
            current = step(current, clock, snapshot.lastInteractionAt)
        }

        return current
            .copy(lastTickAt = snapshot.lastTickAt + owedMinutes * MS_PER_MINUTE)
            .withExpiredEffectsDropped(nowMillis)
    }

    /** One simulated minute, ending at [clock]. */
    private fun step(snapshot: PetSnapshot, clock: Long, interactionAnchor: Long): PetSnapshot {
        // Non-null exactly when she is on the clock, so it doubles as the "busy"
        // flag and keeps the session handling below free of null checks.
        val occupation = if (snapshot.isBusy) snapshot.occupation else null
        val busy = occupation != null
        val mods = snapshot.modifiers()

        val hungerRate = tuning.hungerDecayPerMinute * mods.hungerDecay *
            (if (busy) tuning.busyHungerMultiplier else 1f) *
            (if (snapshot.hasEffect(EffectKind.HUNGER_SURGE, clock)) tuning.hungerSurgeMultiplier else 1f)

        val exhaustion =
            if (snapshot.hasEffect(EffectKind.EXHAUSTION, clock)) tuning.exhaustionMultiplier else 1f

        val energyDelta = when (snapshot.activity) {
            PetActivity.SLEEPING -> tuning.energyRecoveryPerMinute * mods.sleepSpeed
            PetActivity.WORKING, PetActivity.STUDYING ->
                -(occupation?.energyPerMinute ?: tuning.energyDecayPerMinute) *
                    exhaustion * mods.energyDecay
            // The mini-game is played in short bursts; it costs energy per tap,
            // not per minute, so idling on the game screen is not a drain.
            PetActivity.PLAYING, PetActivity.AWAKE ->
                -tuning.energyDecayPerMinute * exhaustion * mods.energyDecay
        }

        // Per job, not per kind: the whole point of the catalog is that a shift
        // at the cafe leaves her cheerful and one at the office does not.
        val activityMoodCost = when (snapshot.activity) {
            PetActivity.WORKING, PetActivity.STUDYING -> occupation?.moodPerMinute ?: 0f
            else -> 0f
        }

        var stats = snapshot.stats.adjusted(hungerBy = -hungerRate, energyBy = energyDelta)
        stats = stats.copy(
            mood = driftMood(
                mood = stats.mood,
                hunger = stats.hunger,
                energy = stats.energy,
                minutesSinceInteraction = minutesBetween(interactionAnchor, clock) * mods.neglect,
            ) - activityMoodCost,
        ).let { PetStats.coerced(it.hunger, it.energy, it.mood) }

        var next = snapshot.copy(stats = stats)
        next = rollEvent(next, clock)

        // She gets up by herself once she is fully rested.
        if (next.isSleeping && stats.energy >= PetStats.MAX) {
            next = next.copy(activity = PetActivity.AWAKE)
        }

        val session = next.session
        if (occupation != null && session != null) {
            next = accrue(next, occupation)
            if (clock >= session.endsAt) {
                next = complete(next, occupation, clock, cancelled = false)
            } else if (stats.energy <= PetStats.MIN) {
                // Running out of energy on the clock ends the shift there and
                // then. She keeps what she has already been paid.
                next = complete(next, occupation, clock, cancelled = true)
            }
        }

        // The mini-game is played in one sitting. If the screen that started it
        // went away — a tab switch, a killed process — nothing would ever end
        // the session, and every other action stays blocked behind it. Time
        // itself closes it instead.
        if (next.activity == PetActivity.PLAYING &&
            minutesBetween(next.lastInteractionAt, clock) >= tuning.maxPlayMinutes
        ) {
            next = next.copy(activity = PetActivity.AWAKE)
        }

        return next
    }

    /**
     * The day's event, landing at most once.
     *
     * Whether a day carries one is a pure function of the day number, so it
     * does not matter which of the four drivers reaches a given day first —
     * they all agree. Storing it is what makes it *land*: its instant effects
     * are applied here, and it stays on the snapshot until the day rolls over.
     */
    private fun rollEvent(snapshot: PetSnapshot, clock: Long): PetSnapshot {
        val day = Events.dayOf(clock)
        if (snapshot.event?.day == day) return snapshot

        val kind = Events.forDay(day)
            ?: return if (snapshot.event == null) snapshot else snapshot.copy(event = null)

        return snapshot.copy(
            event = PetEvent(kind, day),
            progress = snapshot.progress.plus(money = kind.instantMoney()),
            stats = snapshot.stats.adjusted(energyBy = kind.instantEnergy()),
        )
    }

    /** Marks the day's event as read, so it stops being announced. */
    fun acknowledgeEvent(snapshot: PetSnapshot, nowMillis: Long): PetSnapshot {
        val event = snapshot.event ?: return snapshot
        if (event.acknowledged) return snapshot
        return snapshot.copy(event = event.copy(seenAt = nowMillis))
    }

    /**
     * One minute of wages.
     *
     * Pay is earned as the shift is worked rather than handed over at the end,
     * so a two-hour job is not two hours of nothing. The rate is scaled by how
     * she feels *this* minute, which means a shift that starts cheerful and
     * ends miserable pays somewhere in between — no retroactive adjustment, and
     * nothing can ever be taken back out of the wallet.
     */
    private fun accrue(snapshot: PetSnapshot, occupation: Occupation): PetSnapshot {
        val session = snapshot.session ?: return snapshot
        val mods = snapshot.modifiers()
        val rate = multiplierFor(qualityFor(snapshot.stats.mood), occupation.kind)
        val perMinute = occupation.payout.toFloat() / occupation.durationMinutes * rate

        var pay = session.accruedPay
        var exp = session.accruedExp
        when (occupation.kind) {
            OccupationKind.WORK -> {
                pay += perMinute * mods.pay
                exp += tuning.workExpPerMinute * mods.study
            }
            OccupationKind.STUDY -> exp += perMinute * mods.study
        }

        val payDue = pay.toInt() - session.paidOut
        val expDue = exp.toInt() - session.paidExp
        return snapshot.copy(
            progress = snapshot.progress.plus(money = payDue, exp = expDue),
            session = session.copy(
                accruedPay = pay,
                paidOut = session.paidOut + payDue,
                accruedExp = exp,
                paidExp = session.paidExp + expDue,
            ),
        )
    }

    // --- care actions --------------------------------------------------------

    /** The free home-cooked meal behind the bubble's feed button. */
    fun feed(snapshot: PetSnapshot, nowMillis: Long): PetSnapshot {
        val current = advanceTo(snapshot, nowMillis)
        if (!current.canFeed(tuning)) return current
        return current.copy(
            stats = current.stats.adjusted(hungerBy = tuning.feedHunger, moodBy = tuning.feedMood),
            lastInteractionAt = nowMillis,
        ).withEmote(Emote.EATING, nowMillis, tuning.eatingEmoteMillis)
    }

    /**
     * A head pat. Full value once [PetTuning.petFullEffectMinutes] have passed
     * since the last bit of attention, scaled down (but never to nothing) when
     * the player mashes it.
     */
    fun pet(snapshot: PetSnapshot, nowMillis: Long): PetSnapshot {
        val current = advanceTo(snapshot, nowMillis)
        if (!current.acceptsInteraction) return current
        val sinceInteraction = minutesBetween(current.lastInteractionAt, nowMillis)
        val multiplier = max(
            tuning.petMinimumMultiplier,
            min(1f, sinceInteraction / tuning.petFullEffectMinutes),
        )
        return current.copy(
            stats = current.stats.adjusted(moodBy = tuning.petMood * multiplier),
            lastInteractionAt = nowMillis,
        ).withEmote(Emote.LOVED, nowMillis, tuning.lovedEmoteMillis)
    }

    /** Puts her to bed. Energy then climbs on the normal tick until she wakes up. */
    fun startSleep(snapshot: PetSnapshot, nowMillis: Long): PetSnapshot {
        val current = advanceTo(snapshot, nowMillis)
        if (!current.canSleep()) return current
        return current.copy(
            activity = PetActivity.SLEEPING,
            lastInteractionAt = nowMillis,
            emote = null,
            emoteUntil = 0L,
        )
    }

    /** Wakes her early. Allowed from the app, the panel and a long press. */
    fun wake(snapshot: PetSnapshot, nowMillis: Long): PetSnapshot {
        val current = advanceTo(snapshot, nowMillis)
        if (!current.isSleeping) return current
        return current.copy(activity = PetActivity.AWAKE, lastInteractionAt = nowMillis)
    }

    // --- work & study --------------------------------------------------------

    fun startOccupation(snapshot: PetSnapshot, occupation: Occupation, nowMillis: Long): PetSnapshot {
        val current = advanceTo(snapshot, nowMillis)
        if (!current.canStart(occupation, tuning)) return current
        return current.copy(
            activity = when (occupation.kind) {
                OccupationKind.WORK -> PetActivity.WORKING
                OccupationKind.STUDY -> PetActivity.STUDYING
            },
            session = ActivitySession(
                occupationId = occupation.id,
                startedAt = nowMillis,
                endsAt = nowMillis + occupation.durationMinutes * MS_PER_MINUTE,
            ),
            lastInteractionAt = nowMillis,
            emote = null,
            emoteUntil = 0L,
        )
    }

    /** Calling her home early. She is paid for the time she actually put in. */
    fun cancelOccupation(snapshot: PetSnapshot, nowMillis: Long): PetSnapshot {
        val current = advanceTo(snapshot, nowMillis)
        current.session ?: return current
        val occupation = current.occupation ?: return current.copy(
            session = null,
            activity = PetActivity.AWAKE,
        )
        return complete(current, occupation, nowMillis, cancelled = true)
    }

    /**
     * Ends a session.
     *
     * Almost everything has been paid already, minute by minute; all that is
     * left is the fraction of a coin still banked in the session. Leaving early
     * therefore needs no penalty formula — she simply stops earning, and keeps
     * what the hours she actually worked were worth.
     */
    private fun complete(
        snapshot: PetSnapshot,
        occupation: Occupation,
        atMillis: Long,
        cancelled: Boolean,
    ): PetSnapshot {
        val session = snapshot.session
        val remainderPay = ((session?.accruedPay ?: 0f).roundToInt() - (session?.paidOut ?: 0))
            .coerceAtLeast(0)
        val remainderExp = ((session?.accruedExp ?: 0f).roundToInt() - (session?.paidExp ?: 0))
            .coerceAtLeast(0)

        val money = (session?.paidOut ?: 0) + remainderPay
        val exp = (session?.paidExp ?: 0) + remainderExp

        return snapshot.copy(
            progress = snapshot.progress.plus(money = remainderPay, exp = remainderExp),
            activity = PetActivity.AWAKE,
            session = null,
            lastOutcome = ActivityOutcome(
                occupationId = occupation.id,
                kind = occupation.kind,
                money = money,
                exp = exp,
                quality = if (cancelled) OutcomeQuality.POOR else qualityFor(snapshot.stats.mood),
                cancelled = cancelled,
                completedAt = atMillis,
            ),
        ).withEmote(Emote.CELEBRATING, atMillis, tuning.celebrateEmoteMillis)
    }

    fun qualityFor(mood: Float): OutcomeQuality = when {
        mood >= tuning.greatMoodThreshold -> OutcomeQuality.GREAT
        mood >= tuning.goodMoodThreshold -> OutcomeQuality.GOOD
        mood >= tuning.poorMoodThreshold -> OutcomeQuality.POOR
        else -> OutcomeQuality.BAD
    }

    fun multiplierFor(quality: OutcomeQuality, kind: OccupationKind): Float = when (quality) {
        OutcomeQuality.GREAT -> tuning.greatMultiplier
        OutcomeQuality.GOOD -> tuning.goodMultiplier
        OutcomeQuality.POOR ->
            if (kind == OccupationKind.WORK) tuning.poorWorkMultiplier else tuning.poorStudyMultiplier
        OutcomeQuality.BAD ->
            if (kind == OccupationKind.WORK) tuning.badWorkMultiplier else tuning.badStudyMultiplier
    }

    /** Preview of what a session would pay right now — the UI shows it on the card. */
    fun projectedPayout(snapshot: PetSnapshot, occupation: Occupation): Int {
        val multiplier = multiplierFor(qualityFor(snapshot.stats.mood), occupation.kind)
        return (occupation.payout * multiplier).roundToInt()
    }

    fun acknowledgeOutcome(snapshot: PetSnapshot): PetSnapshot = snapshot.copy(lastOutcome = null)

    // --- mini-game -----------------------------------------------------------

    fun startPlaying(snapshot: PetSnapshot, nowMillis: Long): PetSnapshot {
        val current = advanceTo(snapshot, nowMillis)
        if (!current.acceptsInteraction) return current
        return current.copy(activity = PetActivity.PLAYING, lastInteractionAt = nowMillis)
    }

    /** Ends the mini-game and applies its score. Playing lifts mood but costs energy. */
    fun finishPlaying(snapshot: PetSnapshot, score: Int, nowMillis: Long): PetSnapshot {
        val current = advanceTo(snapshot, nowMillis)
        // Not gated on still being PLAYING: a round can outlive the screen that
        // started it, and the score should still count when it comes back.
        if (score <= 0 && current.activity != PetActivity.PLAYING) return current
        val mods = current.modifiers()
        return current.copy(
            activity = PetActivity.AWAKE,
            stats = current.stats.adjusted(
                energyBy = -TapGame.energyCost(score),
                moodBy = TapGame.moodGain(score) * mods.play,
            ),
            progress = current.progress.plus(money = TapGame.coins(score)),
            lastInteractionAt = nowMillis,
        ).withEmote(Emote.CELEBRATING, nowMillis, tuning.celebrateEmoteMillis)
    }

    // --- shop ----------------------------------------------------------------

    /** Buys and immediately applies [item]. There is no inventory to manage. */
    fun buy(snapshot: PetSnapshot, item: ShopItem, nowMillis: Long): PetSnapshot {
        val current = advanceTo(snapshot, nowMillis)
        if (!current.canBuy(item)) return current

        val effects = if (item.effect != null) {
            current.effects.filterNot { it.kind == item.effect } +
                ActiveEffect(item.effect, nowMillis + item.effectMinutes * MS_PER_MINUTE)
        } else {
            current.effects
        }

        val emote = when (item.category) {
            ShopCategory.FOOD -> Emote.EATING
            ShopCategory.GIFT -> Emote.LOVED
            ShopCategory.PILL -> Emote.CELEBRATING
        }
        val emoteMillis = when (item.category) {
            ShopCategory.FOOD -> tuning.eatingEmoteMillis
            ShopCategory.GIFT -> tuning.lovedEmoteMillis
            ShopCategory.PILL -> tuning.celebrateEmoteMillis
        }

        // Feeding her the same thing over and over is something she notices.
        val repeated = if (item.category == ShopCategory.FOOD) {
            if (current.lastMealId == item.id) current.repeatedMeals + 1 else 1
        } else {
            current.repeatedMeals
        }

        return current.copy(
            stats = current.stats.adjusted(
                hungerBy = item.hunger,
                energyBy = item.energy,
                moodBy = item.mood,
            ),
            progress = current.progress.plus(money = item.money - item.price, exp = item.exp),
            effects = effects,
            lastInteractionAt = nowMillis,
            lastMealId = if (item.category == ShopCategory.FOOD) item.id else current.lastMealId,
            repeatedMeals = repeated,
        ).withEmote(emote, nowMillis, emoteMillis)
    }

    /**
     * Buys something permanent.
     *
     * Unlike everything else in the shop this is not consumed, so it is the
     * only purchase that is worth saving for rather than spending on.
     */
    fun buyUpgrade(snapshot: PetSnapshot, upgrade: Upgrade, nowMillis: Long): PetSnapshot {
        val current = advanceTo(snapshot, nowMillis)
        if (!current.canBuy(upgrade)) return current
        return current.copy(
            progress = current.progress.plus(money = -upgrade.price),
            owned = current.owned + upgrade.id,
            // Buying an outfit puts it on; there is no reason to make the
            // player then find it in a list and tap it again.
            outfit = if (upgrade.kind == UpgradeKind.OUTFIT) upgrade.id else current.outfit,
            lastInteractionAt = nowMillis,
        ).withEmote(Emote.CELEBRATING, nowMillis, tuning.celebrateEmoteMillis)
    }

    /** Changes into an outfit she already owns. */
    fun wear(snapshot: PetSnapshot, upgradeId: String, nowMillis: Long): PetSnapshot {
        val current = advanceTo(snapshot, nowMillis)
        val upgrade = Upgrades.byId(upgradeId) ?: return current
        if (upgrade.kind != UpgradeKind.OUTFIT || !current.owns(upgradeId)) return current
        return current.copy(outfit = upgradeId, lastInteractionAt = nowMillis)
            .withEmote(Emote.LOVED, nowMillis, tuning.lovedEmoteMillis)
    }

    // --- mood ----------------------------------------------------------------

    /**
     * Where mood is heading right now: the average of the two hard stats, minus
     * a penalty that grows the longer she is left alone.
     */
    fun moodTarget(stats: PetStats, minutesSinceInteraction: Float): Float {
        val base = tuning.moodHungerWeight * stats.hunger + tuning.moodEnergyWeight * stats.energy
        val neglect = min(tuning.maxNeglectPenalty, minutesSinceInteraction / tuning.neglectMinutesPerPoint)
        return PetStats.clamp(base - neglect)
    }

    private fun driftMood(mood: Float, hunger: Float, energy: Float, minutesSinceInteraction: Float): Float {
        val target = moodTarget(PetStats.coerced(hunger, energy, mood), minutesSinceInteraction)
        val step = (target - mood).coerceIn(-tuning.moodDriftPerMinute, tuning.moodDriftPerMinute)
        return PetStats.clamp(mood + step)
    }

    private fun minutesBetween(fromMillis: Long, toMillis: Long): Float =
        max(0f, (toMillis - fromMillis).toFloat() / MS_PER_MINUTE)

    private fun PetSnapshot.withEmote(emote: Emote, nowMillis: Long, durationMillis: Long) =
        copy(emote = emote, emoteUntil = nowMillis + durationMillis)

    private fun PetSnapshot.withExpiredEffectsDropped(nowMillis: Long): PetSnapshot {
        val live = effects.filter { it.isActive(nowMillis) }
        return if (live.size == effects.size) this else copy(effects = live)
    }

    companion object {
        const val MS_PER_MINUTE = 60_000L
    }
}

/**
 * Scoring for the tap mini-game. Playing is the mood lever that does not need
 * money — but it burns energy, so it cannot replace sleeping and eating.
 */
object TapGame {
    const val DURATION_SECONDS = 20

    fun moodGain(score: Int): Float = min(32f, 6f + score * 0.9f)

    fun energyCost(score: Int): Float = 8f + score * 0.15f

    /** A few coins so an empty wallet is never a dead end. */
    fun coins(score: Int): Int = score / 3
}
