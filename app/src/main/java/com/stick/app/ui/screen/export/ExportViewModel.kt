package com.stick.app.ui.screen.export

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stick.app.data.database.entity.StickerEntity
import com.stick.app.data.repository.StickerRepository
import com.stick.app.domain.converter.EditPipeline
import com.stick.app.domain.converter.ExportOptions
import com.stick.app.domain.converter.MediaConverter
import com.stick.core.model.MediaInfo
import com.stick.core.model.StickerFormat
import com.stick.core.result.StickResult
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class ExportUiState(
    val sticker: StickerEntity? = null,
    val info: MediaInfo = MediaInfo.EMPTY,
    val options: ExportOptions = ExportOptions.telegramVideoSticker(),
    val estimatedSizeBytes: Long = 0L,
    val isExporting: Boolean = false,
    val progress: Float = 0f,
    val outputPath: String? = null,
    val error: String? = null,
)

/**
 * Configures and runs an export. Shows a live size estimate (via the converter's
 * cheap [MediaConverter.estimateSize]) as options change, then performs the real
 * hardware-accelerated conversion on demand.
 */
@HiltViewModel
class ExportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: StickerRepository,
    private val converter: MediaConverter,
) : ViewModel() {

    private val _state = MutableStateFlow(ExportUiState())
    val state: StateFlow<ExportUiState> = _state.asStateFlow()

    fun load(id: String) = viewModelScope.launch {
        val entity = repository.findById(id) ?: return@launch
        val info = converter.probe(entity.localPath).getOrNull() ?: MediaInfo(
            widthPx = entity.widthPx, heightPx = entity.heightPx,
            fps = entity.fps, durationMs = entity.durationMs,
            fileSizeBytes = entity.fileSizeBytes,
        )
        _state.update { it.copy(sticker = entity, info = info) }
        recomputeEstimate()
    }

    fun selectFormat(format: StickerFormat) {
        val options = when (format) {
            StickerFormat.TELEGRAM_WEBM -> ExportOptions.telegramVideoSticker()
            StickerFormat.TELEGRAM_TGS -> ExportOptions.telegramTgs()
            StickerFormat.GIF -> ExportOptions.gif()
            else -> _state.value.options.copy(format = format)
        }
        _state.update { it.copy(options = options) }
        recomputeEstimate()
    }

    fun setFps(fps: Int) = updateOptions { it.copy(fps = fps) }
    fun setQuality(q: Int) = updateOptions { it.copy(quality = q) }
    fun setSize(px: Int) = updateOptions { it.copy(widthPx = px, heightPx = px) }
    fun setOptimize(on: Boolean) = updateOptions { it.copy(optimizeSize = on) }

    private fun updateOptions(block: (ExportOptions) -> ExportOptions) {
        _state.update { it.copy(options = block(it.options)) }
        recomputeEstimate()
    }

    private fun recomputeEstimate() {
        val s = _state.value
        val estimate = converter.estimateSize(s.info, s.options)
        _state.update { it.copy(estimatedSizeBytes = estimate) }
    }

    /** Run the conversion and expose the resulting file path. */
    fun export() = viewModelScope.launch {
        val s = _state.value
        val sticker = s.sticker ?: return@launch
        _state.update { it.copy(isExporting = true, progress = 0f, error = null, outputPath = null) }

        try {
            val exportsDir = File(context.filesDir, "exports").apply { mkdirs() }
            val safeName = sticker.id.substringAfterLast(':').replace(Regex("[^A-Za-z0-9_-]"), "_")
            val output = File(exportsDir, "$safeName.${s.options.format.extension}")
            val pipeline = EditPipeline(sourcePath = sticker.localPath)

            when (val result = converter.convert(pipeline, s.options, output.absolutePath) { p ->
                _state.update { it.copy(progress = p) }
            }) {
                is StickResult.Success -> _state.update {
                    it.copy(
                        isExporting = false,
                        progress = 1f,
                        outputPath = result.value.outputPath,
                        estimatedSizeBytes = result.value.fileSizeBytes,
                    )
                }
                is StickResult.Failure -> _state.update {
                    it.copy(isExporting = false, error = result.error.message)
                }
            }
        } catch (t: Throwable) {
            _state.update { it.copy(isExporting = false, error = t.message ?: "Export failed") }
        }
    }
}
