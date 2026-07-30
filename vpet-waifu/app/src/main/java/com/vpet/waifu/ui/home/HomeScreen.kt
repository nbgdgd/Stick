package com.vpet.waifu.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vpet.waifu.R
import com.vpet.waifu.domain.OccupationKind
import com.vpet.waifu.domain.PetSnapshot
import com.vpet.waifu.domain.PetState
import com.vpet.waifu.domain.PetTuning
import com.vpet.waifu.ui.components.ActionButton
import com.vpet.waifu.ui.components.EffectChip
import com.vpet.waifu.ui.components.LevelRing
import com.vpet.waifu.ui.components.MoneyPill
import com.vpet.waifu.ui.components.OutlineButton
import com.vpet.waifu.ui.components.PanelCard
import com.vpet.waifu.ui.components.PetStage
import com.vpet.waifu.ui.components.PrimaryButton
import com.vpet.waifu.ui.components.StatBarTrack
import com.vpet.waifu.ui.components.StatRow
import com.vpet.waifu.ui.components.StatusChip
import com.vpet.waifu.ui.formatRemaining
import com.vpet.waifu.ui.occupationEmoji
import com.vpet.waifu.ui.occupationNameRes
import com.vpet.waifu.ui.stateLabelRes
import com.vpet.waifu.ui.theme.Accents
import com.vpet.waifu.ui.theme.StatColors
import com.vpet.waifu.ui.theme.Surfaces

/**
 * Her room: the animated character, what she is up to, her stats, and the care
 * actions that do not cost money.
 */
@Composable
fun HomeScreen(
    snapshot: PetSnapshot,
    tuning: PetTuning,
    nowMillis: Long,
    bubbleEnabled: Boolean,
    overlayPermissionGranted: Boolean,
    onGrantOverlayPermission: () -> Unit,
    onBubbleEnabledChange: (Boolean) -> Unit,
    onFeed: () -> Unit,
    onPet: () -> Unit,
    onToggleSleep: () -> Unit,
    onCancelOccupation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = snapshot.state(nowMillis, tuning)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Header(snapshot)

        Box {
            PetStage(state = state, height = 320.dp)
            StatusChip(
                text = stringResource(stateLabelRes(state)),
                dot = statusDot(state),
                modifier = Modifier.padding(14.dp),
            )
        }

        AnimatedVisibility(visible = snapshot.isBusy) {
            SessionCard(snapshot, nowMillis, onCancelOccupation)
        }

        StatsCard(snapshot)
        CareRow(snapshot, tuning, onFeed, onPet, onToggleSleep)
        BubbleCard(
            bubbleEnabled = bubbleEnabled,
            overlayPermissionGranted = overlayPermissionGranted,
            onGrantOverlayPermission = onGrantOverlayPermission,
            onBubbleEnabledChange = onBubbleEnabledChange,
        )
        Spacer(Modifier.height(4.dp))
    }
}

private fun statusDot(state: PetState): Color = when (state) {
    PetState.WORKING, PetState.STUDYING -> StatColors.Exp
    PetState.HUNGRY -> StatColors.Hunger
    PetState.TIRED, PetState.SLEEPING -> StatColors.Energy
    else -> Accents.Bright
}

@Composable
private fun Header(snapshot: PetSnapshot) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LevelRing(exp = snapshot.progress.exp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Accents.Text,
            )
            Text(
                text = stringResource(R.string.level_label, snapshot.level),
                style = MaterialTheme.typography.bodyMedium,
                color = Accents.TextMuted,
            )
        }
        MoneyPill(amount = snapshot.progress.money)
    }
}

