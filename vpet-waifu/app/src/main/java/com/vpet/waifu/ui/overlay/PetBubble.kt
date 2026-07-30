package com.vpet.waifu.ui.overlay

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vpet.waifu.R
import com.vpet.waifu.domain.PetSnapshot
import com.vpet.waifu.domain.PetTuning
import com.vpet.waifu.ui.accentFor
import com.vpet.waifu.ui.character.AnimatedPet
import com.vpet.waifu.ui.character.PetPalette
import com.vpet.waifu.ui.components.StatBarTrack
import com.vpet.waifu.ui.components.StatRow
import com.vpet.waifu.ui.formatRemaining
import com.vpet.waifu.ui.occupationEmoji
import com.vpet.waifu.ui.occupationNameRes
import com.vpet.waifu.ui.stateLabelRes
import com.vpet.waifu.ui.theme.Accents
import com.vpet.waifu.ui.theme.StatColors
import com.vpet.waifu.ui.theme.Surfaces

private val BUBBLE_SIZE = 84.dp
private val PANEL_WIDTH = 232.dp

/**
 * The floating pet and her mini-panel.
 *
 * The whole thing is `WRAP_CONTENT` in the window, so the panel simply extends
 * the composable downwards and [com.vpet.waifu.service.OverlayWindow] re-clamps
 * the window when the size changes.
 */
@Composable
fun PetBubble(
    snapshot: PetSnapshot,
    tuning: PetTuning,
    nowMillis: Long,
    expanded: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onDragEnd: () -> Unit,
    onFeed: () -> Unit,
    onPet: () -> Unit,
    onToggleSleep: () -> Unit,
    onOpenApp: () -> Unit,
    onHide: () -> Unit,
) {
    val state = snapshot.state(nowMillis, tuning)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = CircleShape,
            color = Surfaces.Card.copy(alpha = 0.92f),
            border = BorderStroke(2.dp, accentFor(state)),
            shadowElevation = 6.dp,
            modifier = Modifier
                .size(BUBBLE_SIZE)
                // Two detectors: the drag one consumes movement past touch slop,
                // which is what cancels the pending tap, so a flick to reposition
                // her never also opens the panel.
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onTap() }, onLongPress = { onLongPress() })
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = onDragEnd,
                        onDrag = { change, amount ->
                            change.consume()
                            onDrag(amount.x, amount.y)
                        },
                    )
                },
        ) {
            AnimatedPet(
                state = state,
                palette = PetPalette.forOutfit(snapshot.outfit),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(3.dp),
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            PetPanel(
                snapshot = snapshot,
                tuning = tuning,
                nowMillis = nowMillis,
                onFeed = onFeed,
                onPet = onPet,
                onToggleSleep = onToggleSleep,
                onOpenApp = onOpenApp,
                onHide = onHide,
            )
        }
    }
}

@Composable
private fun PetPanel(
    snapshot: PetSnapshot,
    tuning: PetTuning,
    nowMillis: Long,
    onFeed: () -> Unit,
    onPet: () -> Unit,
    onToggleSleep: () -> Unit,
    onOpenApp: () -> Unit,
    onHide: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Surfaces.Card.copy(alpha = 0.97f),
        border = BorderStroke(1.dp, Surfaces.CardBorder),
        shadowElevation = 8.dp,
        modifier = Modifier
            .padding(top = 6.dp)
            .width(PANEL_WIDTH),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.width(PANEL_WIDTH - 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(stateLabelRes(snapshot.state(nowMillis, tuning))),
                    style = MaterialTheme.typography.labelLarge,
                    color = Accents.Text,
                )
                Text(
                    text = "Lv ${snapshot.level} · ${snapshot.progress.money}¥",
                    style = MaterialTheme.typography.labelSmall,
                    color = Accents.TextMuted,
                )
            }

            // A shift in progress is the thing you most want to know from the
            // bubble, so it gets the top of the panel.
            snapshot.session?.let { session ->
                val occupation = snapshot.occupation
                if (occupation != null) {
                    Text(
                        text = "${occupationEmoji(occupation.id)} " +
                            stringResource(occupationNameRes(occupation.id)) +
                            " · " + formatRemaining(session.remainingMillis(nowMillis)),
                        style = MaterialTheme.typography.labelSmall,
                        color = Accents.Text,
                    )
                    StatBarTrack(
                        fraction = session.progress(nowMillis),
                        color = Accents.Bright,
                        modifier = Modifier.width(PANEL_WIDTH - 24.dp),
                        height = 5.dp,
                    )
                }
            }

            StatRow(
                emoji = "🍽",
                label = stringResource(R.string.stat_hunger),
                value = snapshot.stats.hunger,
                color = StatColors.Hunger,
                compact = true,
            )
            StatRow(
                emoji = "⚡",
                label = stringResource(R.string.stat_energy),
                value = snapshot.stats.energy,
                color = StatColors.Energy,
                compact = true,
            )
            StatRow(
                emoji = "💜",
                label = stringResource(R.string.stat_mood),
                value = snapshot.stats.mood,
                color = StatColors.Mood,
                compact = true,
            )

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .width(PANEL_WIDTH - 24.dp)
                    .padding(top = 2.dp),
            ) {
                PanelAction(
                    icon = Icons.Default.Restaurant,
                    labelRes = R.string.action_feed,
                    enabled = snapshot.canFeed(tuning),
                    onClick = onFeed,
                )
                PanelAction(
                    icon = if (snapshot.isSleeping) Icons.Default.WbSunny else Icons.Default.Bedtime,
                    labelRes = if (snapshot.isSleeping) R.string.action_wake else R.string.action_sleep,
                    enabled = !snapshot.isBusy,
                    onClick = onToggleSleep,
                )
                PanelAction(
                    icon = Icons.Default.Favorite,
                    labelRes = R.string.action_pet,
                    enabled = snapshot.acceptsInteraction,
                    onClick = onPet,
                )
                PanelAction(
                    icon = Icons.AutoMirrored.Filled.OpenInNew,
                    labelRes = R.string.action_open_app,
                    enabled = true,
                    onClick = onOpenApp,
                )
                PanelAction(
                    icon = Icons.Default.Close,
                    labelRes = R.string.action_hide_bubble,
                    enabled = true,
                    onClick = onHide,
                )
            }
        }
    }
}

@Composable
private fun PanelAction(
    icon: ImageVector,
    @StringRes labelRes: Int,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    FilledTonalIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(38.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = stringResource(labelRes),
            modifier = Modifier.size(18.dp),
        )
    }
}
