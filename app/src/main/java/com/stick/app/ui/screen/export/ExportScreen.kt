package com.stick.app.ui.screen.export

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stick.app.R
import com.stick.app.domain.TelegramExporter
import com.stick.app.ui.Formatters
import com.stick.core.model.StickerFormat
import java.io.File

/**
 * Export screen: pick a target format and settings, watch the estimated size
 * update live, run the conversion, then quick-share to Telegram.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExportScreen(
    stickerId: String,
    onBack: () -> Unit,
    viewModel: ExportViewModel = hiltViewModel(),
) {
    LaunchedEffect(stickerId) { viewModel.load(stickerId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.export_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Format", style = MaterialTheme.typography.titleLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StickerFormat.exportTargets.forEach { format ->
                    FilterChip(
                        selected = state.options.format == format,
                        onClick = { viewModel.selectFormat(format) },
                        label = { Text(format.name.removePrefix("TELEGRAM_")) },
                    )
                }
            }

            // Settings
            LabeledSlider("Size", state.options.widthPx.toFloat(), 128f..512f) {
                viewModel.setSize(it.toInt())
            }
            LabeledSlider("FPS", state.options.fps.toFloat(), 5f..60f) {
                viewModel.setFps(it.toInt())
            }
            LabeledSlider("Quality", state.options.quality.toFloat(), 1f..100f) {
                viewModel.setQuality(it.toInt())
            }

            // Live estimate
            Text(
                stringResource(R.string.export_estimated_size, Formatters.bytes(state.estimatedSizeBytes)),
                style = MaterialTheme.typography.bodyLarge,
            )

            if (state.isExporting) {
                LinearProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            Button(
                onClick = { viewModel.export() },
                enabled = !state.isExporting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.export_save))
            }

            // Quick Telegram actions, enabled once an output file exists.
            val output = state.outputPath
            if (output != null) {
                Text(
                    "Exported (${Formatters.bytes(state.estimatedSizeBytes)})",
                    color = MaterialTheme.colorScheme.primary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { TelegramExporter.share(context, listOf(File(output))) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.export_send_telegram))
                    }
                    OutlinedButton(
                        onClick = { TelegramExporter.openStickerBot(context) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.export_open_sticker_bot))
                    }
                }
            }
        }
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label)
            Text(value.toInt().toString())
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = range)
    }
}
