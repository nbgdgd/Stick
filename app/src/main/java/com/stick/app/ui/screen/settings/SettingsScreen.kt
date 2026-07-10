package com.stick.app.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stick.app.R
import com.stick.app.data.repository.PreviewQuality
import com.stick.app.ui.theme.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_settings)) }) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // Theme -----------------------------------------------------------
            Section(stringResource(R.string.settings_theme)) {
                Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeChip(R.string.settings_theme_system, ThemeMode.SYSTEM, settings.themeMode, viewModel::setTheme)
                    ThemeChip(R.string.settings_theme_light, ThemeMode.LIGHT, settings.themeMode, viewModel::setTheme)
                    ThemeChip(R.string.settings_theme_dark, ThemeMode.DARK, settings.themeMode, viewModel::setTheme)
                    ThemeChip(R.string.settings_theme_amoled, ThemeMode.AMOLED, settings.themeMode, viewModel::setTheme)
                }
                SwitchRow(
                    label = stringResource(R.string.settings_dynamic_color),
                    checked = settings.dynamicColor,
                    onCheckedChange = viewModel::setDynamicColor,
                )
            }

            // Layout ----------------------------------------------------------
            Section(stringResource(R.string.settings_layout)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = settings.gridLayout,
                        onClick = { viewModel.setGridLayout(true) },
                        label = { Text(stringResource(R.string.settings_layout_grid)) },
                    )
                    FilterChip(
                        selected = !settings.gridLayout,
                        onClick = { viewModel.setGridLayout(false) },
                        label = { Text(stringResource(R.string.settings_layout_list)) },
                    )
                }
                Text(stringResource(R.string.settings_card_size))
                Slider(
                    value = settings.cardScale,
                    onValueChange = viewModel::setCardScale,
                    valueRange = 0.7f..1.6f,
                )
            }

            // Preview quality -------------------------------------------------
            Section(stringResource(R.string.settings_preview_quality)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PreviewQuality.entries.forEach { q ->
                        FilterChip(
                            selected = settings.previewQuality == q,
                            onClick = { viewModel.setPreviewQuality(q) },
                            label = { Text(q.name.lowercase().replaceFirstChar { it.uppercase() }) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        content()
    }
}

@Composable
private fun ThemeChip(
    labelRes: Int,
    mode: ThemeMode,
    current: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
) {
    FilterChip(
        selected = current == mode,
        onClick = { onSelect(mode) },
        label = { Text(stringResource(labelRes)) },
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
