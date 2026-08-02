package com.vpet.waifu.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vpet.waifu.domain.PetState
import com.vpet.waifu.ui.character.AnimatedPet
import com.vpet.waifu.ui.character.PetPalette
import com.vpet.waifu.ui.character.Prop
import com.vpet.waifu.ui.character.RoomDetail
import com.vpet.waifu.ui.character.RoomTheme
import com.vpet.waifu.ui.character.drawPetRoom
import com.vpet.waifu.ui.theme.StageColors

/**
 * The pet in her room.
 *
 * The backdrop is drawn rather than imported (see `drawPetRoom`), which is what
 * lets the home-screen widget show the identical scene from the same code.
 */
@Composable
fun PetStage(
    state: PetState,
    modifier: Modifier = Modifier,
    height: Dp = 300.dp,
    palette: PetPalette = PetPalette.Default,
    /** The tap bounce, anchored at her feet so she dips rather than shrinks. */
    characterScale: Float = 1f,
    workProp: Prop? = null,
    /** Owned upgrade ids — the furniture her money became. */
    decor: Set<String> = emptySet(),
    /**
     * Whether the room is in its night dress.
     *
     * Follows the player's real evening as well as her sleep, so the window
     * shows stars at the player's midnight instead of a noon sky.
     */
    night: Boolean = state == PetState.SLEEPING,
    /** The room she is living in — bought in the shop, applied here. */
    theme: String = RoomTheme.DEFAULT_ID,
) {
    val room = StageColors.forTheme(theme)
    val top by animateColorAsState(
        if (night) room.nightTop else room.dayTop,
        label = "stage-top",
    )
    val bottom by animateColorAsState(
        if (night) room.nightBottom else room.dayBottom,
        label = "stage-bottom",
    )
    val floor by animateColorAsState(
        if (night) room.floorDark else room.floorLight,
        label = "stage-floor",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(28.dp)),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawPetRoom(
                top = top,
                bottom = bottom,
                floor = floor,
                night = night,
                // Her bedroom's window, shelf and cat behind the café counter
                // or the stage truss is two rooms at once; while she is out,
                // only the wall dressing stays.
                detail = if (workProp != null) RoomDetail.WALL else RoomDetail.FULL,
                decor = decor,
                theme = theme,
            )
        }

        AnimatedPet(
            state = state,
            palette = palette,
            workProp = workProp,
            modifier = Modifier
                .fillMaxSize()
                // The top band is reserved for the speech bubble; pushing her
                // start line down keeps the bubble in the sky and off her face.
                .padding(bottom = 8.dp, top = 48.dp)
                .graphicsLayer {
                    scaleX = characterScale
                    scaleY = characterScale
                    transformOrigin = TransformOrigin(0.5f, 0.95f)
                },
        )
    }
}
