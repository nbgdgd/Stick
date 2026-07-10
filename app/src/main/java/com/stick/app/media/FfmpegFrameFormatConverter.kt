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
 * Encoder for every export format, backed by FFmpeg.
 *
 * FFmpeg handles all the input types the app deals with (static JPEG/PNG comment
 * stickers, animated GIF, APNG) and every output target — including the Telegram
 * `.webm` (VP9 + alpha) and `.mp4` that the platform's `Transformer` could not
 * produce from an image input (which is what used to crash export).
 *
 * `.tgs` is not a raster encode (gzipped Lottie), so it is routed to [lottiePacker].
 */
class FfmpegFrameFormatConverter(
    private val runner: FfmpegRunner,
    private val lottiePacker: LottiePacker,
    private val frameExtractor: AnimatedFrameExtractor? = null,
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
        if (!File(pipeline.sourcePath).exists()) {
            return StickResult.Failure(StickError.NotFound("Source file missing"))
        }

        // FFmpeg can't decode animated WebP — pre-decode those to a PNG sequence.
        val ext = pipeline.sourcePath.substringAfterLast('.', "").lowercase()
        val frames = if (ext == "webp" || ext == "awebp") frameExtractor?.extract(pipeline.sourcePath) else null

        val args = buildArgs(pipeline, options, outputPath, frames)
        return when (val res = runner.run(args, onProgress).also { frames?.dir?.deleteRecursively() }) {
            is StickResult.Failure -> res
            is StickResult.Success -> {
                val file = File(outputPath)
                if (!file.exists() || file.length() == 0L) {
                    StickResult.Failure(StickError.Conversion("FFmpeg produced no output"))
                } else {
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
    }

    /**
     * Translate the edit pipeline + export options into an FFmpeg command line.
     * Pure function → unit-testable without a native binary present.
     */
    internal fun buildArgs(
        pipeline: EditPipeline,
        options: ExportOptions,
        outputPath: String,
        frames: AnimatedFrameExtractor.Frames? = null,
    ): List<String> {
        val ext = pipeline.sourcePath.substringAfterLast('.', "").lowercase()
        val isStatic = frames == null && ext !in ANIMATED_EXTS
        val trim = pipeline.operations.filterIsInstance<EditOperation.Trim>().firstOrNull()
        val durationSec = trim?.let { (it.endMs - it.startMs) / 1000.0 }?.coerceAtLeast(0.1) ?: 3.0

        val args = mutableListOf("-y")
        when {
            frames != null -> {
                // Pre-decoded PNG sequence (animated WebP path).
                args += listOf("-framerate", frames.fps.toString(), "-i", "${frames.dir}/f_%05d.png")
            }
            isStatic -> {
                // A single image must be looped into a short clip to produce an animation.
                args += listOf("-loop", "1", "-t", durationSec.toString(), "-i", pipeline.sourcePath)
            }
            else -> {
                if (trim != null) {
                    args += listOf("-ss", (trim.startMs / 1000.0).toString(), "-t", durationSec.toString())
                }
                args += listOf("-i", pipeline.sourcePath)
            }
        }

        // --- video filter chain ---
        val vf = mutableListOf<String>()
        if (!isStatic) {
            pipeline.operations.filterIsInstance<EditOperation.Speed>().firstOrNull()?.let {
                vf += "setpts=${1f / it.factor}*PTS"
            }
        }
        vf += "fps=${options.fps}"
        pipeline.operations.filterIsInstance<EditOperation.Crop>().firstOrNull()?.let {
            vf += "crop=${it.right - it.left}:${it.bottom - it.top}:${it.left}:${it.top}"
        }
        vf += "scale=${options.widthPx}:${options.heightPx}:force_original_aspect_ratio=decrease:flags=lanczos"
        pipeline.operations.filterIsInstance<EditOperation.Rotate>().firstOrNull()?.let {
            vf += "rotate=${it.degrees}*PI/180"
        }
        pipeline.operations.filterIsInstance<EditOperation.Flip>().firstOrNull()?.let {
            if (it.horizontal) vf += "hflip"
            if (it.vertical) vf += "vflip"
        }

        // --- per-format codec + filter tail ---
        when (options.format) {
            StickerFormat.GIF -> {
                vf += "split[s0][s1];[s0]palettegen=stats_mode=diff[p];[s1][p]paletteuse=dither=bayer"
            }
            StickerFormat.WEBP_ANIMATED -> {
                args += listOf(
                    "-c:v", "libwebp", "-loop", "0",
                    "-lossless", if (options.optimizeSize) "0" else "1",
                    "-q:v", options.quality.toString(),
                )
            }
            StickerFormat.APNG -> args += listOf("-f", "apng", "-plays", "0")
            StickerFormat.TELEGRAM_WEBM -> {
                // Telegram: VP9, ≤512px, ≤3s, alpha preserved, ≤256KB.
                vf += "pad=${options.widthPx}:${options.heightPx}:-1:-1:color=0x00000000"
                args += listOf("-t", "3", "-c:v", "libvpx-vp9", "-pix_fmt", "yuva420p", "-an")
                if (options.bitrateKbps > 0) {
                    args += listOf("-b:v", "${options.bitrateKbps}k")
                } else {
                    args += listOf("-b:v", "0", "-crf", crfFor(options.quality).toString())
                }
            }
            StickerFormat.MP4 -> {
                vf += "format=yuv420p"
                args += listOf("-c:v", "libx264", "-movflags", "+faststart", "-an", "-pix_fmt", "yuv420p")
            }
            else -> {}
        }

        args += listOf("-vf", vf.joinToString(","))
        args += outputPath
        return args
    }

    /** Map 1..100 quality to a VP9 CRF (lower = better/bigger). */
    private fun crfFor(quality: Int): Int {
        val q = quality.coerceIn(1, 100)
        return (50 - (q * 25 / 100)).coerceIn(24, 50) // ~q80 → 30, q50 → 38
    }

    private companion object {
        val ANIMATED_EXTS = setOf("gif", "webp", "apng", "mp4", "webm", "mkv", "mov")
    }
}

/** Executes an FFmpeg command line, reporting `0f..1f` progress. */
interface FfmpegRunner {
    suspend fun run(args: List<String>, onProgress: (Float) -> Unit): StickResult<Unit>

    /** Safe default when no native FFmpeg is bundled. */
    object Unavailable : FfmpegRunner {
        override suspend fun run(args: List<String>, onProgress: (Float) -> Unit): StickResult<Unit> =
            StickResult.Failure(
                StickError.Unsupported("FFmpeg backend is not bundled in this build."),
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
                ".tgs requires a vector (Lottie) source; export raster stickers as .webm instead.",
            ),
        )
    }
}
