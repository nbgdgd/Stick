package com.stick.stickersource.giphy.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTOs for the Giphy Stickers API. Giphy stickers are animated and transparent,
 * which makes them a practical, genuinely-working backend for the app's keyword
 * sticker search (TikTok exposes no public sticker-catalog API).
 */
@Serializable
data class GiphySearchResponse(
    val data: List<GiphyGif> = emptyList(),
    val meta: GiphyMeta? = null,
)

@Serializable
data class GiphyMeta(val status: Int = 0, val msg: String = "")

@Serializable
data class GiphyGif(
    val id: String = "",
    val title: String = "",
    val images: GiphyImages? = null,
)

@Serializable
data class GiphyImages(
    val original: GiphyRendition? = null,
    @SerialName("fixed_width") val fixedWidth: GiphyRendition? = null,
    @SerialName("fixed_width_small") val fixedWidthSmall: GiphyRendition? = null,
)

@Serializable
data class GiphyRendition(
    val url: String = "",
    val webp: String = "",
    val width: String = "0",
    val height: String = "0",
) {
    val widthPx: Int get() = width.toIntOrNull() ?: 0
    val heightPx: Int get() = height.toIntOrNull() ?: 0
}
