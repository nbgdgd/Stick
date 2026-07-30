package com.vpet.waifu.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.vpet.waifu.R
import com.vpet.waifu.domain.PetState
import com.vpet.waifu.ui.theme.StatColors

/**
 * The state machine's only job in Phase 1: pick a sprite.
 *
 * The placeholders are vector drawables so they scale to any density and cost
 * nothing in the repository. The art pipeline (ComfyUI → sprite sheet / WebM
 * with alpha) replaces the `pet_*` drawables without touching this mapping.
 */
@DrawableRes
fun PetState.spriteRes(): Int = when (this) {
    PetState.IDLE -> R.drawable.pet_idle
    PetState.HUNGRY -> R.drawable.pet_hungry
    PetState.SLEEPING -> R.drawable.pet_sleeping
}

@StringRes
fun PetState.labelRes(): Int = when (this) {
    PetState.IDLE -> R.string.state_idle
    PetState.HUNGRY -> R.string.state_hungry
    PetState.SLEEPING -> R.string.state_sleeping
}

/** The ring around the bubble, so her state reads at a glance while collapsed. */
fun PetState.accentColor(): Color = when (this) {
    PetState.IDLE -> StatColors.Mood
    PetState.HUNGRY -> StatColors.Hunger
    PetState.SLEEPING -> StatColors.Energy
}
