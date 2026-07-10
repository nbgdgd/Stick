package com.stick.stickersource.tiktok

import com.stick.core.model.RemoteSticker
import com.stick.core.model.StickerFormat
import com.stick.core.model.StickerOrigin
import com.stick.stickersource.tiktok.dto.CommentDto

/**
 * The single translation layer from TikTok comment DTOs to the domain model.
 * Includes every attached image — static photo comments *and* animated stickers —
 * since both are things a user may want to save.
 */
internal object TikTokMapper {

    const val SOURCE_ID = "tiktok-comment"

    fun stickersFromComment(comment: CommentDto, videoUrl: String): List<RemoteSticker> {
        if (comment.imageList.isEmpty()) return emptyList()
        val author = comment.user?.nickname.orEmpty()
        return comment.imageList.mapIndexedNotNull { index, image ->
            val url = image.originUrl?.primary ?: image.cropUrl?.primary ?: return@mapIndexedNotNull null
            RemoteSticker(
                id = if (comment.imageList.size == 1) comment.id else "${comment.id}_$index",
                sourceId = SOURCE_ID,
                name = comment.text.ifBlank { "Sticker ${comment.id}" }.take(40),
                downloadUrl = url,
                previewUrl = image.cropUrl?.primary ?: url,
                format = formatFor(url),
                origin = StickerOrigin.Comment(videoUrl, comment.id, author),
            )
        }
    }

    /** Best-effort container detection from the CDN URL. */
    private fun formatFor(url: String): StickerFormat {
        val base = url.substringBefore('?').lowercase()
        return when {
            base.endsWith(".webp") -> StickerFormat.WEBP_ANIMATED
            base.endsWith(".gif") -> StickerFormat.GIF
            base.endsWith(".png") -> StickerFormat.PNG
            // TikTok's `~tplv-…-image-origin.image` URLs are served as JPEG.
            else -> StickerFormat.JPEG
        }
    }
}
