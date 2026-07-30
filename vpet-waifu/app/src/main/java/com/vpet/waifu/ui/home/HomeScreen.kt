package com.vpet.waifu.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vpet.waifu.R
import com.vpet.waifu.domain.PetSnapshot
import com.vpet.waifu.domain.PetTuning
import com.vpet.waifu.ui.components.LevelRing
import com.vpet.waifu.ui.components.PetStage
import com.vpet.waifu.ui.components.StatBar
import com.vpet.waifu.ui.components.StatChip
import com.vpet.waifu.ui.formatRemaining
import com.vpet.waifu.ui.occupationEmoji
import com.vpet.waifu.ui.occupationNameRes
import com.vpet.waifu.ui.stateLabelRes
import com.vpet.waifu.ui.theme.StatColors

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
        HeaderRow(snapshot)

        Box {
            PetStage(state = state)
            Text(
                text = stringResource(stateLabelRes(state)),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        Spacer(Modifier.size(4.dp))
    }
}

@Composable
private fun HeaderRow(snapshot: PetSnapshot) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        LevelRing(exp = snapshot.progress.exp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.level_label, snapshot.level),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        StatChip(emoji = "💰", text = "${snapshot.progress.money}", tint = StatColors.Money)
    }
}

@Composable
private fun SessionCard(snapshot: PetSnapshot, nowMillis: Long, onCancel: () -> Unit) {
    val session = snapshot.session ?: return
    val occupation = snapshot.occupation ?: return

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(occupationEmoji(occupation.id), style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(occupationNameRes(occupation.id)),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(
                            R.string.session_remaining,
                            formatRemaining(session.remainingMillis(nowMillis)),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                OutlinedButton(onClick = onCancel) {
                    Text(stringResource(R.string.action_call_home))
                }
            }
            LinearProgressIndicator(
                progress = { session.progress(nowMillis) },
                modifier = Modifier.fillMaxWidth(),
                drawStopIndicator = {},
            )
        }
    }
}

@Composable
private fun StatsCard(snapshot: PetSnapshot) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatBar(
                label = stringResource(R.string.stat_hunger),
                value = snapshot.stats.hunger,
                color = StatColors.Hunger,
            )
            StatBar(
                label = stringResource(R.string.stat_energy),
                value = snapshot.stats.energy,
                color = StatColors.Energy,
            )
            StatBar(
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
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CareButton(
            icon = Icons.Default.Restaurant,
            label = stringResource(R.string.action_feed),
            enabled = snapshot.canFeed(tuning),
            onClick = onFeed,
            modifier = Modifier.weight(1f),
        )
        CareButton(
            icon = if (snapshot.isSleeping) Icons.Default.WbSunny else Icons.Default.Bedtime,
            label = stringResource(
                if (snapshot.isSleeping) R.string.action_wake else R.string.action_sleep,
            ),
            enabled = !snapshot.isBusy,
            onClick = onToggleSleep,
            modifier = Modifier.weight(1f),
        )
        CareButton(
            icon = Icons.Default.Favorite,
            label = stringResource(R.string.action_pet),
            enabled = snapshot.acceptsInteraction,
            onClick = onPet,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun CareButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 8.dp,
            vertical = 12.dp,
        ),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(4.dp))
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun BubbleCard(
    bubbleEnabled: Boolean,
    overlayPermissionGranted: Boolean,
    onGrantOverlayPermission: () -> Unit,
    onBubbleEnabledChange: (Boolean) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.bubble_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.bubble_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = bubbleEnabled && overlayPermissionGranted,
                    onCheckedChange = onBubbleEnabledChange,
                    enabled = overlayPermissionGranted,
                )
            }

            if (!overlayPermissionGranted) {
                Text(
                    text = stringResource(R.string.overlay_permission_rationale),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Button(onClick = onGrantOverlayPermission) {
                    Text(stringResource(R.string.action_grant_overlay))
                }
            }
        }
    }
}
