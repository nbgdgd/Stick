package com.stick.stickersource.tiktok

import com.stick.core.model.RemoteSticker
import com.stick.core.model.StickerFormat
import com.stick.core.model.StickerOrigin
import com.stick.stickersource.tiktok.dto.TikwmComment

/**
 * The single translation layer from tikwm DTOs to the app's stable domain model.
 * A proxy/payload change is absorbed here without touching feature code.
 */
internal object TikTokMapper {

    const val SOURCE_ID = "tiktok-comment"

    /**
     * Turn a comment into one sticker per attached image. Text-only comments
     * produce nothing. Returns a list because a single comment may carry several
     * images.
     */
    fun stickersFromComment(comment: TikwmComment, videoUrl: String): List<RemoteSticker> {
        if (comment.images.isEmpty()) return emptyList()
        val author = comment.user?.nickname.orEmpty()
        return comment.images.mapIndexed { index, url ->
            RemoteSticker(
                id = if (comment.images.size == 1) comment.id else "${comment.id}_$index",
                sourceId = SOURCE_ID,
                name = comment.text.ifBlank { "Sticker ${comment.id}" }.take(40),
                downloadUrl = url,
                previewUrl = url,
                format = formatFor(url),
                origin = StickerOrigin.Comment(videoUrl, comment.id, author),
            )
        }
    }

    /** Best-effort container detection from the CDN URL. */
    private fun formatFor(url: String): StickerFormat {
        val lower = url.substringBefore('?').lowercase()
        return when {
            lower.endsWith(".webp") -> StickerFormat.WEBP_ANIMATED
            lower.endsWith(".gif") -> StickerFormat.GIF
            lower.endsWith(".png") -> StickerFormat.PNG
            else -> StickerFormat.WEBP_ANIMATED
        }
    }
}
