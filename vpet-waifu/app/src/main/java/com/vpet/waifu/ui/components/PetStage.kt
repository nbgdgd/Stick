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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.vpet.waifu.domain.PetState
import com.vpet.waifu.ui.character.AnimatedPet
import com.vpet.waifu.ui.theme.StageColors
import kotlin.math.sin

/**
 * The pet in her room.
 *
 * The backdrop is drawn rather than imported: a two-stop sky, a floor, a window
 * with a sun or a moon, and a scatter of stars at night. It costs nothing, it
 * scales, and it turns a floating character into a scene.
 */
@Composable
fun PetStage(
    state: PetState,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 300.dp,
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
            drawRect(Brush.verticalGradient(listOf(top, bottom)))

            // Floor.
            val floorTop = size.height * 0.78f
            drawRect(
                color = floor,
                topLeft = Offset(0f, floorTop),
                size = Size(size.width, size.height - floorTop),
            )

            // Window with the sun or the moon behind it.
            val wx = size.width * 0.13f
            val wy = size.height * 0.14f
            val ww = size.width * 0.26f
            val wh = size.height * 0.3f
            drawRoundRect(
                color = if (night) Color(0xFF15112A) else Color(0xFFBFE4FA),
                topLeft = Offset(wx, wy),
                size = Size(ww, wh),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f, 10f),
            )
            drawCircle(
                color = if (night) Color(0xFFF2E9C4) else Color(0xFFFFE08A),
                radius = ww * 0.18f,
                center = Offset(wx + ww * 0.66f, wy + wh * 0.3f),
            )
            if (night) {
                repeat(6) { i ->
                    val sx = wx + ww * (0.15f + 0.14f * i)
                    val sy = wy + wh * (0.2f + 0.55f * sin(i * 2.1f).let { (it + 1f) / 2f })
                    drawCircle(Color(0xFFFFFFFF).copy(alpha = 0.7f), radius = 1.6f, center = Offset(sx, sy))
                }
            }
            // Frame.
            drawRoundRect(
                color = if (night) Color(0xFF3A2F5E) else Color(0xFFFFFFFF),
                topLeft = Offset(wx, wy),
                size = Size(ww, wh),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f, 10f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 6f),
            )
            drawLine(
                color = if (night) Color(0xFF3A2F5E) else Color(0xFFFFFFFF),
                start = Offset(wx + ww / 2, wy),
                end = Offset(wx + ww / 2, wy + wh),
                strokeWidth = 5f,
            )
        }

        AnimatedPet(
            state = state,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 8.dp, top = 14.dp),
        )
    }
}
