package com.vpet.waifu.domain

/**
 * Attachment — the number that only goes up.
 *
 * Every other gauge in the game drains: mood drifts back, hunger empties,
 * money is spent. Nothing said "you two have been through something together",
 * which left the relationship the whole app is about with no memory. Bond is
 * that memory: points arrive for genuine care — feeding, considered pats,
 * gifts, finished shifts, granted requests — and they never leave.
 *
 * The daily cap is what makes it attachment rather than a grind bar. Time
 * spent together over many days is the only way up; one very long evening is
 * worth the same as a good half hour.
 */
object Bond {

    const val MAX_LEVEL = 10

    /** The most points one day can contribute. */
    const val DAILY_CAP = 20

    // What each act of care is worth.
    const val FEED = 2
    const val PAT = 2
    const val GIFT = 3
    const val SHIFT = 3
    const val LESSON = 3
    const val GAME = 2
    const val REQUEST_GRANTED = 5
    const val NURSED = 6

    /** Total points needed to *reach* [level]. Level 0 is where everyone starts. */
    fun pointsForLevel(level: Int): Int {
        val l = level.coerceIn(0, MAX_LEVEL)
        return 5 * l * (l + 1)
    }

    fun levelFor(points: Int): Int {
        var level = 0
        while (level < MAX_LEVEL && points >= pointsForLevel(level + 1)) level++
        return level
    }

    /** Progress inside the current level, as (earned, needed). */
    fun levelProgress(points: Int): Pair<Int, Int> {
        val level = levelFor(points)
        if (level >= MAX_LEVEL) return 1 to 1
        val floor = pointsForLevel(level)
        return (points - floor) to (pointsForLevel(level + 1) - floor)
    }

    /**
     * How much being ignored hurts, given how far you have come.
     *
     * The one mechanical tooth bond has, and it is deliberately gentle: a pet
     * at full attachment loses mood to neglect at 60% of the stranger rate.
     * She trusts you to come back — that is what the number means.
     */
    fun neglectSoftening(points: Int): Float =
        (1f - 0.04f * levelFor(points)).coerceAtLeast(0.6f)
}
