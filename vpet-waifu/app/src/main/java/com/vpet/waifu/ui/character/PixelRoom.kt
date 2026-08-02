package com.vpet.waifu.ui.character

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.roundToInt

/**
 * The room, quantised to a sprite character's own pixel grid.
 *
 * A sprite pack is a small drawing blown up — the Anya sheet is 192 points
 * across and lands on a phone stage some three and a half times that size, so
 * every pixel she is made of is a visible square. Behind her the room is vector
 * art rendered at the panel's full resolution, with smooth gradients and
 * anti-aliased diagonals. Those two things cannot share a picture: she reads as
 * a sticker pasted onto a photograph, which is exactly what a player notices
 * first and cannot un-notice.
 *
 * The fix is not to redraw the room. It is to render the same room into a
 * bitmap small enough that one of its pixels is one of *her* pixels, and then
 * blow that up with nearest-neighbour sampling. The output is the identical
 * scene — same window, same shelf, same cat — with the same chunky edges she
 * has, because it went through the same size of grid.
 *
 * The small render is cached on everything it depends on, so it happens when
 * the theme, the hour or the furniture changes and not on any frame in between.
 */
private const val MIN_PIXEL_SCALE = 2f

/** Below this a room bitmap has no room left in it. */
private const val MIN_ROOM_PX = 8

/**
 * How many flat steps the wall is cut into while pixelating.
 *
 * Six is enough to read as deliberate shading and few enough to read as a
 * palette; a dozen is indistinguishable from the gradient it replaced.
 */
internal const val WALL_BANDS = 6

/**
 * The colour the room's contour is drawn towards.
 *
 * Not pure black and not quite the character's own ink: a room needs its line
 * to sit behind her, and matching hers exactly brings the furniture forward
 * enough to compete with the character standing in front of it.
 */
internal const val ROOM_INK = 0xFF3A3150.toInt()

/** The same, for a room that is already dark. */
internal const val ROOM_INK_NIGHT = 0xFF0C0918.toInt()

/**
 * The room's colours at a given moment.
 *
 * Bundled rather than passed loose because they are the cache key: a bitmap has
 * to be rebuilt when any of them moves, and four separate parameters is four
 * chances to forget one.
 */
data class RoomPaint(
    val top: Color,
    val bottom: Color,
    val floor: Color,
    val night: Boolean,
)

/**
 * Renders [drawPetRoom] once into a small [ImageBitmap] and hands it back.
 *
 * [pixelScale] is how many screen pixels one source pixel should become — take
 * it from [SpritePack.pixelScale] so the room and the character agree. The
 * returned bitmap is `ceil(size / pixelScale)` across, which is a few hundred
 * pixels at most and costs well under a megabyte.
 */
@Composable
fun rememberPixelRoom(
    widthPx: Float,
    heightPx: Float,
    pixelScale: Float,
    paint: RoomPaint,
    detail: RoomDetail,
    decor: Set<String>,
    theme: String,
): ImageBitmap? {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current

    // Decided before any remember, and folded into the key rather than causing
    // an early return: bailing out above a remember would give this function a
    // different number of slots depending on its arguments, which is the one
    // thing a composable may not do.
    //
    // Null means "do not pixelate": either the character's own pixels are too
    // small to read as pixels, so there is no grid to match, or the resulting
    // bitmap would be too small to hold a room. Clamping the scale up instead
    // would be worse than skipping — it would make the room chunkier than the
    // character it is supposed to agree with.
    val spec = if (pixelScale < MIN_PIXEL_SCALE) {
        null
    } else {
        val w = (widthPx / pixelScale).roundToInt()
        val h = (heightPx / pixelScale).roundToInt()
        if (w < MIN_ROOM_PX || h < MIN_ROOM_PX) null else IntSize(w, h)
    }

    // Sorted into a string so the key compares by content. A Set would compare
    // by content too, but the string also pins the order, which keeps the key
    // stable when the same ids arrive from a different iteration order.
    val decorKey = decor.sorted().joinToString(",")

    return remember(spec, paint, detail, decorKey, theme) {
        spec?.let {
            renderRoomBitmap(
                widthPx = it.width,
                heightPx = it.height,
                // The room is drawn in raw pixels here, not in dp: the whole
                // point is that the source grid is small. Handing it the
                // screen's density would scale the furniture up and crop it.
                // Nothing in drawPetRoom reads density — every dimension in it
                // is a fraction of the canvas — so this is free of consequence
                // beyond being honest about the units.
                density = Density(density = 1f, fontScale = 1f),
                layoutDirection = layoutDirection,
                paint = paint,
                detail = detail,
                decor = decor,
                theme = theme,
                wallBands = WALL_BANDS,
                ink = true,
            )
        }
    }
}

/** The offscreen pass itself, outside composition so it can be tested. */
fun renderRoomBitmap(
    widthPx: Int,
    heightPx: Int,
    density: Density,
    layoutDirection: LayoutDirection,
    paint: RoomPaint,
    detail: RoomDetail,
    decor: Set<String>,
    theme: String,
    wallBands: Int = 0,
    ink: Boolean = false,
): ImageBitmap {
    val image = ImageBitmap(widthPx, heightPx)
    CanvasDrawScope().draw(
        density = density,
        layoutDirection = layoutDirection,
        canvas = Canvas(image),
        size = Size(widthPx.toFloat(), heightPx.toFloat()),
    ) {
        drawPetRoom(
            top = paint.top,
            bottom = paint.bottom,
            floor = paint.floor,
            night = paint.night,
            detail = detail,
            decor = decor,
            theme = theme,
            wallBands = wallBands,
        )
    }
    if (!ink) return image

    // The contour, over the finished pixels. Matching her grid is what makes
    // the room's edges land on her squares; this is what makes them look like
    // hers — she carries a heavy dark line around every form and the room, up
    // to here, carried none.
    val bitmap = image.asAndroidBitmap()
    val pixels = IntArray(widthPx * heightPx)
    bitmap.getPixels(pixels, 0, widthPx, 0, 0, widthPx, heightPx)
    inkRoomEdges(
        pixels = pixels,
        width = widthPx,
        height = heightPx,
        // Darker after dark: a contour a shade off the daylight wall is
        // invisible against a night one, and the night room would keep its
        // smooth edges while every other theme got a line.
        ink = if (paint.night) ROOM_INK_NIGHT else ROOM_INK,
    )
    bitmap.setPixels(pixels, 0, widthPx, 0, 0, widthPx, heightPx)
    return image
}

/**
 * Blits a pixel room over the whole canvas.
 *
 * [FilterQuality.None] is the entire trick: it maps to `isFilterBitmap = false`
 * on the underlying paint, so an upscale repeats source pixels instead of
 * interpolating between them. With the default quality this draws a blurry
 * low-resolution room, which is worse than not doing it at all.
 */
fun DrawScope.drawPixelRoom(room: ImageBitmap) {
    drawImage(
        image = room,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(room.width, room.height),
        dstOffset = IntOffset.Zero,
        dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
        filterQuality = FilterQuality.None,
    )
}
