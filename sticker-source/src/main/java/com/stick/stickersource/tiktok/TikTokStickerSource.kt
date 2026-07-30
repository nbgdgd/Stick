package com.stick.stickersource.tiktok

import com.stick.core.model.CatalogQuery
import com.stick.core.model.RemoteSticker
import com.stick.core.model.TikTokVideoRef
import com.stick.core.result.StickError
import com.stick.core.result.StickResult
import com.stick.stickersource.StickerSource
import com.stick.stickersource.StickerSource.Capability
import com.stick.stickersource.model.DownloadedAsset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Acquires stickers from TikTok comments via TikTok's own web comment endpoint.
 *
 * The endpoint returns comments without signing and has no aggressive rate limit,
 * so pagination is fast and finds far more comment stickers than the previous
 * proxy. Every image attached to a comment (static photo or animated sticker) is
 * surfaced. Catalog search is served by a different source (Giphy).
 */
class TikTokStickerSource(
    private val api: TikTokApi,
    private val downloader: AssetDownloader,
    private val httpClient: OkHttpClient,
    private val maxCommentPages: Int = 40,
    /**
     * Smaller pages measurably return *more* comments overall: TikTok advances the
     * cursor by `count` and filters within that window, so 20 surfaces comments a
     * 50-wide window drops.
     */
    private val pageSize: Int = 20,
) : StickerSource {

    override val id: String = TikTokMapper.SOURCE_ID
    override val displayName: String = "TikTok"
    override val capabilities: Set<Capability> = setOf(
        Capability.RESOLVE_VIDEO,
        Capability.SCRAPE_COMMENTS,
        Capability.DOWNLOAD,
    )

    override suspend fun resolveVideo(rawInput: String): StickResult<TikTokVideoRef> {
        val url = extractUrl(rawInput)
        parseId(url)?.let { return StickResult.Success(refFor(it, url)) }

        // Short share link (vm./vt.tiktok.com) — follow the redirect to the real URL.
        if (SHORT_LINK_REGEX.containsMatchIn(url)) {
            return resolveShortLink(url)
        }
        return StickResult.Failure(StickError.Unsupported("Unrecognised TikTok link"))
    }

    private suspend fun resolveShortLink(url: String): StickResult<TikTokVideoRef> =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(url).header("User-Agent", BROWSER_UA).get().build()
                httpClient.newCall(request).execute().use { response ->
                    val finalUrl = response.request.url.toString()
                    parseId(finalUrl)?.let { StickResult.Success(refFor(it, finalUrl)) }
                        ?: StickResult.Failure(StickError.NotFound("Link did not resolve to a video"))
                }
            } catch (t: Throwable) {
                StickResult.Failure(StickError.Network("Failed to resolve short link", t))
            }
        }

    override fun stickersFromComments(
        video: TikTokVideoRef,
    ): Flow<StickResult<RemoteSticker>> = flow {
        var cursor = 0L
        var page = 0
        var emittedAny = false
        var replyThreads = 0
        val seen = HashSet<String>()

        suspend fun emitStickers(comment: com.stick.stickersource.tiktok.dto.CommentDto) {
            for (sticker in TikTokMapper.stickersFromComment(comment, video.canonicalUrl)) {
                // De-duplicate on the CDN asset id, not the full URL: the same
                // sticker comes back with different signature/expiry params (and
                // different CDN suffixes), which URL-based de-dup counted as new.
                if (seen.add(assetKey(sticker.downloadUrl))) {
                    emittedAny = true
                    emit(StickResult.Success(sticker))
                }
            }
        }

        while (page < maxCommentPages) {
            val response = try {
                api.comments(awemeId = video.videoId, count = pageSize, cursor = cursor)
            } catch (t: Throwable) {
                if (!emittedAny) emit(StickResult.Failure(StickError.from(t)))
                return@flow
            }

            if (response.comments.isEmpty() && !emittedAny && response.statusCode != 0) {
                emit(StickResult.Failure(StickError.NotFound(response.statusMsg.ifBlank { "No comments" })))
                return@flow
            }

            for (comment in response.comments) {
                emitStickers(comment)
                // Comment stickers frequently sit in replies. Page through ALL of
                // them — a single page dropped stickers on busy threads.
                if (comment.replyCount > 0 && replyThreads < MAX_REPLY_THREADS) {
                    replyThreads++
                    var replyCursor = 0L
                    var replyPage = 0
                    while (replyPage < MAX_REPLY_PAGES) {
                        val replies = runCatching {
                            api.replies(
                                commentId = comment.id,
                                awemeId = video.videoId,
                                count = REPLY_PAGE_SIZE,
                                cursor = replyCursor,
                            )
                        }.getOrNull() ?: break
                        replies.comments.forEach { emitStickers(it) }
                        if (replies.hasMore != 1 || replies.comments.isEmpty()) break
                        replyCursor = replies.cursor
                        replyPage++
                    }
                }
            }

            if (response.hasMore != 1 || response.comments.isEmpty()) break
            cursor = response.cursor
            page++
        }
    }

    override suspend fun searchCatalog(query: CatalogQuery): StickResult<List<RemoteSticker>> =
        StickResult.Success(emptyList())

    override suspend fun download(
        sticker: RemoteSticker,
        onProgress: (Float) -> Unit,
    ): StickResult<DownloadedAsset> = downloader.download(sticker, onProgress)

    // --- URL helpers --------------------------------------------------------

    private fun extractUrl(rawInput: String): String {
        val input = rawInput.trim()
        URL_REGEX.find(input)?.let { return it.value }
        if (BARE_ID_REGEX.matches(input)) return "https://www.tiktok.com/video/$input"
        return input
    }

    private fun parseId(url: String): String? =
        VIDEO_ID_REGEX.find(url)?.groupValues?.getOrNull(1)

    private fun refFor(id: String, url: String): TikTokVideoRef =
        TikTokVideoRef(videoId = id, authorId = "", canonicalUrl = url)

    /**
     * Identity of a sticker asset: the 32-hex object name in the CDN path. Stable
     * across signature params, resolutions and CDN hosts, unlike the full URL.
     */
    private fun assetKey(url: String): String =
        ASSET_ID_REGEX.find(url)?.groupValues?.get(1) ?: url.substringBefore('?')

    private companion object {
        const val MAX_REPLY_THREADS = 200
        const val MAX_REPLY_PAGES = 20
        const val REPLY_PAGE_SIZE = 50
        val ASSET_ID_REGEX = Regex("""/([0-9a-f]{32})""")
        const val BROWSER_UA =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/120.0.0.0 Mobile Safari/537.36"
        val URL_REGEX = Regex(
            """https?://(?:www\.|m\.|vm\.|vt\.)?tiktok\.com/\S+""",
            RegexOption.IGNORE_CASE,
        )
        val VIDEO_ID_REGEX = Regex("""(?:/video/|/v/)(\d{6,25})""")
        val SHORT_LINK_REGEX = Regex("""(?:vm|vt)\.tiktok\.com/""", RegexOption.IGNORE_CASE)
        val BARE_ID_REGEX = Regex("""\d{6,25}""")
    }
}
