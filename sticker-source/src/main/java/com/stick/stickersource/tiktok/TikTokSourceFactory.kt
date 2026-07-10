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
 * the app's DI module trivial and makes it obvious what a "TikTok backend swap"
 * touches: this factory and the classes it references.
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
            urlResolver = TikTokUrlResolver(client),
            downloader = AssetDownloader(client, downloadDir),
        )
    }

    private fun buildHttpClient(enableLogging: Boolean): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .addInterceptor(defaultHeaders())

        if (enableLogging) {
            builder.addInterceptor(
                okhttp3.logging.HttpLoggingInterceptor().apply {
                    level = okhttp3.logging.HttpLoggingInterceptor.Level.BASIC
                },
            )
        }
        return builder.build()
    }

    /**
     * TikTok's web endpoints reject requests that don't look like a browser.
     * Centralising the headers here means a change in their bot-checks is a
     * one-line edit.
     */
    private fun defaultHeaders() = Interceptor { chain ->
        val request = chain.request().newBuilder()
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
            )
            .header("Referer", "https://www.tiktok.com/")
            .header("Accept", "application/json, text/plain, */*")
            .build()
        chain.proceed(request)
    }
}
