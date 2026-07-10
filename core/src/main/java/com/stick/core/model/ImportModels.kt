package com.stick.core.model

/**
 * A resolved reference to a TikTok video, produced from a raw (and possibly
 * shortened `vm.tiktok.com`) URL. Kept in `:core` because both the sticker
 * source and the app's history feature need it.
 */
data class TikTokVideoRef(
    val videoId: String,
    val authorId: String,
    val canonicalUrl: String,
)

/** A single comment fetched from a video, possibly carrying an animated sticker. */
data class TikTokComment(
    val id: String,
    val authorName: String,
    val text: String,
    /** Present only when the comment contains an animated sticker. */
    val sticker: RemoteSticker?,
)

/**
 * Search query against a sticker catalog. Kept as a value object so the query
 * surface can grow (filters, sort, paging) without breaking call sites.
 */
data class CatalogQuery(
    val text: String = "",
    val page: Int = 0,
    val pageSize: Int = 30,
)
