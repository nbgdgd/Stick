package com.stick.stickersource.giphy

import com.stick.stickersource.tiktok.AssetDownloader
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

/** Assembles a wired [GiphyStickerSource]. */
object GiphySourceFactory {

    /**
     * Public Giphy API key. Giphy issues these for free at developers.giphy.com;
     * swap in your own to lift rate limits. Kept here so switching keys/providers
     * is a one-line change.
     */
    const val DEFAULT_API_KEY = "GlVGYHkr3WSBnllca54iNt0yFbjz7L65"

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    fun create(downloadDir: File, apiKey: String = DEFAULT_API_KEY): GiphyStickerSource {
        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
        val retrofit = Retrofit.Builder()
            .baseUrl(GiphyApi.BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        return GiphyStickerSource(
            api = retrofit.create(GiphyApi::class.java),
            downloader = AssetDownloader(client, downloadDir),
            apiKey = apiKey,
        )
    }
}
