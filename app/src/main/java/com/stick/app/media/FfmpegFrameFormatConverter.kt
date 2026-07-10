package com.stick.app.media

import com.stick.app.domain.converter.EditOperation
import com.stick.app.domain.converter.EditPipeline
import com.stick.app.domain.converter.ExportOptions
import com.stick.app.domain.converter.ExportResult
import com.stick.core.model.StickerFormat
import com.stick.core.result.StickError
import com.stick.core.result.StickResult
import java.io.File

/**
 * Frame-format encoder that shells out to an FFmpeg backend.
 *
 * ### Why FFmpeg and not the platform codecs
 * Android's public SDK can *decode* animated GIF/WebP (via `ImageDecoder`) but has
 * no API to *encode* animated GIF, animated WebP or APNG. FFmpeg (with libwebp,
 * libvpx and the apng muxer) covers all of them and is the industry standard for
 * this exact task.
 *
 * ### Wiring
 * This class builds the FFmpeg argument list — the pure, testable part — and hands
 * it to an [FfmpegRunner]. Bind a real runner (e.g. a maintained ffmpeg-kit fork,
 * or a bundled native build) in the DI module. Until a runner is provided, the
 * default [FfmpegRunner.Unavailable] returns a clear, user-actionable error rather
 * than crashing — keeping the rest of the app fully functional.
 *
 * `.tgs` is special: it is not a raster encode but a gzipped Lottie JSON, so it is
 * routed to [LottiePacker] instead of FFmpeg.
 */
class FfmpegFrameFormatConverter(
    private val runner: FfmpegRunner,
    private val lottiePacker: LottiePacker,
) : FrameFormatConverter {

    override suspend fun convert(
        pipeline: EditPipeline,
        options: ExportOptions,
        outputPath: String,
        onProgress: (Float) -> Unit,
    ): StickResult<ExportResult> {
        if (options.format == StickerFormat.TELEGRAM_TGS) {
            return lottiePacker.pack(pipeline, options, outputPath)
        }

        val args = buildArgs(pipeline, options, outputPath)
        return when (val res = runner.run(args, onProgress)) {
            is StickResult.Failure -> res
            is StickResult.Success -> {
                val file = File(outputPath)
                StickResult.Success(
                    ExportResult(
                        outputPath = outputPath,
                        format = options.format,
                        fileSizeBytes = file.length(),
                        widthPx = options.widthPx,
                        heightPx = options.heightPx,
                    ),
                )
            }
        }
    }

    /**
     * Translate the edit pipeline + export options into an FFmpeg filtergraph.
     * Pure function → unit-testable without a native binary present.
     */
    internal fun buildArgs(
        pipeline: EditPipeline,
        options: ExportOptions,
        outputPath: String,
    ): List<String> {
        val filters = mutableListOf<String>()

        // Speed (PTS) must come before fps/scale.
        pipeline.operations.filterIsInstance<EditOperation.Speed>().firstOrNull()?.let {
            filters += "setpts=${1f / it.factor}*PTS"
        }
        filters += "fps=${options.fps}"
        pipeline.operations.filterIsInstance<EditOperation.Crop>().firstOrNull()?.let {
            filters += "crop=${it.right - it.left}:${it.bottom - it.top}:${it.left}:${it.top}"
        }
        filters += "scale=${options.widthPx}:${options.heightPx}:flags=lanczos"
        pipeline.operations.filterIsInstance<EditOperation.Rotate>().firstOrNull()?.let {
            filters += "rotate=${it.degrees}*PI/180"
        }
        pipeline.operations.filterIsInstance<EditOperation.Flip>().firstOrNull()?.let {
            if (it.horizontal) filters += "hflip"
            if (it.vertical) filters += "vflip"
        }

        val args = mutableListOf("-y", "-i", pipeline.sourcePath)
        val trim = pipeline.operations.filterIsInstance<EditOperation.Trim>().firstOrNull()
        if (trim != null) {
            args += listOf("-ss", "${trim.startMs / 1000.0}", "-to", "${trim.endMs / 1000.0}")
        }

        when (options.format) {
            StickerFormat.GIF -> {
                // Two-pass palette for quality; expressed as a single filtergraph.
                filters += "split[s0][s1];[s0]palettegen=stats_mode=diff[p];[s1][p]paletteuse=dither=bayer"
            }
            StickerFormat.WEBP_ANIMATED -> {
                args += listOf("-vcodec", "libwebp", "-lossless", if (options.optimizeSize) "0" else "1",
                    "-quality", options.quality.toString(), "-loop", "0")
            }
            StickerFormat.APNG -> args += listOf("-f", "apng", "-plays", "0")
            else -> {}
        }

        args += listOf("-vf", filters.joinToString(","))
        args += outputPath
        return args
    }
}

/** Executes an FFmpeg command line, reporting `0f..1f` progress. */
interface FfmpegRunner {
    suspend fun run(args: List<String>, onProgress: (Float) -> Unit): StickResult<Unit>

    /** Safe default when no native FFmpeg is bundled. */
    object Unavailable : FfmpegRunner {
        override suspend fun run(args: List<String>, onProgress: (Float) -> Unit): StickResult<Unit> =
            StickResult.Failure(
                StickError.Unsupported(
                    "GIF/WebP/APNG export needs the FFmpeg backend, which is not bundled in " +
                        "this build. WebM and MP4 export work without it.",
                ),
            )
    }
}

/** Packs a (vector) sticker into a Telegram `.tgs` (gzipped Lottie JSON). */
interface LottiePacker {
    suspend fun pack(
        pipeline: EditPipeline,
        options: ExportOptions,
        outputPath: String,
    ): StickResult<ExportResult>

    object Unavailable : LottiePacker {
        override suspend fun pack(
            pipeline: EditPipeline,
            options: ExportOptions,
            outputPath: String,
        ): StickResult<ExportResult> = StickResult.Failure(
            StickError.Unsupported(
                ".tgs export requires a vector (Lottie) source; raster stickers can be exported " +
                    "as Telegram video stickers (.webm) instead.",
            ),
        )
    }
}
