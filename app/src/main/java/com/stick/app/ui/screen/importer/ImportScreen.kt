package com.stick.app.ui.screen.importer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stick.app.R
import com.stick.app.ui.components.StickerCard

/**
 * Import screen: paste/receive a TikTok link, scan its comments for animated
 * stickers, preview and multi-select them, then batch-download into the library.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    initialLink: String?,
    onOpenSticker: (String) -> Unit,
    viewModel: ImportViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // System document picker for "Import a file". OpenDocument shows the file
    // browser and returns a content:// Uri the ViewModel copies into app storage.
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) viewModel.importLocalFile(uri) { savedId -> onOpenSticker(savedId) }
    }

    // Auto-start when the app was opened from a shared TikTok link.
    LaunchedEffect(initialLink) {
        if (!initialLink.isNullOrBlank()) {
            viewModel.onInputChange(initialLink)
            viewModel.startScan(initialLink)
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.import_title)) }) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            TextField(
                value = state.input,
                onValueChange = viewModel::onInputChange,
                placeholder = { Text(stringResource(R.string.import_link_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )

            Button(
                onClick = { viewModel.startScan() },
                enabled = state.input.isNotBlank() && !state.isScanning && !state.isResolving,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            ) {
                Text(stringResource(R.string.import_from_link))
            }

            OutlinedButton(
                onClick = {
                    // Any image plus common animation containers.
                    filePicker.launch(arrayOf("image/*", "video/mp4", "application/octet-stream"))
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(stringResource(R.string.import_from_file))
            }

            if (state.isResolving || state.isScanning) {
                Text(
                    stringResource(R.string.import_scanning),
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            state.error?.let { error ->
                Text(
                    error,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp),
                )
            }

            if (state.discovered.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("${state.discovered.size} found")
                    TextButton(onClick = { viewModel.selectAll(true) }) {
                        Text(stringResource(R.string.import_select_all))
                    }
                }

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(110.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    itemsIndexed(state.discovered) { index, item ->
                        StickerCard(
                            model = item.sticker.previewUrl,
                            contentDescription = item.sticker.name,
                            isFavorite = false,
                            isSelected = item.selected,
                            onClick = { viewModel.toggle(index) },
                            onLongClick = { viewModel.toggle(index) },
                            onToggleFavorite = { viewModel.toggle(index) },
                        )
                    }
                }

                if (state.isDownloading) {
                    LinearProgressIndicator(
                        progress = { state.downloadProgress },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    )
                }

                Button(
                    onClick = { viewModel.downloadSelected() },
                    enabled = state.selectedCount > 0 && !state.isDownloading,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                ) {
                    Text(stringResource(R.string.import_download_selected, state.selectedCount))
                }
            } else if (!state.isScanning && !state.isResolving) {
                // Distinguish "haven't scanned yet" from "scanned, found nothing".
                if (state.resolved != null && state.error == null) {
                    EmptyResult()
                } else {
                    ImportHint()
                }
            }
        }
    }
}

@Composable
private fun EmptyResult() {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "No animated stickers were found in this video's comments. " +
                "Try another video — not every video has sticker comments.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ImportHint() {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Paste a TikTok video link, or share one to Stick, to find animated " +
                "stickers in its comments.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
