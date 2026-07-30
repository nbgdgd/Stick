package com.vpet.waifu.domain

import kotlin.math.abs

/** Something that happened to her, unprompted. */
enum class EventKind {
    /** A good day: everything she does pays a little more. */
    LUCKY_DAY,

    /** A cold. Energy drains faster and work pays worse until it passes. */
    COLD,

    /** Money in the post, from nobody in particular. */
    LETTER,

    /** She has been practising: today's studying sticks better. */
    INSPIRED,

    /** She could not sleep. Today starts tired. */
    RESTLESS,
}

/** An event that has landed, with the day it belongs to. */
data class PetEvent(
    val kind: EventKind,
    /** The day index it fired on, so the same day never fires twice. */
    val day: Long,
    val seenAt: Long = 0,
) {
    val acknowledged: Boolean get() = seenAt > 0
}

/**
 * The day's event, or none.
 *
 * Derived from the day number rather than drawn from a random source, so the
 * same day always produces the same event no matter how many times anything
 * asks. That is not a testing convenience bolted on afterwards — four separate
 * drivers advance this world, any of them can be the first to look at a given
 * day, and an event that depended on *who asked first* would fire twice, or
 * differ between the widget and the app.
 */
object Events {

    /** Roughly how often a day carries an event at all. */
    private const val CHANCE_IN = 3

    fun dayOf(millis: Long): Long = Math.floorDiv(millis, MILLIS_PER_DAY)

    /**
     * What happens on [day], if anything.
     *
     * A cheap integer hash, not [kotlin.random.Random]: the point is a stable
     * scramble of the day number, and a hash gives that without carrying a
     * seeded generator through the simulation.
     */
    fun forDay(day: Long): EventKind? {
        val h = scramble(day)
        if (h % CHANCE_IN != 0L) return null
        val kinds = EventKind.entries
        return kinds[(scramble(day + 977) % kinds.size).toInt()]
    }

    private fun scramble(value: Long): Long {
        var x = value * -7046029254386353131L
        x = x xor (x ushr 32)
        x *= -4658895280553007687L
        x = x xor (x ushr 29)
        return abs(x)
    }

    const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000
}

/**
 * What an event does while it is live.
 *
 * Kept beside the enum rather than inside the simulation so that adding an
 * event is one entry here and one string in the UI.
 */
fun EventKind.effect(): UpgradeEffect = when (this) {
    EventKind.LUCKY_DAY -> UpgradeEffect(pay = 1.3f, study = 1.3f)
    EventKind.COLD -> UpgradeEffect(energyDecay = 1.6f, pay = 0.75f)
    EventKind.LETTER -> UpgradeEffect.NONE
    EventKind.INSPIRED -> UpgradeEffect(study = 1.6f)
    EventKind.RESTLESS -> UpgradeEffect(energyDecay = 1.25f)
}

/** Money paid the moment the event lands. */
fun EventKind.instantMoney(): Int = if (this == EventKind.LETTER) 350 else 0

/** Energy taken the moment the event lands. */
fun EventKind.instantEnergy(): Float = if (this == EventKind.RESTLESS) -25f else 0f