@Composable
private fun SessionCard(snapshot: PetSnapshot, nowMillis: Long, onCancel: () -> Unit) {
    val session = snapshot.session ?: return
    val occupation = snapshot.occupation ?: return

    PanelCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(Accents.Primary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(occupationEmoji(occupation.id), fontSize = 22.sp)
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(occupationNameRes(occupation.id)),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Accents.Text,
                    )
                    Text(
                        text = stringResource(
                            R.string.session_remaining,
                            formatRemaining(session.remainingMillis(nowMillis)),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Accents.TextMuted,
                    )
                }
                Spacer(Modifier.width(10.dp))
                // Wages land every minute now, so the running total belongs on
                // the card — otherwise the only sign she is being paid is the
                // wallet quietly ticking up somewhere else on the screen.
                EffectChip(
                    emoji = if (occupation.kind == OccupationKind.WORK) "💰" else "⭐",
                    text = "+${if (occupation.kind == OccupationKind.WORK) session.paidOut else session.paidExp}",
                    tint = if (occupation.kind == OccupationKind.WORK) StatColors.Money else StatColors.Exp,
                )
            }
            Spacer(Modifier.height(14.dp))
            StatBarTrack(fraction = session.progress(nowMillis), color = Accents.Bright, height = 7.dp)
            Spacer(Modifier.height(14.dp))
            // Full width and on its own line: beside the title it fought the
            // job name for space and both ended up truncated.
            OutlineButton(
                text = stringResource(R.string.action_call_home),
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun StatsCard(snapshot: PetSnapshot) {
    PanelCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            StatRow(
                emoji = "🍽",
                label = stringResource(R.string.stat_hunger),
                value = snapshot.stats.hunger,
                color = StatColors.Hunger,
            )
            StatRow(
                emoji = "⚡",
                label = stringResource(R.string.stat_energy),
                value = snapshot.stats.energy,
                color = StatColors.Energy,
            )
            StatRow(
                emoji = "💜",
                label = stringResource(R.string.stat_mood),
                value = snapshot.stats.mood,
                color = StatColors.Mood,
            )
        }
    }
}

@Composable
private fun CareRow(
    snapshot: PetSnapshot,
    tuning: PetTuning,
    onFeed: () -> Unit,
    onPet: () -> Unit,
    onToggleSleep: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ActionButton(
            icon = Icons.Default.Restaurant,
            label = stringResource(R.string.action_feed),
            tint = StatColors.Hunger,
            enabled = snapshot.canFeed(tuning),
            onClick = onFeed,
            modifier = Modifier.weight(1f),
        )
        ActionButton(
            icon = if (snapshot.isSleeping) Icons.Default.WbSunny else Icons.Default.Bedtime,
            label = stringResource(
                if (snapshot.isSleeping) R.string.action_wake else R.string.action_sleep,
            ),
            tint = StatColors.Energy,
            enabled = !snapshot.isBusy,
            onClick = onToggleSleep,
            modifier = Modifier.weight(1f),
        )
        ActionButton(
            icon = Icons.Default.Favorite,
            label = stringResource(R.string.action_pet),
            tint = StatColors.Mood,
            enabled = snapshot.acceptsInteraction,
            onClick = onPet,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun BubbleCard(
    bubbleEnabled: Boolean,
    overlayPermissionGranted: Boolean,
    onGrantOverlayPermission: () -> Unit,
    onBubbleEnabledChange: (Boolean) -> Unit,
) {
    PanelCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Accents.Primary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("🫧", fontSize = 19.sp)
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.bubble_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = Accents.Text,
                    )
                    Text(
                        text = stringResource(R.string.bubble_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = Accents.TextMuted,
                    )
                }
                Switch(
                    checked = bubbleEnabled && overlayPermissionGranted,
                    onCheckedChange = onBubbleEnabledChange,
                    enabled = overlayPermissionGranted,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Accents.Primary,
                        uncheckedTrackColor = Surfaces.Track,
                        uncheckedBorderColor = Surfaces.Divider,
                    ),
                )
            }

            if (!overlayPermissionGranted) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.overlay_permission_rationale),
                    style = MaterialTheme.typography.bodySmall,
                    color = Accents.Danger,
                )
                Spacer(Modifier.height(10.dp))
                PrimaryButton(
                    text = stringResource(R.string.action_grant_overlay),
                    onClick = onGrantOverlayPermission,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
