package com.vpet.waifu.ui.character

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import kotlin.math.sin

/**
 * The room she stands in: a two-stop sky, a floor, and a window with a sun or a
 * moon behind it.
 *
 * It lives here rather than inside the Compose screen so the widget's rasteriser
 * can draw the identical backdrop — the widget is a snapshot of the same scene,
 * not a second design that has to be kept in step by hand.
 */
fun DrawScope.drawPetRoom(
    top: Color,
    bottom: Color,
    floor: Color,
    night: Boolean,
    cornerRadiusPx: Float = 0f,
    /**
     * Where the wall meets the floor, as a fraction of the height.
     *
     * The widget stacks the character over the room rather than inside it, so
     * the junction has to be pulled up to just behind her feet — left at the
     * default she would appear to float halfway up the wall.
     */
    floorFraction: Float = 0.78f,
) {
    val body: DrawScope.() -> Unit = {
        drawRect(Brush.verticalGradient(listOf(top, bottom)))

        val floorTop = size.height * floorFraction.coerceIn(0.2f, 0.95f)
        drawRect(
            color = floor,
            topLeft = Offset(0f, floorTop),
            size = Size(size.width, size.height - floorTop),
        )

        val wx = size.width * 0.11f
        val wy = floorTop * 0.14f
        val ww = size.width * 0.26f
        val wh = floorTop * 0.62f
        val frame = if (night) Color(0xFF3A2F5E) else Color(0xFFFFFFFF)

        drawRoundRect(
            color = if (night) Color(0xFF15112A) else Color(0xFFBFE4FA),
            topLeft = Offset(wx, wy),
            size = Size(ww, wh),
            cornerRadius = CornerRadius(ww * 0.08f, ww * 0.08f),
        )
        drawCircle(
            color = if (night) Color(0xFFF2E9C4) else Color(0xFFFFE08A),
            radius = ww * 0.18f,
            center = Offset(wx + ww * 0.66f, wy + wh * 0.3f),
        )
        if (night) {
            repeat(6) { i ->
                val sx = wx + ww * (0.15f + 0.14f * i)
                val sy = wy + wh * (0.2f + 0.55f * ((sin(i * 2.1f) + 1f) / 2f))
                drawCircle(Color.White.copy(alpha = 0.7f), radius = ww * 0.02f, center = Offset(sx, sy))
            }
        }
        drawRoundRect(
            color = frame,
            topLeft = Offset(wx, wy),
            size = Size(ww, wh),
            cornerRadius = CornerRadius(ww * 0.08f, ww * 0.08f),
            style = Stroke(width = ww * 0.05f),
        )
        drawLine(
            color = frame,
            start = Offset(wx + ww / 2, wy),
            end = Offset(wx + ww / 2, wy + wh),
            strokeWidth = ww * 0.04f,
        )
    }

    if (cornerRadiusPx <= 0f) {
        body()
        return
    }
    // The widget's bitmap has to carry its own rounded corners: an ImageView
    // inside a RemoteViews tree cannot be clipped by its parent.
    val clip = Path().apply {
        addRoundRect(
            RoundRect(
                Rect(0f, 0f, size.width, size.height),
                CornerRadius(cornerRadiusPx, cornerRadiusPx),
            ),
        )
    }
    clipPath(clip) { body() }
}
