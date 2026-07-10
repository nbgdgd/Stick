package com.stick.app.domain.converter

import com.stick.core.model.MediaInfo
import com.stick.core.result.StickResult

/**
 * The seam between the app and *how media is decoded, edited and encoded*.
 *
 * Like [com.stick.stickersource.StickerSource], this is a pluggable boundary: the
 * default implementation is hardware-accelerated (Media3 `Transformer` +
 * `MediaCodec`), but it can be swapped for an FFmpeg-based backend for formats
 * the platform codecs don't cover (e.g. animated WebP encoding, APNG) without any
 * change to the editor or export UI.
 */
interface MediaConverter {

    /** Probe a file for FPS / resolution / duration / frame count / size. */
    suspend fun probe(path: String): StickResult<MediaInfo>

    /**
     * Apply [pipeline] and encode to [options], reporting progress in `0f..1f`.
     * Non-destructive: [pipeline]'s source file is never modified.
     */
    suspend fun convert(
        pipeline: EditPipeline,
        options: ExportOptions,
        outputPath: String,
        onProgress: (Float) -> Unit = {},
    ): StickResult<ExportResult>

    /**
     * Render a single frame to a PNG for editor scrubbing / preview caching.
     * [positionMs] is clamped to the animation's duration.
     */
    suspend fun extractFrame(
        path: String,
        positionMs: Long,
        outputPath: String,
    ): StickResult<String>

    /**
     * Cheap, no-encode estimate of the output size for [options], used to show
     * "Estimated size: …" before the user commits. See [SizeEstimator].
     */
    fun estimateSize(info: MediaInfo, options: ExportOptions): Long
}
