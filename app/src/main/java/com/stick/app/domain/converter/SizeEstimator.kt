package com.stick.app.domain.converter

import com.stick.core.model.MediaInfo
import com.stick.core.model.StickerFormat
import kotlin.math.max
import kotlin.math.roundToLong

/**
 * Pure, dependency-free heuristic for predicting output file size, so the export
 * screen can show "Estimated size" live as sliders move without running an encode.
 *
 * The model is `pixels × frames × bitsPerPixel(format, quality)` with per-format
 * compression factors calibrated against typical sticker content. It is
 * intentionally conservative and unit-tested in `SizeEstimatorTest`.
 */
object SizeEstimator {

    fun estimate(info: MediaInfo, options: ExportOptions): Long {
        val durationSec = max(info.durationMs, 1L) / 1000.0
        val frames = max((options.fps * durationSec).roundToLong(), 1L)
        val pixels = options.widthPx.toLong() * options.heightPx.toLong()

        val bitsPerPixel = bitsPerPixel(options)
        val optimizeFactor = if (options.optimizeSize) 0.7 else 1.0

        return when (options.format) {
            // Video codecs are sized by bitrate × duration when a bitrate is set.
            StickerFormat.TELEGRAM_WEBM, StickerFormat.MP4 -> {
                val kbps = if (options.bitrateKbps > 0) {
                    options.bitrateKbps
                } else {
                    defaultVideoBitrateKbps(pixels, options.fps, options.quality)
                }
                (kbps * 1000L / 8 * durationSec).roundToLong().coerceAtLeast(1024)
            }
            // Frame-based formats scale with total pixels drawn.
            else -> {
                (pixels * frames * bitsPerPixel / 8.0 * optimizeFactor).roundToLong()
                    .coerceAtLeast(512)
            }
        }
    }

    private fun bitsPerPixel(options: ExportOptions): Double {
        val q = options.quality.coerceIn(1, 100) / 100.0
        return when (options.format) {
            StickerFormat.GIF -> 4.0            // palette-limited
            StickerFormat.WEBP_ANIMATED -> 1.2 * q
            StickerFormat.APNG -> 6.0 * q
            StickerFormat.TELEGRAM_TGS -> 0.05  // vector: nearly size-independent
            else -> 2.0 * q
        }
    }

    private fun defaultVideoBitrateKbps(pixels: Long, fps: Int, quality: Int): Int {
        val base = pixels * fps / 1000.0
        val q = quality.coerceIn(1, 100) / 100.0
        return (base * 0.07 * q).roundToLong().toInt().coerceIn(64, 2000)
    }
}
