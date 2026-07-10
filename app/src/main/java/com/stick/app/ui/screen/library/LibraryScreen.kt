package com.stick.app.ui.screen.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stick.app.R
import com.stick.app.ui.components.StickerCard

/**
 * The library home: a searchable, filterable, sortable grid/list of every saved
 * sticker with multi-select for batch delete and a de-duplicate action.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onOpenSticker: (String) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (state.inSelectionMode) {
                        Text("${state.selectedIds.size} selected")
                    } else {
                        Text(stringResource(R.string.nav_library))
                    }
                },
                actions = {
                    if (state.inSelectionMode) {
                        IconButton(onClick = { viewModel.deleteSelected() }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete selected")
                        }
                    } else {
                        IconButton(onClick = { viewModel.removeDuplicates() }) {
                            Icon(Icons.Filled.FilterList, contentDescription = "Remove duplicates")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            TextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                placeholder = { Text("Search stickers") },
                singleLine = true,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            Row(
                Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = state.filter == LibraryFilter.ALL,
                    onClick = { viewModel.onFilterChange(LibraryFilter.ALL) },
                    label = { Text("All") },
                )
                FilterChip(
                    selected = state.filter == LibraryFilter.FAVORITES,
                    onClick = { viewModel.onFilterChange(LibraryFilter.FAVORITES) },
                    label = { Text("Favorites") },
                )
            }

            if (state.stickers.isEmpty()) {
                EmptyLibrary()
            } else {
                // Card size follows the user's preferred scale (settings).
                val minCell = (110 * state.cardScale).dp
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minCell),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(state.stickers, key = { it.id }) { sticker ->
                        StickerCard(
                            model = sticker.localPath,
                            contentDescription = sticker.name,
                            isFavorite = sticker.isFavorite,
                            isSelected = sticker.id in state.selectedIds,
                            onClick = {
                                if (state.inSelectionMode) viewModel.toggleSelection(sticker.id)
                                else onOpenSticker(sticker.id)
                            },
                            onLongClick = { viewModel.toggleSelection(sticker.id) },
                            onToggleFavorite = { viewModel.toggleFavorite(sticker.id, sticker.isFavorite) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyLibrary() {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "No stickers yet",
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            "Import a TikTok link or browse the catalog to get started.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
