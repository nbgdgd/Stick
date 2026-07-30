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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vpet.waifu.domain.PetState
import com.vpet.waifu.ui.character.AnimatedPet
import com.vpet.waifu.ui.character.PetPalette
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
) {
    val night = state == PetState.SLEEPING
    val top by animateColorAsState(
        if (night) StageColors.NightTop else StageColors.DayTop,
        label = "stage-top",
    )
    val bottom by animateColorAsState(
        if (night) StageColors.NightBottom else StageColors.DayBottom,
        label = "stage-bottom",
    )
    val floor by animateColorAsState(
        if (night) StageColors.FloorDark else StageColors.FloorLight,
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
            drawPetRoom(top = top, bottom = bottom, floor = floor, night = night)
        }

        AnimatedPet(
            state = state,
            palette = palette,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 8.dp, top = 14.dp),
        )
    }
}
