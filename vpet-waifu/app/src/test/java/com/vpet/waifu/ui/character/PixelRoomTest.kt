package com.vpet.waifu.ui.character

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The invariant the pixel room rests on.
 *
 * The whole point of quantising the room is that its pixels come out the same
 * size as the character's. If those two numbers drift apart the result is
 * worse than doing nothing: two different pixel grids in one picture reads as
 * a rendering bug rather than as a style.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class PixelRoomTest {

    private fun pack(frameWidth: Int, frameHeight: Int) = SpritePack(
        id = "test",
        displayName = "Test",
        sheet = androidx.compose.ui.graphics.ImageBitmap(frameWidth, frameHeight),
        frameWidth = frameWidth,
        frameHeight = frameHeight,
        columns = 1,
        clips = emptyMap(),
        jobs = emptyMap(),
        idle = SpriteClip(start = 0, count = 1, fps = 1f),
        pixelateRoom = true,
        // 1.0 keeps the arithmetic in these tests about the grid, not the margin.
        fit = 1f,
    )

    @Test
    fun `room pixels come out the same size as the character's`() {
        // A 411dp-wide phone at 3x, a 300dp stage, the character's box being the
        // stage less the bubble band above and the margin below.
        val stageWidth = 1233f
        val stageHeight = 900f
        val charBoxHeight = 732f
        val pet = pack(192, 208)

        val scale = pet.pixelScale(stageWidth, charBoxHeight)

        val roomWidth = (stageWidth / scale).roundToInt()
        val roomHeight = (stageHeight / scale).roundToInt()
        val roomScaleX = stageWidth / roomWidth
        val roomScaleY = stageHeight / roomHeight

        // Rounding to whole source pixels can never line up exactly; a percent
        // is far below what an eye can pick out of a 3.5x upscale.
        assertTrue("x drifted: $roomScaleX vs $scale", abs(roomScaleX - scale) / scale < 0.01f)
        assertTrue("y drifted: $roomScaleY vs $scale", abs(roomScaleY - scale) / scale < 0.01f)
    }

    @Test
    fun `the scale is taken from whichever axis constrains the frame`() {
        val pet = pack(192, 208)
        // Tall, narrow box: width is the binding constraint.
        assertEquals(2f, pet.pixelScale(384f, 2080f), 0.001f)
        // Wide, short box: height binds.
        assertEquals(3f, pet.pixelScale(1920f, 624f), 0.001f)
        assertEquals(
            min(1233f / 192f, 732f / 208f),
            pet.pixelScale(1233f, 732f),
            0.001f,
        )
    }

    @Test
    fun `a room bitmap is rendered at exactly the size asked for`() {
        val image = renderRoomBitmap(
            widthPx = 350,
            heightPx = 256,
            density = Density(1f, 1f),
            layoutDirection = LayoutDirection.Ltr,
            paint = RoomPaint(
                top = Color(0xFFEDE4FA),
                bottom = Color(0xFFD9CCF2),
                floor = Color(0xFFCFC2E8),
                night = false,
            ),
            detail = RoomDetail.FULL,
            decor = setOf("fridge", "bed"),
            theme = RoomTheme.DEFAULT_ID,
        )

        assertEquals(350, image.width)
        assertEquals(256, image.height)
    }

    @Test
    fun `banding the wall cuts its shades down without moving them`() {
        fun render(bands: Int) = renderRoomBitmap(
            widthPx = 200,
            heightPx = 150,
            density = Density(1f, 1f),
            layoutDirection = LayoutDirection.Ltr,
            paint = RoomPaint(
                top = Color(0xFFEDE4FA),
                bottom = Color(0xFFD9CCF2),
                floor = Color(0xFFCFC2E8),
                night = false,
            ),
            // No furniture: the wall is what is under test, and a shelf would
            // contribute colours of its own to the count.
            detail = RoomDetail.NONE,
            decor = emptySet(),
            theme = RoomTheme.DEFAULT_ID,
            wallBands = bands,
        )

        fun shadesOf(image: androidx.compose.ui.graphics.ImageBitmap): Set<Int> {
            val pixels = IntArray(image.width * image.height)
            image.readPixels(pixels)
            // The top half is wall in every configuration.
            return pixels.take(image.width * image.height / 2).toHashSet()
        }

        val gradient = shadesOf(render(0))
        val banded = shadesOf(render(6))

        assertTrue(
            "banding did not reduce the wall's shades: ${banded.size} vs ${gradient.size}",
            banded.size < gradient.size,
        )
        // The endpoints are the theme's own colours, and a player paid for
        // them: stepping the shading must not shift a single one.
        assertTrue(
            "banding invented shades outside the gradient it replaced",
            banded.all { it in gradient || it == 0xFFEDE4FA.toInt() || it == 0xFFD9CCF2.toInt() },
        )
    }

    @Test
    fun `the room is drawn, not left blank`() {
        // A resolution-independent scene rendered into a tiny bitmap is exactly
        // the kind of thing that silently comes out empty — if drawPetRoom ever
        // starts measuring in dp, this is what catches it.
        val image = renderRoomBitmap(
            widthPx = 120,
            heightPx = 90,
            density = Density(1f, 1f),
            layoutDirection = LayoutDirection.Ltr,
            paint = RoomPaint(
                top = Color(0xFFEDE4FA),
                bottom = Color(0xFFD9CCF2),
                floor = Color(0xFF6B5CA5),
                night = false,
            ),
            detail = RoomDetail.FULL,
            decor = emptySet(),
            theme = RoomTheme.DEFAULT_ID,
        )

        val pixels = IntArray(image.width * image.height)
        image.readPixels(pixels)
        val distinct = pixels.toHashSet()

        assertTrue("the room rendered as a single flat colour", distinct.size > 4)
        assertTrue("the room rendered fully transparent", pixels.any { it ushr 24 != 0 })
    }
}
