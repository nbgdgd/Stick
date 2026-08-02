package com.vpet.waifu.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import com.vpet.waifu.MainActivity
import com.vpet.waifu.R
import com.vpet.waifu.data.PetRepository
import com.vpet.waifu.domain.PetState
import com.vpet.waifu.ui.character.ART_HEIGHT
import com.vpet.waifu.ui.character.ART_WIDTH
import com.vpet.waifu.ui.character.PetPalette
import com.vpet.waifu.ui.character.PetRasterizer
import com.vpet.waifu.ui.character.Prop
import com.vpet.waifu.ui.character.workPropFor
import com.vpet.waifu.ui.isRealNight
import com.vpet.waifu.ui.character.RoomColors
import com.vpet.waifu.ui.character.RoomDetail
import com.vpet.waifu.ui.theme.StageColors
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The home-screen widget: her, in her room, and nothing else.
 *
 * No bars, no counters, no buttons. A widget of this size read as a dashboard
 * with a mascot wedged in the corner; the stats belong in the app, and what
 * belongs here is the character, big enough to actually see. Tapping anywhere
 * opens the app.
 *
 * Two things a widget normally cannot do are solved here.
 *
 * **Animation.** RemoteViews can neither run Compose nor play a frame
 * animation, but `ViewFlipper` is on the short list of views a widget may
 * contain and cycles its children by itself once the launcher attaches it. So
 * the character is pre-rendered into a seamless loop of PNG frames, added as
 * `ImageView` children, and embedded through Glance's [AndroidRemoteViews]. If a
 * launcher declines to auto-start the flipper the widget simply shows the first
 * frame, which is a perfectly good still.
 *
 * **Resizing.** [SizeMode.Exact] re-runs this for every size the user drags to,
 * and the real size is read back from the widget's options so both the layout
 * and the bitmap resolution follow it.
 */
class PetWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // peek, not tick: the widget shows the world advanced to now, but a
        // redraw must not itself write to the save file.
        val snapshot = repositoryOf(context).peek()
        // The same state WidgetSync keys the redraw on, so the picture and the
        // decision to redraw it can never disagree.
        val now = System.currentTimeMillis()
        val state = widgetState(snapshot, now)
        // The room's night follows the player's actual evening and her sleep,
        // not the system theme: a widget showing noon at midnight breaks the
        // fiction harder than any missing feature. The 15-minute heartbeat
        // carries the flip across the boundary.
        val palette = WidgetPalette.of(
            night = isRealNight(now) || state == PetState.SLEEPING,
            theme = snapshot.theme,
        )
        val size = widgetSize(context, id)
        val density = context.resources.displayMetrics.density

        val padding = paddingFor(size)
        val stageHeightDp = stageHeight(size, padding)
        val stageWidthDp = stageHeightDp * (ART_WIDTH / ART_HEIGHT)
        val tempo = FlipTempo.forState(state)
        val workProp = workPropFor(snapshot.occupation?.id)

        // Both bitmaps depend only on what is in their keys, and neither
        // depends on a stat. Without the cache every redraw re-encoded the
        // entire loop from scratch.
        val roomWidthPx = (size.width.value * density).roundToInt().coerceAtMost(MAX_ROOM_PX)
        val roomHeightPx = (size.height.value * density).roundToInt().coerceAtMost(MAX_ROOM_PX)
        val floorFraction = (padding + stageHeightDp * FEET_FRACTION) / size.height.value
        val detail = if (size.height.value < 150f) RoomDetail.NONE else RoomDetail.WALL

        val frames = FRAMES.getOrPut(
            "$state|$tempo|$workProp|${stageWidthDp.roundToInt()}x${stageHeightDp.roundToInt()}|$density|${snapshot.outfit}",
        ) {
            renderFrames(state, tempo, stageWidthDp, stageHeightDp, density, snapshot.outfit, workProp)
        }
        val flipper = buildFlipper(context, tempo, frames)
        // Only the decor this cut of the room can actually show goes into the
        // key, or buying a floor item would re-render a widget whose floor is
        // hidden for an identical picture.
        val decor = snapshot.owned.filter { decorVisible(it, detail) }.sorted()
        val room = ROOMS.getOrPut(
            "${roomWidthPx}x$roomHeightPx|${palette.room.night}|${snapshot.theme}|${floorFraction.round3()}|$detail|${decor.joinToString(",")}",
        ) {
            PetRasterizer.roomPng(
                widthPx = roomWidthPx,
                heightPx = roomHeightPx,
                density = density,
                colors = palette.room,
                cornerRadiusPx = CARD_CORNER_DP * density,
                // Put the wall/floor junction just behind her feet, wherever the
                // character happens to sit in this size of widget.
                floorFraction = floorFraction,
                detail = detail,
                decor = decor.toSet(),
                theme = snapshot.theme,
            )
        }

        provideContent {
            GlanceTheme {
                WidgetBody(
                    stageWidthDp = stageWidthDp,
                    stageHeightDp = stageHeightDp,
                    padding = padding,
                    flipper = flipper,
                    room = ImageProvider(Icon.createWithData(room, 0, room.size)),
                )
            }
        }
    }

    /**
     * Rasterises the loop, shrinking it if it would not fit on the wire.
     *
     * Every frame travels to the launcher inside a single Binder transaction
     * with about a megabyte to share across the whole system, and an oversized
     * update is silently dropped — the user just sees a blank widget. Measured
     * at the capped size a twelve-frame loop lands near 300 KB, but a state
     * dense with detail can run over, so the total is checked and the whole
     * loop re-rendered smaller rather than risking the drop.
     */
    private fun renderFrames(
        state: PetState,
        tempo: FlipTempo,
        stageWidthDp: Float,
        stageHeightDp: Float,
        density: Float,
        outfit: String,
        workProp: Prop?,
    ): List<ByteArray> {
        // Transparent: the room is a separate layer underneath, so the frames
        // carry nothing but the character.
        fun render(scale: Float) = PetRasterizer.animationFrames(
            state = state,
            widthPx = (stageWidthDp * density * scale).roundToInt().coerceAtMost(maxFramePx(scale)),
            heightPx = (stageHeightDp * density * scale).roundToInt().coerceAtMost(maxFramePx(scale)),
            density = density,
            frameCount = FRAME_COUNT,
            loopSeconds = tempo.loopSeconds,
            palette = PetPalette.forOutfit(outfit),
            workProp = workProp,
        )

        val full = render(1f)
        if (full.sumOf { it.size } <= PAYLOAD_BUDGET_BYTES) return full
        return render(SHRUNK_SCALE)
    }

    private fun maxFramePx(scale: Float): Int = (MAX_FRAME_PX * scale).roundToInt()

    /**
     * A `ViewFlipper` with one `ImageView` per frame.
     *
     * The frames go over as `Icon`s wrapping PNG bytes rather than as bitmaps:
     * a bitmap is marshalled uncompressed, and a dozen uncompressed frames
     * would blow the transaction budget on their own.
     */
    private fun buildFlipper(
        context: Context,
        tempo: FlipTempo,
        frames: List<ByteArray>,
    ): RemoteViews {
        val flipper = RemoteViews(context.packageName, tempo.layoutRes)
        frames.forEach { png ->
            val frame = RemoteViews(context.packageName, R.layout.widget_pet_frame)
            frame.setImageViewIcon(R.id.pet_frame, Icon.createWithData(png, 0, png.size))
            flipper.addView(R.id.pet_flipper, frame)
        }
        return flipper
    }

    @Composable
    private fun WidgetBody(
        stageWidthDp: Float,
        stageHeightDp: Float,
        padding: Float,
        flipper: RemoteViews,
        room: ImageProvider,
    ) {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .cornerRadius(CARD_CORNER_DP.dp)
                .clickable(actionStartActivity<MainActivity>()),
            contentAlignment = Alignment.Center,
        ) {
            // The room spans the whole widget so there is no panel edge around
            // the character; its rounded corners are baked into the bitmap
            // because a RemoteViews parent cannot clip its children.
            Image(
                provider = room,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = GlanceModifier.fillMaxSize(),
            )

            Box(
                modifier = GlanceModifier.fillMaxSize().padding(padding.dp),
                contentAlignment = Alignment.Center,
            ) {
                AndroidRemoteViews(
                    remoteViews = flipper,
                    modifier = GlanceModifier.size(stageWidthDp.dp, stageHeightDp.dp),
                )
            }
        }
    }

    companion object {
        /**
         * Ten frames per loop. Measured against the real PNG encoder the
         * busiest state lands near 310 KB at the capped resolution, which
         * leaves comfortable room under the transaction limit; twelve pushed it
         * to 373 KB and tripped the shrink path on every redraw.
         */
        private const val FRAME_COUNT = 10
        private const val MAX_FRAME_PX = 420
        private const val MAX_ROOM_PX = 700

        /** How far down the character's own canvas her feet land. */
        private const val FEET_FRACTION = 0.86f
        private const val CARD_CORNER_DP = 22f

        /** Comfortably under the transaction limit, with room for the rest of the tree. */
        private const val PAYLOAD_BUDGET_BYTES = 400 * 1024
        private const val SHRUNK_SCALE = 0.7f

        /**
         * Rasterised art, reused across redraws.
         *
         * A loop can approach [PAYLOAD_BUDGET_BYTES], so three of them is
         * already about a megabyte; rooms are a single frame each and cost far
         * less, so a few more of those are affordable.
         */
        private val FRAMES = WidgetArtCache<List<ByteArray>>(maxEntries = 3)
        private val ROOMS = WidgetArtCache<ByteArray>(maxEntries = 4)

        /** Frees the cached art — for tests, and for onTrimMemory. */
        fun releaseArt() {
            FRAMES.clear()
            ROOMS.clear()
        }

        /** Breathing room around the character, tighter on a small placement. */
        internal fun paddingFor(size: DpSize): Float = if (size.height.value < 150f) 8f else 14f

        /**
         * How tall the character is drawn.
         *
         * With nothing else in the widget she simply takes it all, bounded by
         * whichever of the two dimensions runs out first so she never overflows
         * a short, wide placement.
         */
        internal fun stageHeight(size: DpSize, padding: Float): Float {
            val byHeight = (size.height.value - padding * 2f).coerceAtLeast(48f)
            val byWidth = (size.width.value - padding * 2f) * (ART_HEIGHT / ART_WIDTH)
            return min(byHeight, byWidth)
        }

        /** Redraws every placed widget. Cheap, and safe to call from anywhere. */
        suspend fun refresh(context: Context) {
            PetWidget().updateAll(context)
        }
    }
}

