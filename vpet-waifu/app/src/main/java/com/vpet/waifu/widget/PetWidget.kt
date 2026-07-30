package com.vpet.waifu.widget

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
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
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
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
import com.vpet.waifu.data.PetRepository
import com.vpet.waifu.domain.PetSnapshot
import com.vpet.waifu.domain.PetState
import com.vpet.waifu.domain.PetStats
import com.vpet.waifu.ui.character.renderPetBitmap
import com.vpet.waifu.ui.theme.StatColors
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlin.math.roundToInt

private const val PORTRAIT_DP = 74f

/**
 * Widget colours.
 *
 * Glance 1.1 exposes only the single-`Color` provider — the resource-id and
 * day/night overloads are both restricted to the library group — so the theme
 * is resolved from the configuration here instead. Widgets are re-provided on a
 * configuration change, so this stays correct when the system theme flips.
 */
private data class WidgetPalette(
    val background: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val track: Color,
    val action: Color,
    val actionDisabled: Color,
) {
    companion object {
        private val Light = WidgetPalette(
            background = Color(0xFFF3EDFC),
            textPrimary = Color(0xFF3C2F60),
            textSecondary = Color(0xFF6E5D97),
            track = Color(0x22000000),
            action = Color(0xFFE2D6F8),
            actionDisabled = Color(0x14000000),
        )
        private val Dark = WidgetPalette(
            background = Color(0xFF221B36),
            textPrimary = Color(0xFFE7DDFB),
            textSecondary = Color(0xFFB9A8DC),
            track = Color(0x33FFFFFF),
            action = Color(0xFF3E3363),
            actionDisabled = Color(0x1AFFFFFF),
        )

        fun of(context: Context): WidgetPalette {
            val night = context.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            return if (night) Dark else Light
        }
    }
}

/**
 * The home-screen widget: the pet, her three stats, and one-tap care.
 *
 * Glance cannot run a Compose canvas, so the character is rasterised by
 * [renderPetBitmap] — the same renderer the app and the bubble use, so the
 * widget can never drift out of sync with the art.
 */
class PetWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Reading through the repository ticks the world forward first, so the
        // widget shows the pet as she is now rather than as she was when the
        // phone last wrote to disk.
        val snapshot = repositoryOf(context).snapshotNow()
        val state = snapshot.state(System.currentTimeMillis())
        val density = context.resources.displayMetrics.density
        val bitmap = renderPetBitmap(
            state = state,
            widthPx = (PORTRAIT_DP * density).roundToInt(),
            heightPx = (PORTRAIT_DP * 1.3f * density).roundToInt(),
            density = density,
        )

        val palette = WidgetPalette.of(context)
        provideContent {
            GlanceTheme {
                WidgetBody(snapshot, state, ImageProvider(bitmap), palette)
            }
        }
    }

    @Composable
    private fun WidgetBody(
        snapshot: PetSnapshot,
        state: PetState,
        pet: ImageProvider,
        palette: WidgetPalette,
    ) {
        Row(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ColorProvider(palette.background))
                .cornerRadius(22.dp)
                .padding(12.dp)
                .clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                provider = pet,
                contentDescription = state.name,
                modifier = GlanceModifier.size(PORTRAIT_DP.dp, (PORTRAIT_DP * 1.3f).dp),
            )
            Spacer(GlanceModifier.width(10.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = headlineFor(state),
                    style = TextStyle(
                        fontWeight = FontWeight.Medium,
                        color = ColorProvider(palette.textPrimary),
                    ),
                )
                Spacer(GlanceModifier.height(8.dp))
                StatRow("🍙", snapshot.stats.hunger, StatColors.Hunger, palette)
                StatRow("⚡", snapshot.stats.energy, StatColors.Energy, palette)
                StatRow("💜", snapshot.stats.mood, StatColors.Mood, palette)
                Spacer(GlanceModifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Lv ${snapshot.level}  ·  ${snapshot.progress.money} ¥",
                        style = TextStyle(color = ColorProvider(palette.textSecondary)),
                    )
                    Spacer(GlanceModifier.defaultWeight())
                    // Care without opening the app — the point of having a widget.
                    QuickAction("🍽", WidgetAction.FEED, snapshot.canFeed(), palette)
                    Spacer(GlanceModifier.width(4.dp))
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
            .size(30.dp)
            .cornerRadius(15.dp)
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
        /** Redraws every placed widget. Cheap, and safe to call from anywhere. */
        suspend fun refresh(context: Context) {
            PetWidget().updateAll(context)
        }
    }
}

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
