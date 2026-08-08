package com.stick.stickersource.tiktok.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTOs for TikTok's own web comment endpoint
 * (`www.tiktok.com/api/comment/list/?aweme_id=…`).
 *
 * This endpoint returns comment JSON without request signing and, unlike the
 * tikwm proxy, has no aggressive 1-request/second limit — so pagination is fast
 * and finds far more comment stickers. Comment stickers/images live in
 * `image_list[].origin_url`.
 *
 * Shapes are undocumented; they are confined here and mapped in exactly one place
 * ([com.stick.stickersource.tiktok.TikTokMapper]).
 */
@Serializable
data class CommentListResponse(
    val comments: List<CommentDto> = emptyList(),
    val cursor: Long = 0,
    @SerialName("has_more") val hasMore: Int = 0,
    val total: Int = 0,
    @SerialName("status_code") val statusCode: Int = 0,
    @SerialName("status_msg") val statusMsg: String = "",
)

@Serializable
data class CommentDto(
    @SerialName("cid") val id: String = "",
    val text: String = "",
    val user: CommentUserDto? = null,
    @SerialName("image_list") val imageList: List<CommentImageDto> = emptyList(),
    /** Animated sticker-pack stickers (the ".awebp" ones) live here, NOT image_list. */
    @SerialName("cmt_sticker_struct") val commentSticker: CommentStickerStruct? = null,
    /** Some replies are inlined here; they can carry stickers too. */
    @SerialName("reply_comment") val replyComment: List<CommentDto>? = null,
    /** Number of replies; comment stickers often live in replies. */
    @SerialName("reply_comment_total") val replyCount: Int = 0,
)

/** TikTok's animated comment-sticker (sticker pack) payload. */
@Serializable
data class CommentStickerStruct(
    val id: String = "",
    val name: String = "",
    @SerialName("animated_url") val animatedUrl: StickerUrlSet? = null,
    @SerialName("static_url") val staticUrl: StickerUrlSet? = null,
)

@Serializable
data class StickerUrlSet(
    @SerialName("high_resolution_url") val high: UrlListDto? = null,
    @SerialName("mid_resolution_url") val mid: UrlListDto? = null,
    @SerialName("low_resolution_url") val low: UrlListDto? = null,
) {
    val best: String? get() = high?.primary ?: mid?.primary ?: low?.primary
}

@Serializable
data class CommentUserDto(
    @SerialName("nickname") val nickname: String = "",
)

@Serializable
data class CommentImageDto(
    /** Full-resolution image/sticker. */
    @SerialName("origin_url") val originUrl: UrlListDto? = null,
    /** Cropped/preview variant. */
    @SerialName("crop_url") val cropUrl: UrlListDto? = null,
)

/** TikTok returns URLs as a CDN candidate list; the first entry is primary. */
@Serializable
data class UrlListDto(
    @SerialName("url_list") val urlList: List<String> = emptyList(),
) {
    val primary: String? get() = urlList.firstOrNull { it.isNotBlank() }
}
