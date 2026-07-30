package com.vpet.waifu.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vpet.waifu.R
import com.vpet.waifu.domain.PetSnapshot
import com.vpet.waifu.domain.PetTuning
import com.vpet.waifu.ui.components.StatBar
import com.vpet.waifu.ui.labelRes
import com.vpet.waifu.ui.spriteRes
import com.vpet.waifu.ui.theme.StatColors

/**
 * The "home" of the pet: the same three stats and the same actions the bubble
 * offers, plus the permission plumbing the bubble cannot ask for itself.
 */
@Composable
fun HomeScreen(
    state: HomeUiState,
    tuning: PetTuning,
    overlayPermissionGranted: Boolean,
    onGrantOverlayPermission: () -> Unit,
    onFeed: () -> Unit,
    onPet: () -> Unit,
    onToggleSleep: () -> Unit,
    onBubbleEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val snapshot = state.snapshot ?: return

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PetPortrait(snapshot, tuning)
        StatsCard(snapshot)
        ActionRow(snapshot, tuning, onFeed, onPet, onToggleSleep)
        BubbleCard(
            bubbleEnabled = state.bubbleEnabled,
            overlayPermissionGranted = overlayPermissionGranted,
            onGrantOverlayPermission = onGrantOverlayPermission,
            onBubbleEnabledChange = onBubbleEnabledChange,
        )
    }
}

@Composable
private fun PetPortrait(snapshot: PetSnapshot, tuning: PetTuning) {
    val petState = snapshot.state(tuning)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Image(
            painter = painterResource(petState.spriteRes()),
            contentDescription = stringResource(petState.labelRes()),
            modifier = Modifier.size(200.dp),
        )
        Text(
            text = stringResource(petState.labelRes()),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
private fun ActionRow(
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
        ActionButton(
            icon = Icons.Default.Restaurant,
            label = stringResource(R.string.action_feed),
            enabled = snapshot.canFeed(tuning),
            onClick = onFeed,
            modifier = Modifier.weight(1f),
        )
        ActionButton(
            icon = if (snapshot.isSleeping) Icons.Default.WbSunny else Icons.Default.Bedtime,
            label = stringResource(
                if (snapshot.isSleeping) R.string.action_wake else R.string.action_sleep,
            ),
            enabled = true,
            onClick = onToggleSleep,
            modifier = Modifier.weight(1f),
        )
        ActionButton(
            icon = Icons.Default.Favorite,
            label = stringResource(R.string.action_pet),
            enabled = snapshot.acceptsInteraction,
            onClick = onPet,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ActionButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilledTonalButton(onClick = onClick, enabled = enabled, modifier = modifier) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge)
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
                horizontalArrangement = Arrangement.SpaceBetween,
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
            } else {
                OutlinedButton(
                    onClick = { onBubbleEnabledChange(!bubbleEnabled) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(
                            if (bubbleEnabled) R.string.action_hide_bubble
                            else R.string.action_show_bubble,
                        ),
                    )
                }
            }
        }
    }
}
