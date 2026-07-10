package com.stick.app.ui.screen.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stick.app.data.database.entity.StickerEntity
import com.stick.app.data.repository.StickerRepository
import com.stick.app.domain.converter.EditOperation
import com.stick.app.domain.converter.EditPipeline
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EditorUiState(
    val sticker: StickerEntity? = null,
    val pipeline: EditPipeline? = null,
    val undoStack: List<EditPipeline> = emptyList(),
) {
    val canUndo: Boolean get() = undoStack.isNotEmpty()
}

/**
 * Non-destructive editor state. Every change produces a new immutable
 * [EditPipeline] and pushes the previous one onto an undo stack; the source file
 * is never touched. The actual pixels are rendered by the [MediaConverter] only at
 * export time (and for on-demand previews).
 */
@HiltViewModel
class EditorViewModel @Inject constructor(
    private val repository: StickerRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(EditorUiState())
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    fun load(id: String) = viewModelScope.launch {
        val entity = repository.findById(id) ?: return@launch
        _state.update {
            it.copy(sticker = entity, pipeline = EditPipeline(sourcePath = entity.localPath))
        }
    }

    /** Add or replace an operation of the same type (sliders replace, others add). */
    fun apply(op: EditOperation, replace: Boolean = true) = _state.update { s ->
        val current = s.pipeline ?: return@update s
        val next = if (replace) current.replacing(op) else current.with(op)
        s.copy(pipeline = next, undoStack = s.undoStack + current)
    }

    fun remove(op: EditOperation) = _state.update { s ->
        val current = s.pipeline ?: return@update s
        s.copy(pipeline = current.without(op), undoStack = s.undoStack + current)
    }

    fun undo() = _state.update { s ->
        val previous = s.undoStack.lastOrNull() ?: return@update s
        s.copy(pipeline = previous, undoStack = s.undoStack.dropLast(1))
    }
}
