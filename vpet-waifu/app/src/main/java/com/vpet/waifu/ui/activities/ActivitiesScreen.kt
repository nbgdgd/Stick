package com.vpet.waifu.ui.activities

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vpet.waifu.R
import com.vpet.waifu.domain.Occupation
import com.vpet.waifu.domain.OccupationKind
import com.vpet.waifu.domain.Occupations
import com.vpet.waifu.domain.PetSimulation
import com.vpet.waifu.domain.PetSnapshot
import com.vpet.waifu.domain.PetTuning
import com.vpet.waifu.ui.components.StatChip
import com.vpet.waifu.ui.formatRemaining
import com.vpet.waifu.ui.occupationEmoji
import com.vpet.waifu.ui.occupationNameRes
import com.vpet.waifu.ui.theme.StatColors

/**
 * Work and study.
 *
 * Each card shows what the session actually pays *right now* — the mood
 * multiplier is applied to the preview, so a sad pet visibly earns less before
 * the player commits to a two-hour shift.
 */
@Composable
fun ActivitiesScreen(
    snapshot: PetSnapshot,
    simulation: PetSimulation,
    tuning: PetTuning,
    nowMillis: Long,
    onStart: (Occupation) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (snapshot.isBusy) {
            item { ActiveSession(snapshot, nowMillis, onCancel) }
        } else if (snapshot.stats.energy < tuning.minimumEnergyToWork) {
            item { Notice(stringResource(R.string.too_tired_to_work)) }
        }

        item { SectionTitle(stringResource(R.string.section_work), "💼") }
        items(Occupations.WORK, key = { it.id }) { occupation ->
            OccupationCard(occupation, snapshot, simulation, tuning, onStart)
        }

        item { SectionTitle(stringResource(R.string.section_study), "📚") }
        items(Occupations.STUDY, key = { it.id }) { occupation ->
            OccupationCard(occupation, snapshot, simulation, tuning, onStart)
        }
    }
}

@Composable
private fun SectionTitle(text: String, emoji: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
        Text(emoji, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Notice(text: String) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun ActiveSession(snapshot: PetSnapshot, nowMillis: Long, onCancel: () -> Unit) {
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
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(occupationEmoji(occupation.id), style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(occupationNameRes(occupation.id)),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        stringResource(
                            R.string.session_remaining,
                            formatRemaining(session.remainingMillis(nowMillis)),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            LinearProgressIndicator(
                progress = { session.progress(nowMillis) },
                modifier = Modifier.fillMaxWidth(),
                drawStopIndicator = {},
            )
            OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_call_home))
            }
        }
    }
}

@Composable
private fun OccupationCard(
    occupation: Occupation,
    snapshot: PetSnapshot,
    simulation: PetSimulation,
    tuning: PetTuning,
    onStart: (Occupation) -> Unit,
) {
    val unlocked = occupation.isUnlocked(snapshot.level)
    val canStart = snapshot.canStart(occupation, tuning)
    val payout = simulation.projectedPayout(snapshot, occupation)
    val isWork = occupation.kind == OccupationKind.WORK

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (unlocked) occupationEmoji(occupation.id) else "🔒",
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(occupationNameRes(occupation.id)),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(
                        R.string.occupation_cost,
                        occupation.durationMinutes,
                        occupation.energyCost.toInt(),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(6.dp))
                if (unlocked) {
                    StatChip(
                        emoji = if (isWork) "💰" else "⭐",
                        text = "+$payout",
                        tint = if (isWork) StatColors.Money else StatColors.Exp,
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.unlocks_at_level, occupation.requiredLevel),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Button(onClick = { onStart(occupation) }, enabled = canStart) {
                Text(stringResource(R.string.action_send))
            }
        }
    }
}
