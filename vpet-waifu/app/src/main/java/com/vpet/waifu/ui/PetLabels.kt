package com.vpet.waifu.ui

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.vpet.waifu.R
import com.vpet.waifu.domain.OutcomeQuality
import com.vpet.waifu.domain.PetState
import com.vpet.waifu.ui.theme.StatColors

/**
 * The bridge between domain ids and user-facing text.
 *
 * The domain deliberately knows nothing about Android, so `"cafe"` becomes
 * "Кафе" here and nowhere else. An emoji per entry gives the shop and the job
 * list some colour without needing an icon set.
 */
@StringRes
fun occupationNameRes(id: String): Int = when (id) {
    "cafe" -> R.string.job_cafe
    "shop" -> R.string.job_shop
    "office" -> R.string.job_office
    "idol" -> R.string.job_idol
    "school" -> R.string.study_school
    "course" -> R.string.study_course
    "university" -> R.string.study_university
    else -> R.string.job_cafe
}

fun occupationEmoji(id: String): String = when (id) {
    "cafe" -> "☕"
    "shop" -> "🛍"
    "office" -> "💼"
    "idol" -> "🎤"
    "school" -> "🎒"
    "course" -> "💻"
    "university" -> "🎓"
    else -> "⭐"
}

@StringRes
fun shopItemNameRes(id: String): Int = when (id) {
    "onigiri" -> R.string.item_onigiri
    "ramen" -> R.string.item_ramen
    "cake" -> R.string.item_cake
    "bento" -> R.string.item_bento
    "parfait" -> R.string.item_parfait
    "flowers" -> R.string.item_flowers
    "teddy" -> R.string.item_teddy
    "headphones" -> R.string.item_headphones
    "ring" -> R.string.item_ring
    "advance" -> R.string.item_advance
    "exp_pill" -> R.string.item_exp_pill
    else -> R.string.item_onigiri
}

fun shopItemEmoji(id: String): String = when (id) {
    "onigiri" -> "🍙"
    "ramen" -> "🍜"
    "cake" -> "🍰"
    "bento" -> "🍱"
    "parfait" -> "🍨"
    "flowers" -> "💐"
    "teddy" -> "🧸"
    "headphones" -> "🎧"
    "ring" -> "💍"
    "advance" -> "💊"
    "exp_pill" -> "💊"
    else -> "🎁"
}

@StringRes
fun stateLabelRes(state: PetState): Int = when (state) {
    PetState.IDLE -> R.string.state_idle
    PetState.HAPPY -> R.string.state_happy
    PetState.HUNGRY -> R.string.state_hungry
    PetState.TIRED -> R.string.state_tired
    PetState.SLEEPING -> R.string.state_sleeping
    PetState.EATING -> R.string.state_eating
    PetState.LOVED -> R.string.state_loved
    PetState.WORKING -> R.string.state_working
    PetState.STUDYING -> R.string.state_studying
    PetState.PLAYING -> R.string.state_playing
    PetState.CELEBRATING -> R.string.state_celebrating
}

/** The ring around the bubble, so her state reads at a glance while collapsed. */
fun accentFor(state: PetState): Color = when (state) {
    PetState.HUNGRY, PetState.EATING -> StatColors.Hunger
    PetState.TIRED, PetState.SLEEPING -> StatColors.Energy
    PetState.WORKING -> StatColors.Money
    PetState.STUDYING -> StatColors.Exp
    else -> StatColors.Mood
}

@StringRes
fun qualityLabelRes(quality: OutcomeQuality): Int = when (quality) {
    OutcomeQuality.GREAT -> R.string.quality_great
    OutcomeQuality.GOOD -> R.string.quality_good
    OutcomeQuality.POOR -> R.string.quality_poor
    OutcomeQuality.BAD -> R.string.quality_bad
}

fun qualityColor(quality: OutcomeQuality): Color = when (quality) {
    OutcomeQuality.GREAT -> Color(0xFF3FB27F)
    OutcomeQuality.GOOD -> StatColors.Mood
    OutcomeQuality.POOR -> StatColors.Hunger
    OutcomeQuality.BAD -> Color(0xFFD4574E)
}

/** "1ч 24мин" / "18мин" — compact enough for a card corner. */
fun formatRemaining(millis: Long): String {
    val totalMinutes = ((millis + 59_999) / 60_000).toInt()
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}ч ${minutes}мин" else "${minutes}мин"
}
