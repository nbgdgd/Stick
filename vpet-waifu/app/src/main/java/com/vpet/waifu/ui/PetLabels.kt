package com.vpet.waifu.ui

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.vpet.waifu.R
import com.vpet.waifu.domain.DialogueLine
import com.vpet.waifu.domain.DialogueTopic
import com.vpet.waifu.domain.EventKind
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
    "energy_drink" -> R.string.item_energy_drink
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
    "energy_drink" -> "🥤"
    "flowers" -> "💐"
    "teddy" -> "🧸"
    "headphones" -> "🎧"
    "ring" -> "💍"
    "advance" -> "💊"
    "exp_pill" -> "💊"
    else -> "🎁"
}

@StringRes
fun upgradeNameRes(id: String): Int = when (id) {
    "fridge" -> R.string.upgrade_fridge
    "bed" -> R.string.upgrade_bed
    "console" -> R.string.upgrade_console
    "cat" -> R.string.upgrade_cat
    "coffee_machine" -> R.string.upgrade_coffee_machine
    "laptop" -> R.string.upgrade_laptop
    "textbooks" -> R.string.upgrade_textbooks
    "studio" -> R.string.upgrade_studio
    "outfit_uniform" -> R.string.upgrade_outfit_uniform
    "outfit_cocoa" -> R.string.upgrade_outfit_cocoa
    "outfit_mint" -> R.string.upgrade_outfit_mint
    "outfit_sakura" -> R.string.upgrade_outfit_sakura
    "outfit_midnight" -> R.string.upgrade_outfit_midnight
    else -> R.string.upgrade_outfit_gold
}

fun upgradeEmoji(id: String): String = when (id) {
    "fridge" -> "\uD83E\uDDCA"
    "bed" -> "\uD83D\uDECF"
    "console" -> "\uD83C\uDFAE"
    "cat" -> "\uD83D\uDC08"
    "coffee_machine" -> "\u2615"
    "laptop" -> "\uD83D\uDCBB"
    "textbooks" -> "\uD83D\uDCDA"
    "studio" -> "\uD83C\uDFB9"
    "outfit_uniform" -> "\uD83C\uDF93"
    "outfit_cocoa" -> "\uD83E\uDD5B"
    "outfit_mint" -> "\uD83C\uDF3F"
    "outfit_sakura" -> "\uD83C\uDF38"
    "outfit_midnight" -> "\uD83C\uDF19"
    else -> "\u2728"
}

@StringRes
fun eventTitleRes(kind: EventKind): Int = when (kind) {
    EventKind.LUCKY_DAY -> R.string.event_title_lucky_day
    EventKind.COLD -> R.string.event_title_cold
    EventKind.LETTER -> R.string.event_title_letter
    EventKind.INSPIRED -> R.string.event_title_inspired
    EventKind.RESTLESS -> R.string.event_title_restless
}

@StringRes
fun eventBodyRes(kind: EventKind): Int = when (kind) {
    EventKind.LUCKY_DAY -> R.string.event_lucky_day
    EventKind.COLD -> R.string.event_cold
    EventKind.LETTER -> R.string.event_letter
    EventKind.INSPIRED -> R.string.event_inspired
    EventKind.RESTLESS -> R.string.event_restless
}

fun eventEmoji(kind: EventKind): String = when (kind) {
    EventKind.LUCKY_DAY -> "\uD83C\uDF40"
    EventKind.COLD -> "\uD83E\uDD12"
    EventKind.LETTER -> "\u2709\uFE0F"
    EventKind.INSPIRED -> "\uD83D\uDCA1"
    EventKind.RESTLESS -> "\uD83D\uDE2B"
}

/**
 * Her line, as a string resource.
 *
 * The domain picks the situation and the phrasing index; the words live here,
 * because words are resources and the domain has no idea Android exists. The
 * table is exhaustive on purpose — a missing topic should be a compile error,
 * not a silent fallback to "So, what shall we do?".
 */
@StringRes
fun dialogueRes(line: DialogueLine): Int = when (line.topic) {
    DialogueTopic.IDLE -> pick(line.variant, R.string.say_idle_0, R.string.say_idle_1, R.string.say_idle_2)
    DialogueTopic.HUNGRY -> pick(line.variant, R.string.say_hungry_0, R.string.say_hungry_1, R.string.say_hungry_2)
    DialogueTopic.STARVING -> pick(line.variant, R.string.say_starving_0, R.string.say_starving_1, R.string.say_starving_2)
    DialogueTopic.TIRED -> pick(line.variant, R.string.say_tired_0, R.string.say_tired_1, R.string.say_tired_2)
    DialogueTopic.SLEEPING -> pick(line.variant, R.string.say_sleeping_0, R.string.say_sleeping_1, R.string.say_sleeping_2)
    DialogueTopic.WORKING -> pick(line.variant, R.string.say_working_0, R.string.say_working_1, R.string.say_working_2)
    DialogueTopic.STUDYING -> pick(line.variant, R.string.say_studying_0, R.string.say_studying_1, R.string.say_studying_2)
    DialogueTopic.PLAYING -> pick(line.variant, R.string.say_playing_0, R.string.say_playing_1, R.string.say_playing_2)
    DialogueTopic.JUST_FED -> pick(line.variant, R.string.say_just_fed_0, R.string.say_just_fed_1, R.string.say_just_fed_2)
    DialogueTopic.SAME_MEAL_AGAIN -> pick(line.variant, R.string.say_same_meal_again_0, R.string.say_same_meal_again_1, R.string.say_same_meal_again_2)
    DialogueTopic.PETTED -> pick(line.variant, R.string.say_petted_0, R.string.say_petted_1, R.string.say_petted_2)
    DialogueTopic.WELCOME_BACK -> pick(line.variant, R.string.say_welcome_back_0, R.string.say_welcome_back_1, R.string.say_welcome_back_2)
    DialogueTopic.MISSED_YOU -> pick(line.variant, R.string.say_missed_you_0, R.string.say_missed_you_1, R.string.say_missed_you_2)
    DialogueTopic.PAYDAY -> pick(line.variant, R.string.say_payday_0, R.string.say_payday_1, R.string.say_payday_2)
    DialogueTopic.LEVEL_UP -> pick(line.variant, R.string.say_level_up_0, R.string.say_level_up_1, R.string.say_level_up_2)
    DialogueTopic.EVENT -> pick(line.variant, R.string.say_event_0, R.string.say_event_1, R.string.say_event_2)
    DialogueTopic.CONTENT -> pick(line.variant, R.string.say_content_0, R.string.say_content_1, R.string.say_content_2)
}

private fun pick(variant: Int, vararg options: Int): Int = options[variant.mod(options.size)]

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
fun formatRemaining(millis: Long): String =
    formatMinutes(((millis + 59_999) / 60_000).toInt())

/**
 * "2ч" / "1ч 30мин" / "45мин".
 *
 * Effect durations are in minutes and some are under an hour, so they cannot
 * just be divided by 60 — that renders a half-hour crash as "0ч".
 */
fun formatMinutes(totalMinutes: Int): String {
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 && minutes > 0 -> "${hours}ч ${minutes}мин"
        hours > 0 -> "${hours}ч"
        else -> "${minutes}мин"
    }
}
