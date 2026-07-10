package com.stick.stickersource.tiktok

import com.stick.stickersource.tiktok.dto.TikwmCommentResponse
import com.stick.stickersource.tiktok.dto.TikwmVideoResponse
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * TikTok data access via the tikwm.com proxy (see [com.stick.stickersource.tiktok.dto.TikwmDto]
 * for why a proxy is used instead of TikTok's signed endpoints).
 *
 * This is the only place endpoint paths/params live; swapping the proxy — or
 * moving to TikTok's signed API later — is a change to this file plus the mapper.
 */
interface TikTokApi {

    /**
     * Resolve any TikTok link (full URL, `vm.tiktok.com` short link, or a bare id)
     * into video metadata, including the numeric aweme id.
     */
    @GET("api/")
    suspend fun resolve(
        @Query("url") url: String,
        @Query("hd") hd: Int = 0,
    ): TikwmVideoResponse

    /** Paginated comment list for a video, keyed by aweme id. */
    @GET("api/comment/list/")
    suspend fun comments(
        @Query("url") awemeId: String,
        @Query("count") count: Int = 50,
        @Query("cursor") cursor: Long = 0,
    ): TikwmCommentResponse

    companion object {
        const val BASE_URL = "https://www.tikwm.com/"
    }
}
