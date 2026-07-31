package com.vpet.waifu.ui

import java.util.Calendar

/**
 * The real clock, in the player's own timezone.
 *
 * The simulation stays deliberately timezone-free — game rules must not change
 * when a phone flies somewhere — but what the *room* looks like is presentation,
 * and a window showing noon at the player's midnight breaks the fiction harder
 * than any missing feature.
 */
fun isRealNight(nowMillis: Long): Boolean {
    val hour = Calendar.getInstance().apply { timeInMillis = nowMillis }.get(Calendar.HOUR_OF_DAY)
    return hour >= 22 || hour < 6
}

/** The morning window, for her "good morning" greeting. */
fun isRealMorning(nowMillis: Long): Boolean {
    val hour = Calendar.getInstance().apply { timeInMillis = nowMillis }.get(Calendar.HOUR_OF_DAY)
    return hour in 5..10
}
