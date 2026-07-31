package com.vpet.waifu.domain

import kotlin.math.abs

/** What she is asking for. */
enum class RequestKind {
    /** A specific dish from the shop. */
    FOOD,

    /** A specific present. */
    GIFT,

    /** A round of something in the arcade. */
    PLAY,
}

/**
 * A live request: what she wants, and until when the wish stands.
 *
 * [slot] is the three-hour window it belongs to, kept so the same window never
 * asks twice — granted, declined or ignored, a wish spoken is a wish spent.
 */
data class PetRequest(
    val kind: RequestKind,
    val itemId: String?,
    val until: Long,
    val slot: Long,
)

/**
 * Her own wishes.
 *
 * Until now every single interaction ran from the player to her: she never
 * wanted anything, which is the difference between a companion and a vending
 * machine. Every few hours she may ask for something concrete — a dish, a
 * present, a game — and granting it while the wish is fresh is worth more than
 * any unprompted gift.
 *
 * Deterministic, like [Events]: whether a slot carries a wish, and what the
 * wish is, are pure functions of the slot number, so the four drivers of this
 * world can never disagree about what she wants.
 */
object Requests {

    /** She asks at most once per this window. */
    const val SLOT_MILLIS = 3L * 60 * 60 * 1000

    /** A wish needs at least this long left in its slot to be worth voicing. */
    const val MINIMUM_WINDOW_MILLIS = 30L * 60 * 1000

    /** Roughly two slots in five carry a wish. */
    private const val CHANCE_IN = 5
    private const val CHANCE_HITS = 2

    fun slotOf(millis: Long): Long = Math.floorDiv(millis, SLOT_MILLIS)

    fun slotEnd(slot: Long): Long = (slot + 1) * SLOT_MILLIS

    /**
     * The wish belonging to [slot], if it carries one she can voice at [level].
     *
     * The item is always something already unlocked: a wish for a thing the
     * player cannot buy yet is not charming, it is a wall.
     */
    fun forSlot(slot: Long, level: Int): Pair<RequestKind, String?>? {
        if (scramble(slot) % CHANCE_IN >= CHANCE_HITS) return null

        val kinds = buildList {
            add(RequestKind.FOOD)
            add(RequestKind.PLAY)
            if (Shop.GIFTS.any { it.isUnlocked(level) }) add(RequestKind.GIFT)
        }
        return when (val kind = kinds[(scramble(slot + 31) % kinds.size).toInt()]) {
            RequestKind.FOOD -> {
                val open = Shop.FOOD.filter { it.isUnlocked(level) }
                kind to open[(scramble(slot + 97) % open.size).toInt()].id
            }
            RequestKind.GIFT -> {
                val open = Shop.GIFTS.filter { it.isUnlocked(level) }
                kind to open[(scramble(slot + 97) % open.size).toInt()].id
            }
            RequestKind.PLAY -> kind to null
        }
    }

    private fun scramble(value: Long): Long {
        var x = value * -7046029254386353131L
        x = x xor (x ushr 32)
        x *= -4658895280553007687L
        x = x xor (x ushr 29)
        return abs(x)
    }
}
