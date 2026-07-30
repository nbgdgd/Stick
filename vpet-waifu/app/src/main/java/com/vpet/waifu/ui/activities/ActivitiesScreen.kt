package com.vpet.waifu.ui.activities

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vpet.waifu.R
import com.vpet.waifu.domain.Occupation
import com.vpet.waifu.domain.OccupationKind
import com.vpet.waifu.domain.Occupations
import com.vpet.waifu.domain.PetSimulation
import com.vpet.waifu.domain.PetSnapshot
import com.vpet.waifu.domain.PetTuning
import com.vpet.waifu.ui.components.EffectChip
import com.vpet.waifu.ui.components.EmojiTile
import com.vpet.waifu.ui.components.MoneyPill
import com.vpet.waifu.ui.components.OutlineButton
import com.vpet.waifu.ui.components.PanelCard
import com.vpet.waifu.ui.components.PrimaryButton
import com.vpet.waifu.ui.components.ScreenTitle
import com.vpet.waifu.ui.components.SectionHeader
import com.vpet.waifu.ui.components.StatBarTrack
import com.vpet.waifu.ui.formatRemaining
import com.vpet.waifu.ui.occupationEmoji
import com.vpet.waifu.ui.occupationNameRes
import com.vpet.waifu.ui.theme.Accents
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
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            ScreenTitle(stringResource(R.string.tab_activities)) {
                MoneyPill(amount = snapshot.progress.money)
            }
        }

        if (snapshot.isBusy) {
            item { ActiveSession(snapshot, nowMillis, onCancel) }
        } else if (snapshot.stats.energy < tuning.minimumEnergyToWork) {
            item { Notice(stringResource(R.string.too_tired_to_work)) }
        }

        item { SectionHeader("💼", stringResource(R.string.section_work)) }
        items(Occupations.WORK, key = { it.id }) { occupation ->
            OccupationCard(occupation, snapshot, simulation, tuning, onStart, Modifier.animateItem())
        }

        item { SectionHeader("📚", stringResource(R.string.section_study)) }
        items(Occupations.STUDY, key = { it.id }) { occupation ->
            OccupationCard(occupation, snapshot, simulation, tuning, onStart, Modifier.animateItem())
        }
    }
}

@Composable
private fun Notice(text: String) {
    PanelCard(
        modifier = Modifier.fillMaxWidth(),
        color = Accents.Danger.copy(alpha = 0.12f),
        border = Accents.Danger.copy(alpha = 0.4f),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("😵", fontSize = 20.sp)
            Spacer(Modifier.width(12.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = Accents.Text,
            )
        }
    }
}

@Composable
private fun ActiveSession(snapshot: PetSnapshot, nowMillis: Long, onCancel: () -> Unit) {
    val session = snapshot.session ?: return
    val occupation = snapshot.occupation ?: return

    PanelCard(
        modifier = Modifier.fillMaxWidth(),
        border = Accents.Primary.copy(alpha = 0.45f),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(Accents.Primary.copy(alpha = 0.16f)),
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
            }
            Spacer(Modifier.height(14.dp))
            StatBarTrack(fraction = session.progress(nowMillis), color = Accents.Bright, height = 7.dp)
            Spacer(Modifier.height(14.dp))
            OutlineButton(
                text = stringResource(R.string.action_call_home),
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * One job.
 *
 * The chips live on their own full-width row under the title rather than
 * beside it: three of them plus a button in a single row left barely forty
 * points for the last chip, so "−16" was clipped mid-character on a narrow
 * phone. Below the title they get the whole card and wrap if they need to.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OccupationCard(
    occupation: Occupation,
    snapshot: PetSnapshot,
    simulation: PetSimulation,
    tuning: PetTuning,
    onStart: (Occupation) -> Unit,
    modifier: Modifier = Modifier,
) {
    val unlocked = occupation.isUnlocked(snapshot.level)
    val canStart = snapshot.canStart(occupation, tuning)
    val payout = simulation.projectedPayout(snapshot, occupation)
    val isWork = occupation.kind == OccupationKind.WORK

    PanelCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                EmojiTile(
                    emoji = if (unlocked) occupationEmoji(occupation.id) else "🔒",
                    tint = if (isWork) StatColors.Money else StatColors.Exp,
                    size = 54.dp,
                )
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(occupationNameRes(occupation.id)),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Accents.Text,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (unlocked) {
                            stringResource(R.string.duration_minutes, occupation.durationMinutes)
                        } else {
                            stringResource(R.string.unlocks_at_level, occupation.requiredLevel)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (unlocked) Accents.TextMuted else Accents.Danger,
                    )
                }
                Spacer(Modifier.width(10.dp))
                PrimaryButton(
                    text = stringResource(R.string.action_send),
                    onClick = { onStart(occupation) },
                    enabled = canStart,
                    minWidth = 96.dp,
                )
            }

            if (unlocked) {
                Spacer(Modifier.height(12.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    EffectChip(
                        emoji = if (isWork) "💰" else "⭐",
                        text = "+$payout",
                        tint = if (isWork) StatColors.Money else StatColors.Exp,
                    )
                    EffectChip(
                        emoji = "⚡",
                        text = "−${occupation.energyCost.toInt()}",
                        tint = StatColors.Energy,
                    )
                    EffectChip(
                        emoji = "💜",
                        text = stringResource(R.string.pay_scales_with_mood),
                        tint = StatColors.Mood,
                    )
                }
            }
        }
    }
}
