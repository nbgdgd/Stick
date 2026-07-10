package com.stick.stickersource.tiktok

import com.stick.stickersource.tiktok.dto.CommentListDto
import com.stick.stickersource.tiktok.dto.StickerCatalogDto
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Retrofit description of the (undocumented) TikTok web endpoints used to obtain
 * comment stickers and the sticker catalog.
 *
 * This is the *only* place endpoint paths and query parameters live. If TikTok
 * renames a path, this file — and possibly [TikTokMapper] — is all that changes.
 */
interface TikTokApi {

    /**
     * Paginated comment list for a video.
     *
     * @param awemeId numeric video id from [TikTokVideoRef].
     * @param cursor  pagination cursor; `0` for the first page.
     * @param count   page size (TikTok caps this around 50).
     */
    @GET("api/comment/list/")
    suspend fun comments(
        @Query("aweme_id") awemeId: String,
        @Query("cursor") cursor: Long = 0,
        @Query("count") count: Int = 50,
        @Query("aid") appId: Int = DEFAULT_AID,
    ): CommentListDto

    /**
     * Keyword search over the comment-sticker catalog. When TikTok does not
     * expose search, callers page the full catalog and filter locally — see
     * [com.stick.stickersource.tiktok.TikTokStickerSource.searchCatalog].
     */
    @GET("api/comment/sticker/list/")
    suspend fun catalog(
        @Query("keyword") keyword: String,
        @Query("cursor") cursor: Long = 0,
        @Query("count") count: Int = 30,
        @Query("aid") appId: Int = DEFAULT_AID,
    ): StickerCatalogDto

    companion object {
        const val BASE_URL = "https://www.tiktok.com/"
        /** Web application id TikTok's own site sends with API calls. */
        const val DEFAULT_AID = 1988
    }
}
