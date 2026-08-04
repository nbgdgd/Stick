package com.vpet.waifu.ui.character

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.vpet.waifu.domain.PetState

/**
 * Which character is on screen.
 *
 * Three things that are not variants of one another: two complete vector rigs
 * with their own proportions, palettes and prop placements, and any number of
 * sprite packs. Everything downstream — the stage, the bubble, the arcade, the
 * widget — asks this rather than juggling a nullable pack and a boolean.
 */
sealed interface PetSkin {

    /** The character as she is drawn now: inked, three and a third heads. */
    data object Modern : PetSkin

    /** The character as she was before the redraw: rounder, violet twin tails. */
    data object Classic : PetSkin

    /** A sheet of finished pictures. */
    data class Sheet(val pack: SpritePack) : PetSkin

    companion object {
        /** The id stored in settings; blank is the current drawn character. */
        const val MODERN_ID = ""
        const val CLASSIC_ID = "classic"

        /**
         * Resolves a stored id.
         *
         * An id naming a pack that is no longer installed falls back to the
         * drawn character rather than to an empty stage — a save should survive
         * a pack being removed from the app.
         */
        fun of(id: String?, pack: SpritePack?): PetSkin = when {
            pack != null -> Sheet(pack)
            id == CLASSIC_ID -> Classic
            else -> Modern
        }
    }
}

/**
 * How much of the box the character actually fills, top to bottom.
 *
 * The three characters are drawn in three different spaces: the current rig
 * leaves air above her head and below her feet inside a 200x300 field, the
 * classic one the same inside 200x280, and a sprite cell is usually cropped
 * tight. Anything that has to sit *above her head* — which in practice means
 * the speech bubble — needs this to know where the top of her actually is,
 * instead of guessing a fixed inset and being wrong for two of the three.
 */
private fun PetSkin.artField(): Pair<Float, Float> = when (this) {
    PetSkin.Modern -> 200f to 300f
    PetSkin.Classic -> 200f to 280f
    is PetSkin.Sheet -> pack.frameWidth.toFloat() to pack.frameHeight.toFloat()
}

/** Where her tallest point sits inside her own field, as a fraction of it. */
private fun PetSkin.headroomFraction(): Float = when (this) {
    // The ahoge tips at roughly y=10 of 300.
    PetSkin.Modern -> 0.033f
    // The twin tails start higher, in a field that is shorter to begin with.
    PetSkin.Classic -> 0.05f
    // A cell cropped to the character has no air in it at all.
    is PetSkin.Sheet -> 0f
}

/**
 * How far below the top of her box the top of her head actually is, in pixels.
 *
 * The speech bubble used to be pinned to the top of the stage with a fixed
 * inset, which was tuned against one character and wrong for the others: the
 * drawn rig's head reaches within a few points of it, while a sprite is
 * anchored to the floor and scaled down, leaving its head a hundred and forty
 * points lower. The bubble sat neatly over one character and floated in empty
 * sky above the next — text at the top for one, apparently at the bottom for
 * another.
 *
 * [boxWidth] and [boxHeight] are the box the figure is drawn into, not the
 * whole stage.
 */
fun PetSkin.headTopPx(boxWidth: Float, boxHeight: Float): Float {
    val (fieldW, fieldH) = artField()
    return when (this) {
        // A sheet is fitted by its own margin and anchored to the floor, so its
        // top edge is simply the box floor less the drawn height.
        is PetSkin.Sheet -> {
            val drawn = fieldH * pack.pixelScale(boxWidth, boxHeight)
            (boxHeight - drawn).coerceAtLeast(0f)
        }
        // A rig letterboxes its whole field into the box and centres it, then
        // leaves its own air above her head inside that field.
        else -> {
            val scale = minOf(boxWidth / fieldW, boxHeight / fieldH)
            val drawn = fieldH * scale
            (boxHeight - drawn) / 2f + drawn * headroomFraction()
        }
    }
}

/** The character, animating, whichever one is selected. */
@Composable
fun PetFigure(
    skin: PetSkin,
    state: PetState,
    modifier: Modifier = Modifier,
    // Defaulted per skin rather than to one palette: the classic rig in the
    // current character's silver is a different character wearing her body,
    // and a caller that forgets the argument should not be able to cause that.
    palette: PetPalette = if (skin == PetSkin.Classic) PetPalette.Classic else PetPalette.Default,
    workProp: Prop? = null,
) {
    when (skin) {
        is PetSkin.Sheet -> SpritePet(pack = skin.pack, state = state, modifier = modifier)
        PetSkin.Classic -> {
            val seconds = rememberPetPhaseSeconds()
            Canvas(modifier) {
                drawClassicPet(PetPoseFactory.pose(state, seconds.floatValue, workProp), palette)
            }
        }
        PetSkin.Modern -> AnimatedPet(
            state = state,
            palette = palette,
            workProp = workProp,
            modifier = modifier,
        )
    }
}
