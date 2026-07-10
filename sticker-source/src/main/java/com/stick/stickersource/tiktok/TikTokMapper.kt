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
        val author = comment.user?.nickname.orEmpty()
        val out = ArrayList<RemoteSticker>()

        // 1) Animated sticker-pack sticker (the ".awebp" ones users tap from the tray).
        comment.commentSticker?.animatedUrl?.best?.let { animated ->
            out += RemoteSticker(
                id = "s_${comment.id}",
                sourceId = SOURCE_ID,
                name = comment.commentSticker.name.ifBlank { comment.text }.ifBlank { "Sticker" }.take(40),
                downloadUrl = animated,
                previewUrl = comment.commentSticker.staticUrl?.best ?: animated,
                format = formatFor(animated),
                origin = StickerOrigin.Comment(videoUrl, comment.id, author),
            )
        }

        // 2) Image/photo comments.
        comment.imageList.forEachIndexed { index, image ->
            val url = image.originUrl?.primary ?: image.cropUrl?.primary ?: return@forEachIndexed
            out += RemoteSticker(
                id = if (comment.imageList.size == 1) "i_${comment.id}" else "i_${comment.id}_$index",
                sourceId = SOURCE_ID,
                name = comment.text.ifBlank { "Sticker ${comment.id}" }.take(40),
                downloadUrl = url,
                previewUrl = image.cropUrl?.primary ?: url,
                format = formatFor(url),
                origin = StickerOrigin.Comment(videoUrl, comment.id, author),
            )
        }

        // 3) Replies inlined in the response can carry stickers too.
        comment.replyComment?.forEach { out += stickersFromComment(it, videoUrl) }
        return out
    }

    /** Best-effort container detection from the CDN URL. */
    private fun formatFor(url: String): StickerFormat {
        val base = url.substringBefore('?').lowercase()
        return when {
            base.endsWith(".awebp") || base.endsWith(".webp") -> StickerFormat.WEBP_ANIMATED
            base.endsWith(".gif") -> StickerFormat.GIF
            base.endsWith(".png") -> StickerFormat.PNG
            // TikTok's `~tplv-…-image-origin.image` URLs are served as JPEG.
            else -> StickerFormat.JPEG
        }
    }
}
