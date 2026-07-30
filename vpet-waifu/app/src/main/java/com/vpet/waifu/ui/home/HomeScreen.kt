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
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Paid
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vpet.waifu.R
import com.vpet.waifu.data.PetPreferences
import com.vpet.waifu.data.PetSettings
import com.vpet.waifu.domain.Dialogue
import com.vpet.waifu.domain.OccupationKind
import com.vpet.waifu.domain.PetSnapshot
import com.vpet.waifu.domain.PetState
import com.vpet.waifu.domain.PetTuning
import com.vpet.waifu.ui.components.ActionButton
import com.vpet.waifu.ui.character.PetPalette
import com.vpet.waifu.ui.components.EffectChip
import com.vpet.waifu.ui.components.EventCard
import com.vpet.waifu.ui.components.GainPop
import com.vpet.waifu.ui.components.LevelRing
import com.vpet.waifu.ui.components.MoneyPill
import com.vpet.waifu.ui.components.OutlineButton
import com.vpet.waifu.ui.components.PanelCard
import com.vpet.waifu.ui.components.PetStage
import com.vpet.waifu.ui.components.PrimaryButton
import com.vpet.waifu.ui.components.StatBarTrack
import com.vpet.waifu.ui.components.StatRow
import com.vpet.waifu.ui.components.SpeechBubble
import com.vpet.waifu.ui.components.StatusChip
import com.vpet.waifu.ui.dialogueRes
import com.vpet.waifu.ui.formatRemaining
import com.vpet.waifu.ui.occupationIcon
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
    settings: PetSettings,
    overlayPermissionGranted: Boolean,
    onGrantOverlayPermission: () -> Unit,
    onBubbleEnabledChange: (Boolean) -> Unit,
    onSoundChange: (Boolean) -> Unit,
    onHapticsChange: (Boolean) -> Unit,
    onNotificationsChange: (Boolean) -> Unit,
    onNameChange: (String) -> Unit,
    onFeed: () -> Unit,
    onPet: () -> Unit,
    onToggleSleep: () -> Unit,
    onCancelOccupation: () -> Unit,
    onDismissEvent: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = snapshot.state(nowMillis, tuning)
    val line = remember(snapshot, nowMillis / 5_000L) {
        Dialogue.lineFor(snapshot, nowMillis, tuning)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Header(snapshot, settings.petName)

        Box {
            PetStage(
                state = state,
                height = 320.dp,
                palette = PetPalette.forOutfit(snapshot.outfit),
            )
            // Bottom-left, over the floor: the one region of the room that
            // never has her or the furniture behind text.
            StatusChip(
                text = stringResource(stateLabelRes(state)),
                dot = statusDot(state),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp),
            )
            // She talks — in the sky band above her head. The first cut hung
            // the bubble at her eye level and it sat straight across her face.
            SpeechBubble(
                text = stringResource(dialogueRes(line)),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp, start = 16.dp, end = 16.dp),
            )
            // Every minute she is on the clock, what she just earned floats up.
            // Top-right corner: visible, and never over her.
            snapshot.session?.let { session ->
                val isWork = snapshot.occupation?.kind == OccupationKind.WORK
                GainPop(
                    total = if (isWork) session.paidOut else session.paidExp,
                    label = if (isWork) "¥" else "EXP",
                    tint = if (isWork) StatColors.Money else StatColors.Exp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 84.dp, end = 18.dp),
                )
            }
        }

        snapshot.event?.takeIf { !it.acknowledged }?.let { event ->
            EventCard(event, onDismissEvent)
        }

        AnimatedVisibility(visible = snapshot.isBusy) {
            SessionCard(snapshot, nowMillis, onCancelOccupation)
        }

        StatsCard(snapshot)
        CareRow(snapshot, tuning, onFeed, onPet, onToggleSleep)
        SettingsCard(
            settings = settings,
            overlayPermissionGranted = overlayPermissionGranted,
            onGrantOverlayPermission = onGrantOverlayPermission,
            onBubbleEnabledChange = onBubbleEnabledChange,
            onSoundChange = onSoundChange,
            onHapticsChange = onHapticsChange,
            onNotificationsChange = onNotificationsChange,
            onNameChange = onNameChange,
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
private fun Header(snapshot: PetSnapshot, petName: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LevelRing(exp = snapshot.progress.exp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = petName.ifBlank { stringResource(R.string.app_name) },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Accents.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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
                    Icon(
                        imageVector = occupationIcon(occupation.id),
                        contentDescription = null,
                        tint = Accents.Bright,
                        modifier = Modifier.size(22.dp),
                    )
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
                    icon = if (occupation.kind == OccupationKind.WORK) Icons.Rounded.Paid else Icons.Rounded.Star,
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
                icon = Icons.Rounded.Restaurant,
                label = stringResource(R.string.stat_hunger),
                value = snapshot.stats.hunger,
                color = StatColors.Hunger,
            )
            StatRow(
                icon = Icons.Rounded.Bolt,
                label = stringResource(R.string.stat_energy),
                value = snapshot.stats.energy,
                color = StatColors.Energy,
            )
            StatRow(
                icon = Icons.Rounded.Favorite,
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
private fun SettingsCard(
    settings: PetSettings,
    overlayPermissionGranted: Boolean,
    onGrantOverlayPermission: () -> Unit,
    onBubbleEnabledChange: (Boolean) -> Unit,
    onSoundChange: (Boolean) -> Unit,
    onHapticsChange: (Boolean) -> Unit,
    onNotificationsChange: (Boolean) -> Unit,
    onNameChange: (String) -> Unit,
) {
    PanelCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            NameField(settings.petName, onNameChange)

            SettingRow(
                icon = Icons.Rounded.PictureInPictureAlt,
                title = stringResource(R.string.bubble_title),
                subtitle = stringResource(R.string.bubble_subtitle),
                checked = settings.bubbleEnabled && overlayPermissionGranted,
                enabled = overlayPermissionGranted,
                onCheckedChange = onBubbleEnabledChange,
            )

            if (!overlayPermissionGranted) {
                Text(
                    text = stringResource(R.string.overlay_permission_rationale),
                    style = MaterialTheme.typography.bodySmall,
                    color = Accents.Danger,
                )
                PrimaryButton(
                    text = stringResource(R.string.action_grant_overlay),
                    onClick = onGrantOverlayPermission,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            SettingRow(
                icon = Icons.Rounded.Notifications,
                title = stringResource(R.string.settings_notifications),
                checked = settings.notificationsEnabled,
                onCheckedChange = onNotificationsChange,
            )
            SettingRow(
                icon = Icons.AutoMirrored.Rounded.VolumeUp,
                title = stringResource(R.string.settings_sound),
                checked = settings.soundEnabled,
                onCheckedChange = onSoundChange,
            )
            SettingRow(
                icon = Icons.Rounded.Vibration,
                title = stringResource(R.string.settings_haptics),
                checked = settings.hapticsEnabled,
                onCheckedChange = onHapticsChange,
            )
        }
    }
}

/**
 * Naming her.
 *
 * Held locally while it is being typed and committed on the button, rather than
 * written on every keystroke: the name reaches notifications and the header, and
 * watching those redraw letter by letter is unpleasant.
 */
@Composable
private fun NameField(current: String, onNameChange: (String) -> Unit) {
    var draft by rememberSaveable(current) { mutableStateOf(current) }
    val dirty = draft.trim() != current

    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = draft,
            onValueChange = { if (it.length <= PetPreferences.MAX_NAME_LENGTH) draft = it },
            modifier = Modifier.weight(1f),
            singleLine = true,
            label = { Text(stringResource(R.string.name_title)) },
            placeholder = { Text(stringResource(R.string.name_hint)) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Accents.Text,
                unfocusedTextColor = Accents.Text,
                focusedBorderColor = Accents.Primary,
                unfocusedBorderColor = Surfaces.Divider,
                focusedLabelColor = Accents.Primary,
                unfocusedLabelColor = Accents.TextDim,
                cursorColor = Accents.Bright,
            ),
        )
        AnimatedVisibility(visible = dirty) {
            Row {
                Spacer(Modifier.width(10.dp))
                PrimaryButton(
                    text = stringResource(R.string.action_save),
                    onClick = { onNameChange(draft) },
                )
            }
        }
    }
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(Accents.Primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Accents.Bright, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = Accents.Text,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Accents.TextMuted,
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Accents.Primary,
                uncheckedTrackColor = Surfaces.Track,
                uncheckedBorderColor = Surfaces.Divider,
            ),
        )
    }
}
