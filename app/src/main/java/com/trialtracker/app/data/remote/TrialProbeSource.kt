package com.trialtracker.app.data.remote

import com.trialtracker.app.data.model.Deal
import com.trialtracker.app.data.parse.TrialTextExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Checks a service's own pricing page for its current free-trial length.
 *
 * This is the automatic half of the trial data. It reads the page the service
 * publishes about its own subscription — not the Play Store listing, which both
 * fails to state the trial in its description (1 of 20 catalog apps) and is
 * off-limits to automated access under Play's terms.
 *
 * Coverage is honest about itself: 8 of the 20 catalog services state the trial
 * in text a fetch can see. The rest render the number client-side, so they stay
 * hand-verified and are labelled as such in the UI.
 */
class TrialProbeSource(private val client: OkHttpClient) {

    data class Result(
        val days: Int,
        val phrase: String,
        val sourceUrl: String,
    )

    suspend fun probe(deal: Deal): Result? = withContext(Dispatchers.IO) {
        candidateUrls(deal.deepLink).firstNotNullOfOrNull { url ->
            val body = fetch(url) ?: return@firstNotNullOfOrNull null
            val finding = TrialTextExtractor.extract(TrialTextExtractor.htmlToText(body))
            finding?.let { Result(days = it.days, phrase = it.phrase, sourceUrl = url) }
        }
    }

    /**
     * The catalog link is usually the marketing page; the trial is as often on a
     * dedicated pricing page. Both are tried, in order, and the first page that
     * states a length wins.
     */
    private fun candidateUrls(deepLink: String): List<String> {
        val url = deepLink.toHttpUrlOrNull() ?: return emptyList()
        val root = "${url.scheme}://${url.host}"
        return listOf(deepLink, "$root/pricing", "$root/plans", "$root/premium").distinct()
    }

    private fun fetch(url: String): String? = runCatching {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", BROWSER_UA)
            .header("Accept-Language", "en-US,en;q=0.9,ru;q=0.8")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val body = response.body?.string()
            // A near-empty response is a redirect stub or a bot wall, not a page.
            if (body == null || body.length < MIN_PAGE_BYTES) null else body
        }
    }.getOrNull()

    private companion object {
        // Pricing pages serve different markup to unknown clients; a browser UA
        // gets the same public page a user would see.
        const val BROWSER_UA =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/120.0.0.0 Mobile Safari/537.36"
        const val MIN_PAGE_BYTES = 500
    }
}
