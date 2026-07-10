package com.stick.stickersource.tiktok.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTOs for the tikwm.com TikTok proxy.
 *
 * TikTok's own web endpoints require request signing (`X-Bogus`/`msToken`), so a
 * plain client cannot read comments. tikwm performs that signing server-side and
 * returns plain JSON, which is what makes the comment-scraping flow actually work.
 *
 * As always, these shapes are third-party and могут change — they are confined to
 * this package and mapped to the domain model in exactly one place
 * ([com.stick.stickersource.tiktok.TikTokMapper]).
 */
@Serializable
data class TikwmVideoResponse(
    val code: Int = 0,
    val msg: String? = null,
    val data: TikwmVideoData? = null,
)

@Serializable
data class TikwmVideoData(
    /** The numeric aweme (video) id. */
    val id: String = "",
    val title: String = "",
    val author: TikwmAuthor? = null,
)

@Serializable
data class TikwmAuthor(
    @SerialName("unique_id") val uniqueId: String = "",
    val nickname: String = "",
)

@Serializable
data class TikwmCommentResponse(
    val code: Int = 0,
    val msg: String? = null,
    val data: TikwmCommentData? = null,
)

@Serializable
data class TikwmCommentData(
    val comments: List<TikwmComment> = emptyList(),
    val cursor: Long = 0,
    val total: Int = 0,
    @SerialName("hasMore") val hasMore: Boolean = false,
)

@Serializable
data class TikwmComment(
    val id: String = "",
    val text: String = "",
    /** Comment stickers/images arrive as a list of direct CDN URLs. */
    val images: List<String> = emptyList(),
    val user: TikwmCommentUser? = null,
)

@Serializable
data class TikwmCommentUser(
    val nickname: String = "",
    @SerialName("unique_id") val uniqueId: String = "",
)