/**
 * How fast the flipbook runs.
 *
 * Each state's animation has its own natural speed — typing is quick, sleeping
 * is slow — so a single interval would leave one of them wrong. The interval
 * lives in the layout XML rather than being pushed through
 * `RemoteViews.setInt`, so there are three layouts and each state picks one.
 */
private enum class FlipTempo(val layoutRes: Int, val loopSeconds: Float) {
    FAST(R.layout.widget_pet_flipper_fast, 1.5f),
    NORMAL(R.layout.widget_pet_flipper_normal, 2.5f),
    SLOW(R.layout.widget_pet_flipper_slow, 5f),
    ;

    companion object {
        fun forState(state: PetState): FlipTempo = when (state) {
            PetState.WORKING, PetState.PLAYING, PetState.CELEBRATING,
            PetState.HAPPY, PetState.EATING,
            -> FAST
            PetState.IDLE, PetState.LOVED, PetState.HUNGRY -> NORMAL
            PetState.TIRED, PetState.SLEEPING, PetState.STUDYING, PetState.SICK -> SLOW
        }
    }
}

/**
 * Widget colours.
 *
 * Glance 1.1 exposes only the single-`Color` provider — the resource-id and
 * day/night overloads are both restricted to the library group — so the theme
 * is resolved from the configuration here instead. Widgets are re-provided on a
 * configuration change, so this stays correct when the system theme flips.
 */
private data class WidgetPalette(val room: RoomColors) {
    companion object {
        /** The bought room reaches the home screen too, or it is half a purchase. */
        fun of(night: Boolean, theme: String): WidgetPalette {
            val room = StageColors.forTheme(theme)
            return WidgetPalette(
                if (night) {
                    RoomColors(top = room.nightTop, bottom = room.nightBottom, floor = room.floorDark, night = true)
                } else {
                    RoomColors(top = room.dayTop, bottom = room.dayBottom, floor = room.floorLight, night = false)
                },
            )
        }
    }
}

/**
 * The widget's real size in dp.
 *
 * `SizeMode.Exact` re-runs `provideGlance` on every resize, but the size it
 * exposes is only available inside the composition — and the frames have to be
 * rasterised before that. The options bundle carries the same numbers, using
 * the portrait pair (min width, max height).
 */
private fun widgetSize(context: Context, id: GlanceId): DpSize {
    val appWidgetId = runCatching { GlanceAppWidgetManager(context).getAppWidgetId(id) }
        .getOrNull() ?: return DEFAULT_WIDGET_SIZE
    val options = runCatching { AppWidgetManager.getInstance(context).getAppWidgetOptions(appWidgetId) }
        .getOrNull() ?: return DEFAULT_WIDGET_SIZE

    val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
    val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
    if (width <= 0 || height <= 0) return DEFAULT_WIDGET_SIZE
    return DpSize(width.dp, height.dp)
}

private val DEFAULT_WIDGET_SIZE = DpSize(250.dp, 180.dp)

/** Whether an owned upgrade shows in this cut of the room at all. */
private fun decorVisible(id: String, detail: RoomDetail): Boolean = when (id) {
    "coffee_machine", "laptop", "textbooks", "studio" -> detail != RoomDetail.NONE
    "fridge", "bed", "console", "cat" -> detail == RoomDetail.FULL
    else -> false
}

/** Keeps float rounding out of a cache key. */
private fun Float.round3(): Int = (this * 1000f).roundToInt()

class PetWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PetWidget()
}

/**
 * Widgets are created by the system, not by Hilt, so they reach the singleton
 * graph through an entry point.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun petRepository(): PetRepository
}

private fun repositoryOf(context: Context): PetRepository =
    EntryPointAccessors
        .fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
        .petRepository()
