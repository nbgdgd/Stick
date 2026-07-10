package com.stick.stickersource.tiktok

import com.stick.stickersource.tiktok.dto.CommentListResponse
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * TikTok's web comment endpoint. Reading comments does not require signing, so a
 * plain browser-like request works. This is the only place the endpoint path and
 * params live.
 */
interface TikTokApi {

    @GET("api/comment/list/")
    suspend fun comments(
        @Query("aweme_id") awemeId: String,
        @Query("count") count: Int = 50,
        @Query("cursor") cursor: Long = 0,
        @Query("aid") appId: Int = DEFAULT_AID,
    ): CommentListResponse

    companion object {
        const val BASE_URL = "https://www.tiktok.com/"
        /** Web application id TikTok's own site sends. */
        const val DEFAULT_AID = 1988
    }
}
