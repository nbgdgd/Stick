package com.stick.app.ui.screen.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stick.app.data.database.entity.StickerEntity
import com.stick.app.data.repository.SettingsRepository
import com.stick.app.data.repository.StickerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class LibraryFilter { ALL, FAVORITES }
enum class LibrarySort { NEWEST, NAME, SIZE }

data class LibraryUiState(
    val stickers: List<StickerEntity> = emptyList(),
    val query: String = "",
    val filter: LibraryFilter = LibraryFilter.ALL,
    val sort: LibrarySort = LibrarySort.NEWEST,
    val gridLayout: Boolean = true,
    val cardScale: Float = 1f,
    val selectedIds: Set<String> = emptySet(),
) {
    val inSelectionMode: Boolean get() = selectedIds.isNotEmpty()
}

/**
 * Drives the library grid/list. Combines persisted stickers with the live query,
 * filter and sort so the list reacts to any of them without imperative reloads.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: StickerRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(LibraryFilter.ALL)
    private val sort = MutableStateFlow(LibrarySort.NEWEST)
    private val selectedIds = MutableStateFlow<Set<String>>(emptySet())

    private val stickers = combine(query, filter) { q, f -> q to f }
        .flatMapLatest { (q, f) ->
            when {
                q.isNotBlank() -> repository.search(q)
                f == LibraryFilter.FAVORITES -> repository.observeFavorites()
                else -> repository.observeAll()
            }
        }

    val uiState: StateFlow<LibraryUiState> = combine(
        stickers, query, filter, sort, selectedIds,
    ) { list, q, f, s, selected ->
        LibraryUiState(
            stickers = list.sortedWith(comparatorFor(s)),
            query = q,
            filter = f,
            sort = s,
            selectedIds = selected,
        )
    }.combine(settingsRepository.settings) { state, settings ->
        state.copy(gridLayout = settings.gridLayout, cardScale = settings.cardScale)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    fun onQueryChange(value: String) { query.value = value }
    fun onFilterChange(value: LibraryFilter) { filter.value = value }
    fun onSortChange(value: LibrarySort) { sort.value = value }

    fun toggleFavorite(id: String, current: Boolean) = viewModelScope.launch {
        repository.setFavorite(id, !current)
    }

    fun toggleSelection(id: String) {
        selectedIds.value = selectedIds.value.let { if (id in it) it - id else it + id }
    }

    fun clearSelection() { selectedIds.value = emptySet() }

    fun deleteSelected() = viewModelScope.launch {
        selectedIds.value.forEach { repository.delete(it) }
        selectedIds.value = emptySet()
    }

    fun removeDuplicates() = viewModelScope.launch { repository.removeDuplicates() }

    private fun comparatorFor(sort: LibrarySort): Comparator<StickerEntity> = when (sort) {
        LibrarySort.NEWEST -> compareByDescending { it.createdAtEpochMs }
        LibrarySort.NAME -> compareBy { it.name.lowercase() }
        LibrarySort.SIZE -> compareByDescending { it.fileSizeBytes }
    }
}
