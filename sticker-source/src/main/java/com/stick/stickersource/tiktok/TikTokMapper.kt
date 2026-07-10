package com.stick.stickersource.tiktok

import com.stick.core.model.MediaInfo
import com.stick.core.model.RemoteSticker
import com.stick.core.model.StickerFormat
import com.stick.core.model.StickerOrigin
import com.stick.stickersource.tiktok.dto.CommentDto
import com.stick.stickersource.tiktok.dto.StickerDto

/**
 * The single translation layer from TikTok DTOs to the app's stable domain model.
 *
 * Keeping every field access in one file means a TikTok payload change is
 * absorbed here without touching feature code. All lookups are null-safe and fall
 * back to sensible defaults.
 */
internal object TikTokMapper {

    const val COMMENT_SOURCE_ID = "tiktok-comment"
    const val CATALOG_SOURCE_ID = "tiktok-catalog"

    /**
     * Extract an animated sticker from a comment, or `null` if the comment has
     * none. Reads both the `sticker` object and the legacy `image_list.gif_url`.
     */
    fun stickerFromComment(comment: CommentDto, videoUrl: String): RemoteSticker? {
        // Preferred: explicit sticker object.
        comment.sticker?.let { s ->
            val animate = s.animateUrl?.primary
            if (!animate.isNullOrBlank()) {
                return RemoteSticker(
                    id = s.id.ifBlank { comment.id },
                    sourceId = COMMENT_SOURCE_ID,
                    name = s.name.ifBlank { "Sticker ${s.id}" },
                    downloadUrl = animate,
                    previewUrl = s.staticUrl?.primary ?: animate,
                    format = StickerFormat.WEBP_ANIMATED,
                    info = MediaInfo(widthPx = s.width, heightPx = s.height),
                    keywords = s.keywords,
                    origin = StickerOrigin.Comment(videoUrl, comment.id, comment.user?.nickname.orEmpty()),
                )
            }
        }
        // Fallback: animated image attached to the comment.
        comment.imageList.firstNotNullOfOrNull { it.gifUrl?.primary }?.let { gif ->
            return RemoteSticker(
                id = comment.id,
                sourceId = COMMENT_SOURCE_ID,
                name = "Sticker ${comment.id}",
                downloadUrl = gif,
                previewUrl = comment.imageList.firstNotNullOfOrNull { it.originUrl?.primary } ?: gif,
                format = StickerFormat.WEBP_ANIMATED,
                origin = StickerOrigin.Comment(videoUrl, comment.id, comment.user?.nickname.orEmpty()),
            )
        }
        return null
    }

    fun stickerFromCatalog(s: StickerDto): RemoteSticker? {
        val animate = s.animateUrl?.primary ?: return null
        return RemoteSticker(
            id = s.id,
            sourceId = CATALOG_SOURCE_ID,
            name = s.name.ifBlank { "Sticker ${s.id}" },
            downloadUrl = animate,
            previewUrl = s.staticUrl?.primary ?: animate,
            format = StickerFormat.WEBP_ANIMATED,
            info = MediaInfo(widthPx = s.width, heightPx = s.height),
            keywords = s.keywords,
            origin = StickerOrigin.Catalog(collection = "tiktok"),
        )
    }
}
