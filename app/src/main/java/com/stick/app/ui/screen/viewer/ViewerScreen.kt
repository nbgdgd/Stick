package com.stick.app.ui.screen.viewer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stick.app.R
import com.stick.app.ui.Formatters
import com.stick.app.ui.components.AnimatedStickerImage

/**
 * Full-screen sticker preview with play/pause, frame stepping, and a metadata
 * panel (FPS / resolution / size / duration / frames). Routes on to the editor
 * and exporter.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(
    stickerId: String,
    onEdit: () -> Unit,
    onExport: () -> Unit,
    onBack: () -> Unit,
    viewModel: ViewerViewModel = hiltViewModel(),
) {
    LaunchedEffect(stickerId) { viewModel.load(stickerId) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.sticker?.name ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.editor_title))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedStickerImage(
                    model = state.sticker?.localPath,
                    contentDescription = state.sticker?.name,
                    play = state.playing,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // Playback controls
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { viewModel.stepFrame(-1) }) {
                    Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous frame")
                }
                IconButton(onClick = { viewModel.togglePlay() }) {
                    Icon(
                        if (state.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = "Play/pause",
                    )
                }
                IconButton(onClick = { viewModel.stepFrame(1) }) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "Next frame")
                }
            }

            // Metadata grid
            MetadataRow(stringResource(R.string.viewer_fps), Formatters.fps(state.info.fps))
            MetadataRow(stringResource(R.string.viewer_resolution), state.info.resolutionLabel)
            MetadataRow(stringResource(R.string.viewer_size), Formatters.bytes(state.info.fileSizeBytes))
            MetadataRow(stringResource(R.string.viewer_duration), Formatters.duration(state.info.durationMs))
            MetadataRow(stringResource(R.string.viewer_frames), state.info.frameCount.toString())

            Button(onClick = onExport, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.export_title))
            }
        }
    }
}

@Composable
private fun MetadataRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
