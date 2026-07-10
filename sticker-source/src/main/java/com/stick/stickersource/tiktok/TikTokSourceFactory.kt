package com.stick.stickersource.tiktok

import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Assembles a fully-wired [TikTokStickerSource]. Isolating construction here keeps
 * the app's DI module trivial and makes it obvious what a backend swap touches:
 * this factory and the classes it references.
 */
object TikTokSourceFactory {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    fun create(
        downloadDir: File,
        enableLogging: Boolean = false,
    ): TikTokStickerSource {
        val client = buildHttpClient(enableLogging)
        val retrofit = Retrofit.Builder()
            .baseUrl(TikTokApi.BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

        val api = retrofit.create(TikTokApi::class.java)
        return TikTokStickerSource(
            api = api,
            downloader = AssetDownloader(client, downloadDir),
        )
    }

    private fun buildHttpClient(enableLogging: Boolean): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(40, TimeUnit.SECONDS)
            .followRedirects(true)
            .addInterceptor(defaultHeaders())
            // tikwm free tier: 1 req/s. Space API calls out so pagination works.
            .addInterceptor(RateLimitInterceptor(minIntervalMs = 1_200, hostMatch = "tikwm"))

        if (enableLogging) {
            builder.addInterceptor(
                okhttp3.logging.HttpLoggingInterceptor().apply {
                    level = okhttp3.logging.HttpLoggingInterceptor.Level.BASIC
                },
            )
        }
        return builder.build()
    }

    private fun defaultHeaders() = Interceptor { chain ->
        val request = chain.request().newBuilder()
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
            )
            .header("Accept", "application/json, text/plain, */*")
            .build()
        chain.proceed(request)
    }
}
