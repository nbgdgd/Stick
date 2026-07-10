package com.stick.core.model

/**
 * Technical metadata about a decoded animation, surfaced in the viewer
 * (FPS / resolution / size / duration).
 *
 * All fields are nullable-free; a value of `0` means "unknown / not yet probed".
 */
data class MediaInfo(
    val widthPx: Int = 0,
    val heightPx: Int = 0,
    val frameCount: Int = 0,
    val fps: Float = 0f,
    val durationMs: Long = 0L,
    val fileSizeBytes: Long = 0L,
    val format: StickerFormat = StickerFormat.WEBP_ANIMATED,
    val hasAlpha: Boolean = true,
) {
    val aspectRatio: Float
        get() = if (heightPx == 0) 1f else widthPx.toFloat() / heightPx.toFloat()

    val resolutionLabel: String
        get() = "${widthPx}×${heightPx}"

    companion object {
        val EMPTY = MediaInfo()
    }
}
