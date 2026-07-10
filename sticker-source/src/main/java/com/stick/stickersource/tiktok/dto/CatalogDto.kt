package com.stick.stickersource.tiktok.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTOs for the sticker *catalog* search endpoint
 * (`/api/comment/sticker/list/` — the same pool the comment composer draws from).
 *
 * See the module README, section "Catalog discovery", for how this endpoint was
 * identified and the fallback strategy when it is unavailable. Same stability
 * caveats as [CommentListDto] apply.
 */
@Serializable
data class StickerCatalogDto(
    @SerialName("sticker_list") val stickers: List<StickerDto> = emptyList(),
    @SerialName("cursor") val cursor: Long = 0,
    @SerialName("has_more") val hasMore: Int = 0,
)
