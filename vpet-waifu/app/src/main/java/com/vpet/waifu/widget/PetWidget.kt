package com.vpet.waifu.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.vpet.waifu.MainActivity
import com.vpet.waifu.R
import com.vpet.waifu.data.PetRepository
import com.vpet.waifu.domain.PetSnapshot
import com.vpet.waifu.domain.PetState
import com.vpet.waifu.domain.PetStats
import com.vpet.waifu.ui.character.ART_HEIGHT
import com.vpet.waifu.ui.character.ART_WIDTH
import com.vpet.waifu.ui.character.PetRasterizer
import com.vpet.waifu.ui.character.RoomColors
import com.vpet.waifu.ui.character.RoomDetail
import com.vpet.waifu.ui.theme.StageColors
import com.vpet.waifu.ui.theme.StatColors
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The home-screen widget: the pet, animating, in the middle.
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
        val state = snapshot.state(System.currentTimeMillis())
        val palette = WidgetPalette.of(context)
        val size = widgetSize(context, id)
        val layout = WidgetLayout.forSize(size)
        val density = context.resources.displayMetrics.density

        val stageHeightDp = layout.stageHeight(size)
        val stageWidthDp = stageHeightDp * (ART_WIDTH / ART_HEIGHT)
        val tempo = FlipTempo.forState(state)

        // Both bitmaps depend only on what is in their keys, and neither
        // depends on a stat. Without the cache a wage landing every minute of a
        // shift re-encoded the entire loop every minute.
        val roomWidthPx = (size.width.value * density).roundToInt().coerceAtMost(MAX_ROOM_PX)
        val roomHeightPx = (size.height.value * density).roundToInt().coerceAtMost(MAX_ROOM_PX)
        val floorFraction = (layout.padding + stageHeightDp * FEET_FRACTION) / size.height.value
        val detail = if (layout == WidgetLayout.PORTRAIT_ONLY) RoomDetail.NONE else RoomDetail.WALL

        val frames = FRAMES.getOrPut("$state|$tempo|${stageWidthDp.roundToInt()}x${stageHeightDp.roundToInt()}|$density") {
            renderFrames(state, tempo, stageWidthDp, stageHeightDp, density)
        }
        val flipper = buildFlipper(context, tempo, frames)
        val room = ROOMS.getOrPut("${roomWidthPx}x$roomHeightPx|${palette.room.night}|${floorFraction.round3()}|$detail") {
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
            )
        }

        provideContent {
            GlanceTheme {
                WidgetBody(
                    snapshot = snapshot,
                    state = state,
                    palette = palette,
                    layout = layout,
                    stageWidthDp = stageWidthDp,
                    stageHeightDp = stageHeightDp,
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
        snapshot: PetSnapshot,
        state: PetState,
        palette: WidgetPalette,
        layout: WidgetLayout,
        stageWidthDp: Float,
        stageHeightDp: Float,
        flipper: RemoteViews,
        room: ImageProvider,
    ) {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .cornerRadius(CARD_CORNER_DP.dp)
                .clickable(actionStartActivity<MainActivity>()),
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

            Column(
                modifier = GlanceModifier.fillMaxSize().padding(layout.padding.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = GlanceModifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    AndroidRemoteViews(
                        remoteViews = flipper,
                        modifier = GlanceModifier.size(stageWidthDp.dp, stageHeightDp.dp),
                    )
                }

                if (layout.showsStats) {
                    Spacer(GlanceModifier.height(8.dp))
                    Text(
                        text = headlineFor(state),
                        style = TextStyle(
                            fontWeight = FontWeight.Medium,
                            color = ColorProvider(palette.textPrimary),
                        ),
                    )
                    Spacer(GlanceModifier.height(6.dp))
                    StatRow("🍙", snapshot.stats.hunger, StatColors.Hunger, palette)
                    StatRow("⚡", snapshot.stats.energy, StatColors.Energy, palette)
                    StatRow("💜", snapshot.stats.mood, StatColors.Mood, palette)
                }

                if (layout.showsFooter) {
                    Spacer(GlanceModifier.height(6.dp))
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Lv ${snapshot.level}  ·  ${snapshot.progress.money} ¥",
                            style = TextStyle(color = ColorProvider(palette.textSecondary)),
                        )
                        Spacer(GlanceModifier.defaultWeight())
                        // Care without opening the app — the point of a widget.
                        QuickAction("🍽", WidgetAction.FEED, snapshot.canFeed(), palette)
                        Spacer(GlanceModifier.width(6.dp))
                        QuickAction(
                            if (snapshot.isSleeping) "☀" else "🌙",
                            WidgetAction.TOGGLE_SLEEP,
                            !snapshot.isBusy,
                            palette,
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun StatRow(icon: String, value: Float, color: Color, palette: WidgetPalette) {
        Row(
            modifier = GlanceModifier.fillMaxWidth().padding(bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = icon)
            Spacer(GlanceModifier.width(6.dp))
            LinearProgressIndicator(
                progress = (value / PetStats.MAX).coerceIn(0f, 1f),
                modifier = GlanceModifier.defaultWeight().height(7.dp).cornerRadius(4.dp),
                color = ColorProvider(color),
                backgroundColor = ColorProvider(palette.track),
            )
        }
    }

    @Composable
    private fun QuickAction(
        label: String,
        action: WidgetAction,
        enabled: Boolean,
        palette: WidgetPalette,
    ) {
        val base = GlanceModifier
            .size(32.dp)
            .cornerRadius(16.dp)
            .background(ColorProvider(if (enabled) palette.action else palette.actionDisabled))
        // actionRunCallback is itself composable, so the clickable modifier is
        // built here rather than inside a conditional argument.
        val modifier = if (enabled) {
            base.clickable(
                actionRunCallback<PetWidgetAction>(
                    actionParametersOf(PetWidgetAction.ACTION_KEY to action.name),
                ),
            )
        } else {
            base
        }

        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(text = label)
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
            PetState.TIRED, PetState.SLEEPING, PetState.STUDYING -> SLOW
        }
    }
}

/**
 * How much of the widget fits.
 *
 * Small placements get the character alone — a 2x2 cell has no room for three
 * bars and still reads as a pet rather than a dashboard.
 */
private enum class WidgetLayout(
    val padding: Int,
    val showsStats: Boolean,
    val showsFooter: Boolean,
) {
    PORTRAIT_ONLY(padding = 6, showsStats = false, showsFooter = false),
    WITH_STATS(padding = 10, showsStats = true, showsFooter = false),
    FULL(padding = 12, showsStats = true, showsFooter = true),
    ;

    /** Height for the character, leaving room for whatever else is shown. */
    fun stageHeight(size: DpSize): Float {
        val available = size.height.value - padding * 2f
        val reserved = (if (showsStats) 78f else 0f) + (if (showsFooter) 44f else 0f)
        val byHeight = (available - reserved).coerceAtLeast(48f)
        // Never so wide that the character overflows a short, wide widget.
        val byWidth = (size.width.value - padding * 2f) * (ART_HEIGHT / ART_WIDTH)
        return min(byHeight, byWidth)
    }

    companion object {
        fun forSize(size: DpSize): WidgetLayout {
            val w = size.width.value
            val h = size.height.value
            return when {
                h >= 200f && w >= 200f -> FULL
                h >= 130f && w >= 150f -> WITH_STATS
                else -> PORTRAIT_ONLY
            }
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
private data class WidgetPalette(
    val textPrimary: Color,
    val textSecondary: Color,
    val track: Color,
    val action: Color,
    val actionDisabled: Color,
    val room: RoomColors,
) {
    companion object {
        fun of(context: Context): WidgetPalette {
            val night = context.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            return if (night) Dark else Light
        }

        private val DayRoom = RoomColors(
            top = StageColors.DayTop,
            bottom = StageColors.DayBottom,
            floor = StageColors.FloorLight,
            night = false,
        )
        private val NightRoom = RoomColors(
            top = StageColors.NightTop,
            bottom = StageColors.NightBottom,
            floor = StageColors.FloorDark,
            night = true,
        )

        private val Light = WidgetPalette(
            textPrimary = Color(0xFF3C2F60),
            textSecondary = Color(0xFF6E5D97),
            track = Color(0x22000000),
            action = Color(0xFFE2D6F8),
            actionDisabled = Color(0x14000000),
            room = DayRoom,
        )
        private val Dark = WidgetPalette(
            textPrimary = Color(0xFFE7DDFB),
            textSecondary = Color(0xFFB9A8DC),
            track = Color(0x33FFFFFF),
            action = Color(0xFF3E3363),
            actionDisabled = Color(0x1AFFFFFF),
            room = NightRoom,
        )
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

/** Keeps float rounding out of a cache key. */
private fun Float.round3(): Int = (this * 1000f).roundToInt()

private fun headlineFor(state: PetState): String = when (state) {
    PetState.WORKING -> "На работе"
    PetState.STUDYING -> "Учится"
    PetState.SLEEPING -> "Спит"
    PetState.HUNGRY -> "Хочет есть"
    PetState.TIRED -> "Устала"
    PetState.PLAYING -> "Играет"
    PetState.HAPPY -> "Отлично!"
    PetState.EATING -> "Кушает"
    PetState.LOVED -> "Довольна"
    PetState.CELEBRATING -> "Ура!"
    PetState.IDLE -> "Ждёт тебя"
}

enum class WidgetAction { FEED, TOGGLE_SLEEP }

/** Runs a care action straight from the home screen and redraws the widget. */
class PetWidgetAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val repository = repositoryOf(context)
        when (parameters[ACTION_KEY]?.let { runCatching { WidgetAction.valueOf(it) }.getOrNull() }) {
            WidgetAction.FEED -> repository.feed()
            WidgetAction.TOGGLE_SLEEP -> repository.toggleSleep()
            null -> repository.tick()
        }
        PetWidget().updateAll(context)
    }

    companion object {
        val ACTION_KEY = ActionParameters.Key<String>("vpet_widget_action")
    }
}

class PetWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PetWidget()
}

/**
 * Widgets and their action callbacks are created by the system, not by Hilt, so
 * they reach the singleton graph through an entry point.
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
