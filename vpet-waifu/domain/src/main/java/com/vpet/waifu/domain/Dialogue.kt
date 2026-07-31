package com.vpet.waifu.domain

import kotlin.math.abs

/**
 * A thing she has to say.
 *
 * The domain names the *situation*; the UI owns the words, because the words
 * are string resources and this module knows nothing about Android. Each topic
 * has several phrasings so she does not repeat herself, and [variant] picks
 * one.
 */
enum class DialogueTopic {
    /** No particular situation — she is just there. */
    IDLE,
    HUNGRY,
    STARVING,
    TIRED,
    SLEEPING,
    WORKING,
    STUDYING,
    PLAYING,
    JUST_FED,
    /** Fed the same thing several times running. */
    SAME_MEAL_AGAIN,
    PETTED,
    /** You came back after being away for a while. */
    WELCOME_BACK,
    /** You came back after being away for a long while. */
    MISSED_YOU,
    /** She has just been paid, mid-shift. */
    PAYDAY,
    LEVEL_UP,
    /** An event landed today. */
    EVENT,
    /** She is ill and saying so. */
    SICK,
    /** She is asking for something — the request card carries the specifics. */
    REQUEST,
    /** Everything is fine and she is delighted about it. */
    CONTENT,
}

/** One line: which topic, and which of its phrasings. */
data class DialogueLine(val topic: DialogueTopic, val variant: Int)

/**
 * What she would say right now.
 *
 * Ordered by how much the situation deserves to be spoken about: a starving pet
 * does not remark on the weather. The result is a pure function of the snapshot
 * and the clock, so the app, the overlay and a test all get the same line at the
 * same instant rather than three different ones.
 */
object Dialogue {

    /** How many phrasings each topic has, so the UI and this agree. */
    const val VARIANTS = 3

    /** Long enough away that coming back is worth remarking on. */
    private const val AWAY_MINUTES = 90f

    /** Long enough that she is going to say something about it. */
    private const val LONG_AWAY_MINUTES = 12f * 60f

    fun lineFor(
        snapshot: PetSnapshot,
        nowMillis: Long,
        tuning: PetTuning = PetTuning(),
    ): DialogueLine {
        val topic = topicFor(snapshot, nowMillis, tuning)
        // The variant is stable for a given situation and minute, so the line
        // does not flicker between frames while the state has not changed.
        val minute = nowMillis / PetSimulation.MS_PER_MINUTE
        return DialogueLine(topic, variantFor(topic, minute, snapshot.lastInteractionAt))
    }

    private fun topicFor(snapshot: PetSnapshot, nowMillis: Long, tuning: PetTuning): DialogueTopic {
        val awayMinutes = (nowMillis - snapshot.lastInteractionAt).toFloat() / PetSimulation.MS_PER_MINUTE

        // What she is doing wins over how she feels, exactly as the sprite FSM
        // orders it — she is not going to complain about being peckish while
        // she is asleep.
        when (snapshot.activity) {
            PetActivity.SLEEPING -> return DialogueTopic.SLEEPING
            PetActivity.WORKING -> return if (snapshot.session?.paidOut ?: 0 > 0) {
                DialogueTopic.PAYDAY
            } else {
                DialogueTopic.WORKING
            }
            PetActivity.STUDYING -> return DialogueTopic.STUDYING
            PetActivity.PLAYING -> return DialogueTopic.PLAYING
            PetActivity.AWAKE -> Unit
        }

        // A reaction you just caused beats anything ambient.
        if (nowMillis < snapshot.emoteUntil) {
            when (snapshot.emote) {
                Emote.EATING -> return if (snapshot.repeatedMeals >= 3) {
                    DialogueTopic.SAME_MEAL_AGAIN
                } else {
                    DialogueTopic.JUST_FED
                }
                Emote.LOVED -> return DialogueTopic.PETTED
                Emote.CELEBRATING -> return DialogueTopic.LEVEL_UP
                null -> Unit
            }
        }

        if (snapshot.event != null && !snapshot.event.acknowledged) return DialogueTopic.EVENT

        // Being ill outranks everything ambient — it is the one state the
        // player has to actually do something about.
        if (snapshot.isSick) return DialogueTopic.SICK

        // Then the two things that actually need doing something about.
        if (snapshot.stats.hunger <= tuning.hungryThreshold / 2f) return DialogueTopic.STARVING
        if (snapshot.stats.hunger <= tuning.hungryThreshold) return DialogueTopic.HUNGRY
        if (snapshot.stats.energy <= tuning.tiredThreshold) return DialogueTopic.TIRED

        // Her own wish, once nothing is urgent.
        if (snapshot.request != null && nowMillis < snapshot.request.until) return DialogueTopic.REQUEST

        // Then noticing you.
        if (awayMinutes >= LONG_AWAY_MINUTES) return DialogueTopic.MISSED_YOU
        if (awayMinutes >= AWAY_MINUTES) return DialogueTopic.WELCOME_BACK

        if (snapshot.stats.mood >= tuning.happyThreshold) return DialogueTopic.CONTENT
        return DialogueTopic.IDLE
    }

    private fun variantFor(topic: DialogueTopic, minute: Long, anchor: Long): Int {
        var x = minute * 31L + topic.ordinal * 7919L + anchor / 1000L
        x = x xor (x ushr 17)
        x *= -7046029254386353131L
        x = x xor (x ushr 31)
        return (abs(x) % VARIANTS).toInt()
    }
}
