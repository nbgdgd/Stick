package com.stick.app.ui.screen.importer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stick.app.data.repository.StickerRepository
import com.stick.app.domain.usecase.ImportStickersUseCase
import com.stick.core.model.RemoteSticker
import com.stick.core.model.TikTokVideoRef
import com.stick.core.result.StickResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A discovered sticker plus whether the user has ticked it for download. */
data class DiscoveredSticker(val sticker: RemoteSticker, val selected: Boolean = true)

data class ImportUiState(
    val input: String = "",
    val isResolving: Boolean = false,
    val isScanning: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadProgress: Float = 0f,
    val resolved: TikTokVideoRef? = null,
    val discovered: List<DiscoveredSticker> = emptyList(),
    val error: String? = null,
    val savedCount: Int = 0,
) {
    val selectedCount: Int get() = discovered.count { it.selected }
}

/**
 * Owns the whole import pipeline: resolve link → stream comment stickers →
 * multi-select → batch download. All heavy lifting is delegated to
 * [ImportStickersUseCase]; the ViewModel only holds UI state.
 */
@HiltViewModel
class ImportViewModel @Inject constructor(
    private val importStickers: ImportStickersUseCase,
    private val repository: StickerRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ImportUiState())
    val state: StateFlow<ImportUiState> = _state.asStateFlow()

    fun onInputChange(value: String) = _state.update { it.copy(input = value, error = null) }

    /** Resolve the link, then scan comments, emitting previews as they arrive. */
    fun startScan(rawInput: String = _state.value.input) = viewModelScope.launch {
        if (rawInput.isBlank()) return@launch
        _state.update { it.copy(isResolving = true, error = null, discovered = emptyList(), savedCount = 0) }

        // Any surprise here becomes a visible error instead of crashing the app.
        try {
            when (val resolved = importStickers.resolve(rawInput)) {
                is StickResult.Failure -> _state.update {
                    it.copy(isResolving = false, error = resolved.error.message)
                }
                is StickResult.Success -> {
                    _state.update { it.copy(isResolving = false, isScanning = true, resolved = resolved.value) }
                    scan(resolved.value, rawInput)
                }
            }
        } catch (t: Throwable) {
            _state.update {
                it.copy(isResolving = false, isScanning = false, error = t.message ?: "Unexpected error")
            }
        }
    }

    private suspend fun scan(video: TikTokVideoRef, rawInput: String) {
        importStickers.scanComments(video)
            .catch { t -> _state.update { it.copy(isScanning = false, error = t.message) } }
            .onCompletion {
                val found = _state.value.discovered.size
                repository.recordHistory(rawInput, video.canonicalUrl, found)
                _state.update { it.copy(isScanning = false) }
            }
            .collect { result ->
                when (result) {
                    is StickResult.Success -> _state.update { s ->
                        s.copy(discovered = s.discovered + DiscoveredSticker(result.value))
                    }
                    is StickResult.Failure -> _state.update { it.copy(error = result.error.message) }
                }
            }
    }

    fun toggle(index: Int) = _state.update { s ->
        s.copy(
            discovered = s.discovered.mapIndexed { i, d ->
                if (i == index) d.copy(selected = !d.selected) else d
            },
        )
    }

    fun selectAll(selected: Boolean) = _state.update { s ->
        s.copy(discovered = s.discovered.map { it.copy(selected = selected) })
    }

    /** Download and save every ticked sticker, reporting batch progress. */
    fun downloadSelected(onDone: () -> Unit = {}) = viewModelScope.launch {
        val chosen = _state.value.discovered.filter { it.selected }.map { it.sticker }
        if (chosen.isEmpty()) return@launch
        _state.update { it.copy(isDownloading = true, downloadProgress = 0f) }

        val results = importStickers.downloadAndSave(chosen) { index, total, _ ->
            _state.update { it.copy(downloadProgress = index.toFloat() / total) }
        }
        val saved = results.count { it.isSuccess }
        _state.update { it.copy(isDownloading = false, downloadProgress = 1f, savedCount = saved) }
        onDone()
    }
}
