package com.stick.stickersource.tiktok.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTOs mirroring TikTok's web comment API
 * (`/api/comment/list/?aweme_id=…`).
 *
 * ⚠️ These shapes are **reverse-engineered and not contractually stable**.
 * TikTok changes field names and nesting without notice. They are confined to
 * this package on purpose: mapping to the stable domain model happens in exactly
 * one place ([com.stick.stickersource.tiktok.TikTokMapper]), so an API change is
 * a small, local edit here rather than an app-wide refactor.
 *
 * Every field is optional/nullable so an unexpected payload degrades to "no
 * sticker found" instead of throwing.
 */
@Serializable
data class CommentListDto(
    @SerialName("comments") val comments: List<CommentDto> = emptyList(),
    @SerialName("cursor") val cursor: Long = 0,
    @SerialName("has_more") val hasMore: Int = 0,
    @SerialName("total") val total: Int = 0,
)

@Serializable
data class CommentDto(
    @SerialName("cid") val id: String = "",
    @SerialName("text") val text: String = "",
    @SerialName("user") val user: UserDto? = null,
    /**
     * Animated comment stickers arrive here. Historically TikTok has used both
     * `image_list` (generic images) and `sticker`; we read whichever is present.
     */
    @SerialName("image_list") val imageList: List<CommentImageDto> = emptyList(),
    @SerialName("sticker") val sticker: StickerDto? = null,
)

@Serializable
data class UserDto(
    @SerialName("uid") val uid: String = "",
    @SerialName("nickname") val nickname: String = "",
)

@Serializable
data class CommentImageDto(
    /** Non-animated preview thumbnail. */
    @SerialName("origin_url") val originUrl: UrlListDto? = null,
    /** The animated variant (WebP/GIF). Present for animated comment stickers. */
    @SerialName("gif_url") val gifUrl: UrlListDto? = null,
)

@Serializable
data class StickerDto(
    @SerialName("id") val id: String = "",
    @SerialName("name") val name: String = "",
    @SerialName("static_url") val staticUrl: UrlListDto? = null,
    @SerialName("animate_url") val animateUrl: UrlListDto? = null,
    @SerialName("width") val width: Int = 0,
    @SerialName("height") val height: Int = 0,
    @SerialName("keywords") val keywords: List<String> = emptyList(),
)

/** TikTok returns URLs as a candidate list; the first entry is the primary CDN. */
@Serializable
data class UrlListDto(
    @SerialName("url_list") val urlList: List<String> = emptyList(),
    @SerialName("width") val width: Int = 0,
    @SerialName("height") val height: Int = 0,
) {
    val primary: String? get() = urlList.firstOrNull()
}
