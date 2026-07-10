package com.stick.core.model

/**
 * Every animation container the app is able to read (import) and/or write (export).
 *
 * The values are intentionally exhaustive and self-describing so that both the
 * viewer and the exporter can reason about a sticker without inspecting file bytes.
 */
enum class StickerFormat(
    val extension: String,
    val mimeType: String,
    val isAnimated: Boolean,
    val supportsTransparency: Boolean,
) {
    /** TikTok comment stickers are commonly served as animated WebP. */
    WEBP_ANIMATED("webp", "image/webp", isAnimated = true, supportsTransparency = true),
    WEBP_STATIC("webp", "image/webp", isAnimated = false, supportsTransparency = true),
    GIF("gif", "image/gif", isAnimated = true, supportsTransparency = true),
    APNG("png", "image/apng", isAnimated = true, supportsTransparency = true),
    PNG("png", "image/png", isAnimated = false, supportsTransparency = true),

    /** Telegram video sticker. VP9 in a WebM container, ≤ 3s, ≤ 256KB, 512px. */
    TELEGRAM_WEBM("webm", "video/webm", isAnimated = true, supportsTransparency = true),

    /** Telegram animated sticker: a gzipped Lottie/Bodymovin JSON. Vector only. */
    TELEGRAM_TGS("tgs", "application/gzip", isAnimated = true, supportsTransparency = true),

    MP4("mp4", "video/mp4", isAnimated = true, supportsTransparency = false),
    LOTTIE("json", "application/json", isAnimated = true, supportsTransparency = true);

    companion object {
        /** Formats that make sense as an export target, in UI display order. */
        val exportTargets: List<StickerFormat> = listOf(
            TELEGRAM_WEBM, TELEGRAM_TGS, GIF, WEBP_ANIMATED, MP4, APNG,
        )

        fun fromExtension(ext: String): StickerFormat? =
            entries.firstOrNull { it.extension.equals(ext.removePrefix("."), ignoreCase = true) }
    }
}
