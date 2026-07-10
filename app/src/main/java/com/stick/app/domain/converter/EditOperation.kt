package com.stick.app.domain.converter

/**
 * A single, order-independent edit the user has applied in the editor.
 *
 * Edits are modelled as immutable value objects and collected into an
 * [EditPipeline]. The converter interprets the pipeline; the UI just adds/removes
 * operations. This makes the editor non-destructive (the original file is never
 * mutated) and trivially undoable.
 */
sealed interface EditOperation {
    data class Resize(val widthPx: Int, val heightPx: Int) : EditOperation
    /** Playback speed multiplier; 2.0 = twice as fast. */
    data class Speed(val factor: Float) : EditOperation
    data class Fps(val fps: Int) : EditOperation
    data class Trim(val startMs: Long, val endMs: Long) : EditOperation
    /** 0f (transparent) … 1f (opaque). */
    data class Opacity(val alpha: Float) : EditOperation
    data class Crop(val left: Int, val top: Int, val right: Int, val bottom: Int) : EditOperation
    data class Rotate(val degrees: Int) : EditOperation
    data class Flip(val horizontal: Boolean, val vertical: Boolean) : EditOperation
    data object CenterContent : EditOperation
    /** ARGB color; ignored when [removeBackground] is also present. */
    data class BackgroundColor(val argb: Int) : EditOperation
    data object RemoveBackground : EditOperation
    data class AddText(
        val text: String,
        val xFraction: Float,
        val yFraction: Float,
        val sizeSp: Int,
        val argb: Int,
    ) : EditOperation
    /** Combine several stickers into one, laid out in a grid. */
    data class Merge(val additionalPaths: List<String>, val columns: Int) : EditOperation
}

/**
 * An ordered set of edits plus the source path. Immutable; the editor produces a
 * new pipeline on every change so state is easy to snapshot for undo/redo.
 */
data class EditPipeline(
    val sourcePath: String,
    val operations: List<EditOperation> = emptyList(),
) {
    fun with(op: EditOperation): EditPipeline = copy(operations = operations + op)

    /** Replace any existing operation of the same type (e.g. re-drag a slider). */
    fun replacing(op: EditOperation): EditPipeline =
        copy(operations = operations.filterNot { it::class == op::class } + op)

    fun without(op: EditOperation): EditPipeline =
        copy(operations = operations - op)

    val isEmpty: Boolean get() = operations.isEmpty()
}
