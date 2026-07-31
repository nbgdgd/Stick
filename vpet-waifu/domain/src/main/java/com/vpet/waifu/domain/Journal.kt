package com.vpet.waifu.domain

/** Something worth telling the player about later. */
enum class JournalKind {
    /** A shift finished; amount is what it paid. */
    SHIFT_DONE,

    /** A lesson finished; amount is the EXP it taught. */
    LESSON_DONE,

    /** She fell ill. */
    FELL_SICK,

    /** She shook the illness off by herself. */
    RECOVERED,

    /** A wish nobody granted ran out. */
    WISH_EXPIRED,

    /** The day's event landed; detail is the [EventKind] name. */
    EVENT,

    /** The week's goal was met; amount is the reward. */
    GOAL_DONE,

    /** A day-count anniversary; amount is the number of days. */
    ANNIVERSARY,
}

/**
 * One line of her diary.
 *
 * [detail] is a plain id — an occupation, an item, an event kind — for the UI
 * to name; the domain stays free of words as always.
 */
data class JournalEntry(
    val kind: JournalKind,
    val detail: String? = null,
    val amount: Int = 0,
    val at: Long,
)

/**
 * The diary itself: what happened while nobody was looking.
 *
 * Coming back after eight hours used to mean archaeology — the shift result
 * card, a mood number lower than you left it, and no way to learn *why*. The
 * simulation now writes down every notable thing as it lands, and the app
 * shows the entries since the player last looked. Capped: it is a recap, not
 * a chronicle.
 */
object Journal {
    const val MAX_ENTRIES = 20

    fun append(journal: List<JournalEntry>, entry: JournalEntry): List<JournalEntry> =
        (journal + entry).takeLast(MAX_ENTRIES)
}
