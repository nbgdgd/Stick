package com.stick.stickersource.giphy

import com.stick.stickersource.giphy.dto.GiphySearchResponse
import retrofit2.http.GET
import retrofit2.http.Query

/** Giphy Stickers endpoints. Only the two needed for browse + search. */
interface GiphyApi {

    @GET("v1/stickers/search")
    suspend fun search(
        @Query("api_key") apiKey: String,
        @Query("q") query: String,
        @Query("limit") limit: Int = 30,
        @Query("offset") offset: Int = 0,
        @Query("rating") rating: String = "pg-13",
    ): GiphySearchResponse

    @GET("v1/stickers/trending")
    suspend fun trending(
        @Query("api_key") apiKey: String,
        @Query("limit") limit: Int = 30,
        @Query("offset") offset: Int = 0,
        @Query("rating") rating: String = "pg-13",
    ): GiphySearchResponse

    companion object {
        const val BASE_URL = "https://api.giphy.com/"
    }
}
