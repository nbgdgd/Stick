package com.stick.app.ui.screen.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.Slider
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stick.app.R
import com.stick.app.domain.converter.EditOperation
import com.stick.app.ui.components.AnimatedStickerImage

/**
 * The built-in editor. Presents the non-destructive edit operations as quick
 * chips plus sliders for continuous values. Every action updates the pipeline in
 * the ViewModel; nothing is rendered until export.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    stickerId: String,
    onExport: () -> Unit,
    onBack: () -> Unit,
    viewModel: EditorViewModel = hiltViewModel(),
) {
    LaunchedEffect(stickerId) { viewModel.load(stickerId) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text(stringResource(R.string.editor_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.undo() }, enabled = state.canUndo) {
                        Icon(Icons.Filled.Undo, contentDescription = "Undo")
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
            AnimatedStickerImage(
                model = state.sticker?.localPath,
                contentDescription = state.sticker?.name,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
            )

            // Discrete operations as chips.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = { viewModel.apply(EditOperation.Rotate(90), replace = false) },
                    label = { Text(stringResource(R.string.editor_rotate)) },
                )
                AssistChip(
                    onClick = { viewModel.apply(EditOperation.Flip(horizontal = true, vertical = false)) },
                    label = { Text(stringResource(R.string.editor_flip)) },
                )
                AssistChip(
                    onClick = { viewModel.apply(EditOperation.CenterContent, replace = false) },
                    label = { Text("Center") },
                )
                AssistChip(
                    onClick = { viewModel.apply(EditOperation.RemoveBackground, replace = false) },
                    label = { Text(stringResource(R.string.editor_remove_bg)) },
                )
            }

            // Continuous operations as sliders.
            SpeedSlider { viewModel.apply(EditOperation.Speed(it)) }
            OpacitySlider { viewModel.apply(EditOperation.Opacity(it)) }
            FpsSlider { viewModel.apply(EditOperation.Fps(it)) }

            Button(onClick = onExport, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.export_title))
            }
        }
    }
}

@Composable
private fun SpeedSlider(onChange: (Float) -> Unit) {
    var value by remember { mutableFloatStateOf(1f) }
    LabeledSlider(stringResource(R.string.editor_speed), value, 0.25f..4f, "%.2fx".format(value)) {
        value = it; onChange(it)
    }
}

@Composable
private fun OpacitySlider(onChange: (Float) -> Unit) {
    var value by remember { mutableFloatStateOf(1f) }
    LabeledSlider(stringResource(R.string.editor_opacity), value, 0f..1f, "${(value * 100).toInt()}%") {
        value = it; onChange(it)
    }
}

@Composable
private fun FpsSlider(onChange: (Int) -> Unit) {
    var value by remember { mutableFloatStateOf(30f) }
    LabeledSlider(stringResource(R.string.editor_fps), value, 5f..60f, "${value.toInt()}") {
        value = it; onChange(it.toInt())
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueLabel: String,
    onValueChange: (Float) -> Unit,
) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label)
            Text(valueLabel)
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = range)
    }
}
