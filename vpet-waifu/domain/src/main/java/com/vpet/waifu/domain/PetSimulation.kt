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
        val occupation = snapshot.occupation
        val busy = snapshot.isBusy && occupation != null

        val hungerRate = tuning.hungerDecayPerMinute *
            (if (busy) tuning.busyHungerMultiplier else 1f) *
            (if (snapshot.hasEffect(EffectKind.HUNGER_SURGE, clock)) tuning.hungerSurgeMultiplier else 1f)

        val exhaustion =
            if (snapshot.hasEffect(EffectKind.EXHAUSTION, clock)) tuning.exhaustionMultiplier else 1f

        val energyDelta = when (snapshot.activity) {
            PetActivity.SLEEPING -> tuning.energyRecoveryPerMinute
            PetActivity.WORKING, PetActivity.STUDYING ->
                -(occupation?.energyPerMinute ?: tuning.energyDecayPerMinute) * exhaustion
            // The mini-game is played in short bursts; it costs energy per tap,
            // not per minute, so idling on the game screen is not a drain.
            PetActivity.PLAYING, PetActivity.AWAKE -> -tuning.energyDecayPerMinute * exhaustion
        }

        val activityMoodCost = when (snapshot.activity) {
            PetActivity.WORKING -> tuning.workMoodPerMinute
            PetActivity.STUDYING -> tuning.studyMoodPerMinute
            else -> 0f
        }

        var stats = snapshot.stats.adjusted(hungerBy = -hungerRate, energyBy = energyDelta)
        stats = stats.copy(
            mood = driftMood(
                mood = stats.mood,
                hunger = stats.hunger,
                energy = stats.energy,
                minutesSinceInteraction = minutesBetween(interactionAnchor, clock),
            ) - activityMoodCost,
        ).let { PetStats.coerced(it.hunger, it.energy, it.mood) }

        var next = snapshot.copy(stats = stats)

        // She gets up by herself once she is fully rested.
        if (next.isSleeping && stats.energy >= PetStats.MAX) {
            next = next.copy(activity = PetActivity.AWAKE)
        }

        val session = next.session
        if (busy && session != null && occupation != null) {
            if (clock >= session.endsAt) {
                next = complete(next, occupation, clock, cancelled = false, completedFraction = 1f)
            } else if (stats.energy <= PetStats.MIN) {
                // Running out of energy on the clock ends the shift badly.
                next = complete(
                    next, occupation, clock,
                    cancelled = true,
                    completedFraction = session.progress(clock),
                )
            }
        }

        return next
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
        val session = current.session ?: return current
        val occupation = current.occupation ?: return current.copy(
            session = null,
            activity = PetActivity.AWAKE,
        )
        return complete(
            current, occupation, nowMillis,
            cancelled = true,
            completedFraction = session.progress(nowMillis),
        )
    }

    /**
     * Ends a session and pays it out.
     *
     * [completedFraction] is 1 for a full shift and the elapsed share for one
     * cut short, so leaving early is not free but is not a total loss either.
     */
    private fun complete(
        snapshot: PetSnapshot,
        occupation: Occupation,
        atMillis: Long,
        cancelled: Boolean,
        completedFraction: Float,
    ): PetSnapshot {
        val quality = qualityFor(snapshot.stats.mood)
        val multiplier = multiplierFor(quality, occupation.kind) * completedFraction.coerceIn(0f, 1f)

        val money: Int
        val exp: Int
        when (occupation.kind) {
            OccupationKind.WORK -> {
                money = (occupation.payout * multiplier).roundToInt()
                exp = (occupation.durationMinutes * tuning.workExpPerMinute * completedFraction).roundToInt()
            }
            OccupationKind.STUDY -> {
                money = 0
                exp = (occupation.payout * multiplier).roundToInt()
            }
        }

        return snapshot.copy(
            progress = snapshot.progress.plus(money = money, exp = exp),
            activity = PetActivity.AWAKE,
            session = null,
            lastOutcome = ActivityOutcome(
                occupationId = occupation.id,
                kind = occupation.kind,
                money = money,
                exp = exp,
                quality = if (cancelled) OutcomeQuality.POOR else quality,
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
        if (current.activity != PetActivity.PLAYING) return current
        return current.copy(
            activity = PetActivity.AWAKE,
            stats = current.stats.adjusted(
                energyBy = -TapGame.energyCost(score),
                moodBy = TapGame.moodGain(score),
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

        return current.copy(
            stats = current.stats.adjusted(
                hungerBy = item.hunger,
                energyBy = item.energy,
                moodBy = item.mood,
            ),
            progress = current.progress.plus(money = item.money - item.price, exp = item.exp),
            effects = effects,
            lastInteractionAt = nowMillis,
        ).withEmote(emote, nowMillis, emoteMillis)
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
