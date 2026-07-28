package com.trialtracker.app.data.remote

import com.trialtracker.app.BuildConfig
import com.trialtracker.app.data.model.DealCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Downloads the deals catalog so offers can be updated without shipping a release.
 *
 * The endpoint is a plain static JSON file (GitHub Pages / raw.githubusercontent /
 * Firebase Hosting all work). Only public catalog data is fetched — the request
 * carries nothing about the user or their installed apps.
 *
 * NOTE: scraping third-party promo pages is intentionally *not* implemented. The
 * seam for it is this class: add another source behind the same suspend function.
 * It is unreliable and can violate the terms of the services being scraped, so it
 * stays out of the MVP.
 */
open class CatalogRemoteSource(
    private val json: Json,
    private val client: OkHttpClient,
    private val url: String = BuildConfig.CATALOG_URL,
) {

    open suspend fun fetch(): Result<DealCatalog> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(url).header("Accept", "application/json").build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code}")
                val body = response.body?.string().orEmpty()
                json.decodeFromString(DealCatalog.serializer(), body)
            }
        }
    }
}
