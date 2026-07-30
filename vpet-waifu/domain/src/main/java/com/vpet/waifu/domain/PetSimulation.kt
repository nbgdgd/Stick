package com.vpet.waifu.domain

import kotlin.math.max
import kotlin.math.min

/**
 * The whole game loop, as a pure function of (snapshot, now).
 *
 * Nothing here touches Android, a database or a clock: callers pass the current
 * time in. That makes every balance rule reproducible in a unit test and lets
 * the exact same code run from three places — the 60-second ticker inside the
 * overlay service, the 15-minute WorkManager job, and the catch-up that happens
 * the moment the app is reopened.
 *
 * Every action advances the world first, so an action can never be used to
 * dodge decay that was already owed.
 */
class PetSimulation(val tuning: PetTuning = PetTuning()) {

    /**
     * Pays off all whole minutes between [PetSnapshot.lastTickAt] and [nowMillis].
     *
     * Leftover seconds stay owed: `lastTickAt` moves by whole minutes only, so
     * ticking twice a second never drops fractions of a minute on the floor.
     */
    fun advanceTo(snapshot: PetSnapshot, nowMillis: Long): PetSnapshot {
        val owedMinutes = (nowMillis - snapshot.lastTickAt) / MS_PER_MINUTE
        if (owedMinutes <= 0) return snapshot

        // A long absence saturates the stats long before the cap, so simulating
        // the tail of the window (rather than the head) keeps the neglect
        // penalty aligned with real timestamps at no extra cost.
        val simulated = min(owedMinutes, tuning.maxCatchUpMinutes)
        var clock = nowMillis - simulated * MS_PER_MINUTE
        var stats = snapshot.stats
        var activity = snapshot.activity

        repeat(simulated.toInt()) {
            clock += MS_PER_MINUTE
            val sleeping = activity == PetActivity.SLEEPING
            stats = stats.adjusted(
                hungerBy = -tuning.hungerDecayPerMinute,
                energyBy = if (sleeping) tuning.energyRecoveryPerMinute else -tuning.energyDecayPerMinute,
            )
            stats = stats.copy(
                mood = driftMood(
                    mood = stats.mood,
                    hunger = stats.hunger,
                    energy = stats.energy,
                    minutesSinceInteraction = minutesBetween(snapshot.lastInteractionAt, clock),
                ),
            )
            // She gets up by herself once she is fully rested.
            if (sleeping && stats.energy >= PetStats.MAX) activity = PetActivity.AWAKE
        }

        return snapshot.copy(
            stats = stats,
            activity = activity,
            lastTickAt = snapshot.lastTickAt + owedMinutes * MS_PER_MINUTE,
        )
    }

    /** Feeding: a chunk of hunger back and a small lift in mood. No-op while asleep. */
    fun feed(snapshot: PetSnapshot, nowMillis: Long): PetSnapshot {
        val current = advanceTo(snapshot, nowMillis)
        if (!current.canFeed(tuning)) return current
        return current.copy(
            stats = current.stats.adjusted(hungerBy = tuning.feedHunger, moodBy = tuning.feedMood),
            lastInteractionAt = nowMillis,
        )
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
        )
    }

    /** Puts her to bed. Energy then climbs on the normal tick until she wakes up. */
    fun startSleep(snapshot: PetSnapshot, nowMillis: Long): PetSnapshot {
        val current = advanceTo(snapshot, nowMillis)
        if (!current.canSleep()) return current
        return current.copy(activity = PetActivity.SLEEPING, lastInteractionAt = nowMillis)
    }

    /** Wakes her early. Allowed from the app screen and by long-pressing the bubble. */
    fun wake(snapshot: PetSnapshot, nowMillis: Long): PetSnapshot {
        val current = advanceTo(snapshot, nowMillis)
        if (!current.isSleeping) return current
        return current.copy(activity = PetActivity.AWAKE, lastInteractionAt = nowMillis)
    }

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

    companion object {
        const val MS_PER_MINUTE = 60_000L
    }
}
