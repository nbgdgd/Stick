package com.stick.app.ui.screen.viewer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stick.app.data.database.entity.StickerEntity
import com.stick.app.data.repository.StickerRepository
import com.stick.app.domain.converter.MediaConverter
import com.stick.core.model.MediaInfo
import com.stick.core.model.StickerFormat
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ViewerUiState(
    val sticker: StickerEntity? = null,
    val info: MediaInfo = MediaInfo.EMPTY,
    val playing: Boolean = true,
    val frameIndex: Int = 0,
)

/**
 * Loads a saved sticker and its probed [MediaInfo] for the viewer. Owns the
 * play/pause and frame-step state that the viewer UI binds to.
 */
@HiltViewModel
class ViewerViewModel @Inject constructor(
    private val repository: StickerRepository,
    private val converter: MediaConverter,
) : ViewModel() {

    private val _state = MutableStateFlow(ViewerUiState())
    val state: StateFlow<ViewerUiState> = _state.asStateFlow()

    fun load(id: String) = viewModelScope.launch {
        val entity = repository.findById(id) ?: return@launch
        val probed = converter.probe(entity.localPath).getOrNull()
            ?: MediaInfo(
                widthPx = entity.widthPx,
                heightPx = entity.heightPx,
                fps = entity.fps,
                durationMs = entity.durationMs,
                fileSizeBytes = entity.fileSizeBytes,
                format = StickerFormat.entries.firstOrNull { it.name == entity.format }
                    ?: StickerFormat.WEBP_ANIMATED,
            )
        _state.update { it.copy(sticker = entity, info = probed) }
    }

    fun togglePlay() = _state.update { it.copy(playing = !it.playing) }

    fun stepFrame(delta: Int) = _state.update {
        val max = (it.info.frameCount - 1).coerceAtLeast(0)
        it.copy(playing = false, frameIndex = (it.frameIndex + delta).coerceIn(0, max))
    }
}
