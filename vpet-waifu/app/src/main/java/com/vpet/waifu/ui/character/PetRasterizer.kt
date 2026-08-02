package com.vpet.waifu.ui.character

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.vpet.waifu.domain.PetState
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt
import kotlin.math.min

/** Colours for the room baked into a rasterised frame. */
data class RoomColors(
    val top: Color,
    val bottom: Color,
    val floor: Color,
    val night: Boolean,
)

/**
 * Turns the live character renderer into pixels.
 *
 * The home-screen widget cannot run Compose, so it gets bitmaps drawn by the
 * exact same code the app and the bubble use — one source of truth for the art
 * instead of a parallel set of static drawables that would drift out of sync.
 */
object PetRasterizer {

    /**
     * The room on its own, PNG-encoded.
     *
     * The backdrop is identical in every frame, so it is drawn once as a
     * separate layer instead of being paid for a dozen times. That also lets it
     * span the whole widget while the character frames stay small and sharp —
     * a character-sized backdrop would leave a visible panel edge.
     */
    fun roomPng(
        widthPx: Int,
        heightPx: Int,
        density: Float,
        colors: RoomColors,
        cornerRadiusPx: Float = 0f,
        floorFraction: Float = 0.78f,
        detail: RoomDetail = RoomDetail.WALL,
        decor: Set<String> = emptySet(),
        theme: String = RoomTheme.DEFAULT_ID,
    ): ByteArray = png(
        draw(widthPx, heightPx, density) {
            drawPetRoom(
                colors.top, colors.bottom, colors.floor, colors.night,
                cornerRadiusPx, floorFraction, detail, decor, theme,
            )
        },
    )

    /**
     * A seamless animation loop, PNG-encoded.
     *
     * PNG rather than raw bitmaps because these travel to the launcher through
     * a Binder transaction with roughly a megabyte to share: flat vector art
     * compresses to a small fraction of its `ARGB_8888` size, which is the
     * difference between a dozen frames fitting and the update being dropped.
     */
    fun animationFrames(
        state: PetState,
        widthPx: Int,
        heightPx: Int,
        density: Float,
        frameCount: Int,
        loopSeconds: Float,
        palette: PetPalette = PetPalette.Default,
        room: RoomColors? = null,
        cornerRadiusPx: Float = 0f,
        workProp: Prop? = null,
    ): List<ByteArray> = (0 until frameCount).map { index ->
        val pose = PetPoseFactory.widgetLoopFrame(state, index, frameCount, loopSeconds, workProp)
        png(
            draw(widthPx, heightPx, density) {
                room?.let { drawPetRoom(it.top, it.bottom, it.floor, it.night, cornerRadiusPx) }
                drawPet(pose, palette)
            },
        )
    }

    /**
     * The same loop, cut out of a sprite sheet instead of drawn.
     *
     * The widget cannot host a composable, so whichever character is selected
     * has to arrive as PNG bytes either way; for a sheet that is a crop and a
     * scale rather than thirty paths per frame, which is a good deal cheaper.
     */
    fun spriteFrames(
        pack: SpritePack,
        state: PetState,
        widthPx: Int,
        heightPx: Int,
        frameCount: Int,
    ): List<ByteArray> {
        val clip = pack.clipFor(state)
        val source = pack.sheet.asAndroidBitmap()
        val scale = min(
            widthPx.toFloat() / pack.frameWidth,
            heightPx.toFloat() / pack.frameHeight,
        )
        val w = (pack.frameWidth * scale).roundToInt().coerceAtLeast(1)
        val h = (pack.frameHeight * scale).roundToInt().coerceAtLeast(1)

        return (0 until frameCount).map { index ->
            // Sample the clip evenly across the widget's own loop length, so a
            // six-frame clip and a three-frame one both fill the same period.
            val cell = clip.row * pack.columns + clip.from +
                (index * clip.count / frameCount).coerceAtMost(clip.count - 1)
            val col = cell % pack.columns
            val row = cell / pack.columns
            val frame = Bitmap.createBitmap(
                source,
                col * pack.frameWidth,
                row * pack.frameHeight,
                pack.frameWidth,
                pack.frameHeight,
            )
            val target = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
            android.graphics.Canvas(target).drawBitmap(
                frame,
                null,
                android.graphics.Rect((widthPx - w) / 2, heightPx - h, (widthPx + w) / 2, heightPx),
                android.graphics.Paint().apply { isFilterBitmap = false },
            )
            frame.recycle()
            png(target)
        }
    }

    private fun png(bitmap: Bitmap): ByteArray =
        ByteArrayOutputStream(INITIAL_PNG_BUFFER).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            bitmap.recycle()
            out.toByteArray()
        }

    private inline fun draw(
        widthPx: Int,
        heightPx: Int,
        density: Float,
        crossinline block: DrawScope.() -> Unit,
    ): Bitmap {
        val width = widthPx.coerceAtLeast(1)
        val height = heightPx.coerceAtLeast(1)
        val target = ImageBitmap(width, height, ImageBitmapConfig.Argb8888)
        CanvasDrawScope().draw(
            density = Density(density),
            layoutDirection = LayoutDirection.Ltr,
            canvas = Canvas(target),
            size = Size(width.toFloat(), height.toFloat()),
        ) {
            block()
        }
        return target.asAndroidBitmap()
    }

    private const val INITIAL_PNG_BUFFER = 32 * 1024
}
