package com.stick.core.model

/**
 * A sticker as advertised by a remote source (TikTok comment, TikTok catalog,
 * clipboard, a local file, …) *before* it has been downloaded.
 *
 * This is the boundary type produced by every [com.stick.stickersource] provider.
 * It is deliberately transport-agnostic: it only carries enough information to
 * preview the sticker and to fetch the full asset later.
 */
data class RemoteSticker(
    /** Stable identifier within the originating source. */
    val id: String,
    /** Which provider produced this entry (e.g. "tiktok-comment", "tiktok-catalog"). */
    val sourceId: String,
    /** Human readable name, when the source exposes one. */
    val name: String,
    /** Direct URL to the full-resolution animated asset. */
    val downloadUrl: String,
    /** Low-cost URL for grid previews; falls back to [downloadUrl]. */
    val previewUrl: String = downloadUrl,
    val format: StickerFormat = StickerFormat.WEBP_ANIMATED,
    /** Best-effort metadata; may be partially unknown until downloaded. */
    val info: MediaInfo = MediaInfo.EMPTY,
    /** Free-form keywords the catalog exposes; used for on-device search. */
    val keywords: List<String> = emptyList(),
    /** Where this sticker was seen (comment id, video url, author …) for history. */
    val origin: StickerOrigin = StickerOrigin.Unknown,
)

/** Provenance of a [RemoteSticker], preserved so the library can show history. */
sealed interface StickerOrigin {
    data object Unknown : StickerOrigin
    data class Comment(
        val videoUrl: String,
        val commentId: String,
        val authorName: String,
    ) : StickerOrigin
    data class Catalog(val collection: String) : StickerOrigin
    data class LocalFile(val path: String) : StickerOrigin
    data object Clipboard : StickerOrigin
}
