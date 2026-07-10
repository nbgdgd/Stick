package com.stick.app.media

import com.stick.app.domain.converter.EditPipeline
import com.stick.app.domain.converter.ExportOptions
import com.stick.app.domain.converter.ExportResult
import com.stick.core.result.StickResult

/**
 * Encoder for containers the Android platform codecs can't write: animated GIF,
 * animated WebP, APNG and Telegram's `.tgs` (gzipped Lottie).
 *
 * This is deliberately a separate, injectable seam from [Media3MediaConverter] so
 * the native/heavy dependency (an FFmpeg build, or a Lottie packer) can be swapped
 * or feature-flagged without touching the video pipeline or any UI. The default
 * production binding is [FfmpegFrameFormatConverter].
 */
interface FrameFormatConverter {
    suspend fun convert(
        pipeline: EditPipeline,
        options: ExportOptions,
        outputPath: String,
        onProgress: (Float) -> Unit,
    ): StickResult<ExportResult>
}
