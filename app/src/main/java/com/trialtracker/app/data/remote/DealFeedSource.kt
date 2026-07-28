package com.trialtracker.app.data.remote

import com.trialtracker.app.data.model.Deal
import com.trialtracker.app.data.parse.AtomFeedParser
import com.trialtracker.app.data.parse.RedditDealMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * A live source of real, dated offers.
 *
 * There is no API anywhere that lists which apps currently run a free trial, but
 * price drops and limited-time "free" promos *are* published continuously — the
 * Play-deal subreddits carry them, every post links the Play listing, and Reddit
 * exposes each subreddit as an Atom feed. That feed is a documented, public
 * interface, which is why it is used here instead of scraping the Play Store (a
 * ToS violation) or a third-party promo page (brittle and unstable).
 *
 * What comes out is discounts. Free trials still come from the curated catalog —
 * nothing publishes those in machine-readable form, and pretending otherwise
 * would just mean inventing data.
 */
class DealFeedSource(private val client: OkHttpClient) {

    data class Feed(
        val subreddit: String,
        val sourceKey: String,
        val label: String,
        val limit: Int = 100,
    ) {
        val url: String get() = "https://www.reddit.com/r/$subreddit/new/.rss?limit=$limit"
    }

    suspend fun fetch(feed: Feed): Result<List<Deal>> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(feed.url)
                // Reddit rejects generic clients; a descriptive UA is required.
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/atom+xml, application/xml;q=0.9")
                .build()

            val xml = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code} for r/${feed.subreddit}")
                response.body?.string().orEmpty()
            }

            AtomFeedParser.parse(xml)
                .mapIndexedNotNull { index, entry ->
                    RedditDealMapper.map(entry, feed.sourceKey, index)
                }
                // The same app can be posted twice within one window; keep the newest.
                .distinctBy { it.packageName }
        }
    }

    companion object {
        const val USER_AGENT = "android:com.trialtracker.app:1.0.0 (deal aggregator)"

        val DEFAULT_FEEDS = listOf(
            Feed(
                subreddit = "googleplaydeals",
                sourceKey = "googleplaydeals",
                label = "r/googleplaydeals",
            ),
            Feed(
                subreddit = "AppHookup",
                sourceKey = "apphookup",
                label = "r/AppHookup",
            ),
        )

        fun labelFor(sourceKey: String): String =
            DEFAULT_FEEDS.firstOrNull { it.sourceKey == sourceKey }?.label ?: when (sourceKey) {
                Deal.SOURCE_CATALOG -> "Проверенный каталог"
                Deal.SOURCE_PROBE -> "Страница тарифов сервиса"
                else -> sourceKey
            }
    }
}
