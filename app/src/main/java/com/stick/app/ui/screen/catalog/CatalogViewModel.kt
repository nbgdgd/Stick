package com.stick.app.ui.screen.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stick.app.domain.usecase.ImportStickersUseCase
import com.stick.core.model.CatalogQuery
import com.stick.core.model.RemoteSticker
import com.stick.core.result.StickResult
import com.stick.stickersource.StickerSource
import com.stick.stickersource.StickerSourceRegistry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CatalogUiState(
    val query: String = "",
    val results: List<RemoteSticker> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val savingId: String? = null,
) {
    val available: Boolean get() = true
}

/**
 * Powers the built-in TikTok sticker catalog: keyword search over the
 * comment-sticker library with one-tap save. Search is debounced so typing
 * doesn't hammer the source. The catalog capability is optional — if no
 * registered source advertises [StickerSource.Capability.SEARCH_CATALOG] the
 * screen shows an unavailable state.
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class CatalogViewModel @Inject constructor(
    private val registry: StickerSourceRegistry,
    private val importStickers: ImportStickersUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(CatalogUiState())
    val state: StateFlow<CatalogUiState> = _state.asStateFlow()

    private val queryFlow = MutableStateFlow("")

    private val catalogSource: StickerSource?
        get() = registry.primaryFor(StickerSource.Capability.SEARCH_CATALOG)

    init {
        viewModelScope.launch {
            queryFlow
                .debounce(350)
                .distinctUntilChanged()
                .collect { runSearch(it) }
        }
    }

    fun onQueryChange(value: String) {
        _state.update { it.copy(query = value) }
        queryFlow.value = value
    }

    private suspend fun runSearch(query: String) {
        val source = catalogSource ?: run {
            _state.update { it.copy(error = "No sticker catalog available", results = emptyList()) }
            return
        }
        _state.update { it.copy(isLoading = true, error = null) }
        try {
            when (val result = source.searchCatalog(CatalogQuery(text = query))) {
                is StickResult.Success -> _state.update {
                    it.copy(isLoading = false, results = result.value)
                }
                is StickResult.Failure -> _state.update {
                    it.copy(isLoading = false, error = result.error.message)
                }
            }
        } catch (t: Throwable) {
            _state.update { it.copy(isLoading = false, error = t.message ?: "Search failed") }
        }
    }

    /** Download a catalog sticker straight into the library. */
    fun save(sticker: RemoteSticker) = viewModelScope.launch {
        _state.update { it.copy(savingId = sticker.id) }
        importStickers.downloadAndSave(listOf(sticker))
        _state.update { it.copy(savingId = null) }
    }
}
