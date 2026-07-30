package com.vpet.waifu.widget

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.vpet.waifu.domain.PetState
import com.vpet.waifu.ui.character.PetPoseFactory
import com.vpet.waifu.ui.character.PetRasterizer
import com.vpet.waifu.ui.character.RoomColors
import com.vpet.waifu.ui.theme.StageColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The widget's animation, checked on the real Android graphics stack.
 *
 * There is no emulator available here, but Robolectric's native graphics mode
 * runs the actual Skia pipeline and the actual PNG encoder — which is what the
 * payload budget depends on, so guessing at it would be worthless.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetFrameTest {

    private val density = 2.75f
    private val frameCount = 10

    private fun frames(state: PetState) = PetRasterizer.animationFrames(
        state = state,
        widthPx = 300,
        heightPx = 420,
        density = density,
        frameCount = frameCount,
        loopSeconds = 2.4f,
    )

    @Test
    fun `every state renders a full set of frames`() {
        PetState.entries.forEach { state ->
            val rendered = frames(state)
            assertEquals("$state", frameCount, rendered.size)
            assertTrue("$state produced an empty frame", rendered.all { it.size > 512 })
        }
    }

    @Test
    fun `the frames actually differ, so the widget animates`() {
        val rendered = frames(PetState.IDLE)
        val distinct = rendered.map { it.contentHashCode() }.distinct()

        assertTrue("only ${distinct.size} distinct frames — the loop would look frozen", distinct.size >= 8)
    }

    @Test
    fun `every state's loop joins back onto itself`() {
        // Frame 0 and the frame after the last are the same instant, which is
        // what stops the flipbook from jolting once per cycle.
        PetState.entries.forEach { state ->
            val first = PetPoseFactory.widgetLoopFrame(state, 0, frameCount, 2.5f)
            val wrapped = PetPoseFactory.widgetLoopFrame(state, frameCount, frameCount, 2.5f)

            assertEquals("$state breath", first.breath, wrapped.breath, 0.05f)
            assertEquals("$state bounce", first.bodyBounce, wrapped.bodyBounce, 0.4f)
            assertEquals("$state hair", first.hairSwayDegrees, wrapped.hairSwayDegrees, 0.8f)
            assertEquals("$state ahoge", first.ahogeDegrees, wrapped.ahogeDegrees, 0.8f)
        }
    }

    @Test
    fun `every state fits the transaction budget, room included`() {
        // The whole update rides one Binder transaction with about a megabyte
        // shared across the system; over budget and the launcher silently drops
        // it, leaving a blank widget. This is the check that caught a
        // twelve-frame loop overflowing on the busiest state.
        val budget = 400 * 1024
        val room = PetRasterizer.roomPng(
            widthPx = 700, heightPx = 550, density = density,
            colors = RoomColors(
                StageColors.DayTop, StageColors.DayBottom, StageColors.FloorLight, night = false,
            ),
        ).size
        PetState.entries.forEach { state ->
            val total = frames(state).sumOf { it.size } + room
            assertTrue(
                "$state needs ${total / 1024} KB, budget is ${budget / 1024} KB",
                total <= budget,
            )
        }
    }

    @Test
    fun `PNG beats raw bitmaps by more than an order of magnitude`() {
        val total = frames(PetState.IDLE).sumOf { it.size }
        val raw = 300 * 420 * 4 * frameCount

        assertTrue("png=$total raw=$raw", total * 10 < raw)
    }

    @Test
    fun `the room renders as its own cheap layer`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val room = PetRasterizer.roomPng(
            widthPx = 700,
            heightPx = 550,
            density = context.resources.displayMetrics.density,
            colors = RoomColors(
                StageColors.DayTop, StageColors.DayBottom, StageColors.FloorLight, night = false,
            ),
            cornerRadiusPx = 44f,
            floorFraction = 0.55f,
        )

        assertTrue("room is empty", room.size > 512)
        assertTrue("room costs ${room.size / 1024} KB on its own", room.size < 80 * 1024)
    }
}
