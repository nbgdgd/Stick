package com.stick.app.domain.converter

import com.stick.core.model.StickerFormat

/**
 * Everything the exporter needs to produce a single output file.
 *
 * [telegramVideoSticker] and [telegramTgs] provide one-tap presets that clamp the
 * settings to Telegram's hard limits (512px, ≤3s, ≤256KB, 30fps).
 */
data class ExportOptions(
    val format: StickerFormat,
    val widthPx: Int = 512,
    val heightPx: Int = 512,
    val fps: Int = 30,
    /** 0..100 encoder quality. */
    val quality: Int = 80,
    /** Target bitrate for video formats, in kbps. 0 = let the encoder decide. */
    val bitrateKbps: Int = 0,
    val preserveTransparency: Boolean = true,
    /** Apply extra size-reduction passes (palette/lossy) at a small quality cost. */
    val optimizeSize: Boolean = true,
) {
    companion object {
        /** Telegram video sticker preset: VP9/WebM, 512px, 30fps, ≤256KB target. */
        fun telegramVideoSticker() = ExportOptions(
            format = StickerFormat.TELEGRAM_WEBM,
            widthPx = 512,
            heightPx = 512,
            fps = 30,
            quality = 75,
            preserveTransparency = true,
            optimizeSize = true,
        )

        fun telegramTgs() = ExportOptions(
            format = StickerFormat.TELEGRAM_TGS,
            widthPx = 512,
            heightPx = 512,
            fps = 60,
        )

        fun gif() = ExportOptions(format = StickerFormat.GIF, quality = 90)
    }
}

/** Result of a conversion, including the actual size so the UI can confirm it. */
data class ExportResult(
    val outputPath: String,
    val format: StickerFormat,
    val fileSizeBytes: Long,
    val widthPx: Int,
    val heightPx: Int,
)
