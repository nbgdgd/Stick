package com.stick.stickersource.tiktok

import com.stick.core.model.TikTokVideoRef
import com.stick.core.result.StickError
import com.stick.core.result.StickResult
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * Turns any of the many shapes a TikTok link can take into a canonical
 * [TikTokVideoRef]:
 *
 *  - `https://www.tiktok.com/@user/video/7212345678901234567`
 *  - `https://m.tiktok.com/v/7212345678901234567.html`
 *  - `https://vm.tiktok.com/ZM8abcdef/`  (short share link → needs a redirect hop)
 *  - a bare `7212345678901234567`
 *
 * Short links are resolved with a single HEAD request that follows redirects, so
 * the "share" import path works without opening a browser.
 */
class TikTokUrlResolver(private val httpClient: OkHttpClient) {

    private val fullUrlRegex =
        Regex("""tiktok\.com/@([\w.\-]+)/video/(\d+)""", RegexOption.IGNORE_CASE)
    private val mUrlRegex =
        Regex("""tiktok\.com/v/(\d+)""", RegexOption.IGNORE_CASE)
    private val shortLinkRegex =
        Regex("""(?:vm|vt)\.tiktok\.com/[\w]+""", RegexOption.IGNORE_CASE)
    private val bareIdRegex = Regex("""^\d{6,25}$""")

    suspend fun resolve(rawInput: String): StickResult<TikTokVideoRef> {
        val input = rawInput.trim()
        if (input.isEmpty()) {
            return StickResult.Failure(StickError.NotFound("Empty link"))
        }

        // 1) Already a full/mobile URL or a bare id — parse directly.
        parseKnownShapes(input)?.let { return StickResult.Success(it) }

        // 2) Short share link — follow the redirect once, then re-parse.
        if (shortLinkRegex.containsMatchIn(input)) {
            return resolveShortLink(input)
        }

        return StickResult.Failure(
            StickError.Unsupported("Unrecognised TikTok link: $input"),
        )
    }

    private fun parseKnownShapes(input: String): TikTokVideoRef? {
        fullUrlRegex.find(input)?.let { m ->
            val author = m.groupValues[1]
            val id = m.groupValues[2]
            return TikTokVideoRef(
                videoId = id,
                authorId = author,
                canonicalUrl = "https://www.tiktok.com/@$author/video/$id",
            )
        }
        mUrlRegex.find(input)?.let { m ->
            val id = m.groupValues[1]
            return TikTokVideoRef(id, authorId = "", canonicalUrl = "https://m.tiktok.com/v/$id.html")
        }
        if (bareIdRegex.matches(input)) {
            return TikTokVideoRef(input, authorId = "", canonicalUrl = "https://www.tiktok.com/video/$input")
        }
        return null
    }

    private fun resolveShortLink(input: String): StickResult<TikTokVideoRef> {
        val url = if (input.startsWith("http")) input else "https://$input"
        return try {
            // Follow redirects (OkHttpClient defaults to followRedirects = true)
            // and read the final URL, which is the canonical video URL.
            val request = Request.Builder().url(url).head().build()
            httpClient.newCall(request).execute().use { response ->
                val finalUrl = response.request.url.toString()
                parseKnownShapes(finalUrl)?.let { StickResult.Success(it) }
                    ?: StickResult.Failure(
                        StickError.NotFound("Short link did not resolve to a video: $finalUrl"),
                    )
            }
        } catch (e: IOException) {
            StickResult.Failure(StickError.Network("Failed to resolve short link", e))
        }
    }
}
