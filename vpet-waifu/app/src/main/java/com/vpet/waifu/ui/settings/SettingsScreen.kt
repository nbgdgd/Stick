package com.vpet.waifu.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import com.vpet.waifu.R
import com.vpet.waifu.data.PetPreferences
import com.vpet.waifu.data.PetSettings
import com.vpet.waifu.ui.components.PanelCard
import com.vpet.waifu.ui.components.PrimaryButton
import com.vpet.waifu.ui.theme.Accents
import com.vpet.waifu.ui.theme.StatColors
import com.vpet.waifu.ui.theme.Surfaces

/**
 * Settings — a place you go, not a card at the bottom of Home.
 *
 * Rendered as a full-screen overlay above the tab host; the system back
 * button closes it like any other screen.
 */
@Composable
fun SettingsScreen(
    settings: PetSettings,
    overlayPermissionGranted: Boolean,
    onGrantOverlayPermission: () -> Unit,
    onBubbleEnabledChange: (Boolean) -> Unit,
    onSoundChange: (Boolean) -> Unit,
    onMusicChange: (Boolean) -> Unit,
    onHapticsChange: (Boolean) -> Unit,
    onNotificationsChange: (Boolean) -> Unit,
    onNameChange: (String) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    var licenses by rememberSaveable { mutableStateOf(false) }
    if (licenses) {
        AlertDialog(
            onDismissRequest = { licenses = false },
            containerColor = Surfaces.Card,
            titleContentColor = Accents.Text,
            textContentColor = Accents.TextMuted,
            shape = RoundedCornerShape(24.dp),
            title = { Text(stringResource(R.string.licenses_title)) },
            text = {
                Text(
                    text = stringResource(R.string.licenses_body),
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            confirmButton = {
                PrimaryButton(text = stringResource(R.string.action_ok), onClick = { licenses = false })
            },
        )
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Surfaces.Screen) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Surfaces.Tile)
                        .border(1.dp, Surfaces.CardBorder, RoundedCornerShape(12.dp))
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = null,
                        tint = Accents.Text,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.settings_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = Accents.Text,
                )
            }

            PanelCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    NameField(settings.petName, onNameChange)
                }
            }

            PanelCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    SettingRow(
                        icon = Icons.Rounded.PictureInPictureAlt,
                        tint = StatColors.Energy,
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
                        tint = StatColors.Money,
                        title = stringResource(R.string.settings_notifications),
                        checked = settings.notificationsEnabled,
                        onCheckedChange = onNotificationsChange,
                    )
                    SettingRow(
                        icon = Icons.AutoMirrored.Rounded.VolumeUp,
                        tint = StatColors.Mood,
                        title = stringResource(R.string.settings_sound),
                        checked = settings.soundEnabled,
                        onCheckedChange = onSoundChange,
                    )
                    SettingRow(
                        icon = Icons.Rounded.MusicNote,
                        tint = StatColors.Exp,
                        title = stringResource(R.string.settings_music),
                        checked = settings.musicEnabled,
                        onCheckedChange = onMusicChange,
                    )
                    SettingRow(
                        icon = Icons.Rounded.Vibration,
                        tint = StatColors.Hunger,
                        title = stringResource(R.string.settings_haptics),
                        checked = settings.hapticsEnabled,
                        onCheckedChange = onHapticsChange,
                    )
                }
            }

            PanelCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { licenses = true }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Description,
                        contentDescription = null,
                        tint = Accents.TextMuted,
                        modifier = Modifier.size(19.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.licenses_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = Accents.Text,
                    )
                }
            }
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
            leadingIcon = {
                Icon(Icons.Rounded.Badge, contentDescription = null, tint = Accents.TextMuted)
            },
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
        if (dirty) {
            Spacer(Modifier.width(10.dp))
            PrimaryButton(
                text = stringResource(R.string.action_save),
                onClick = { onNameChange(draft.trim()) },
            )
        }
    }
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    tint: Color,
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
                .clip(RoundedCornerShape(12.dp))
                .background(tint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(19.dp))
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
