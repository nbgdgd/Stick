package com.stick.stickersource.tiktok

import com.stick.core.model.CatalogQuery
import com.stick.core.model.RemoteSticker
import com.stick.core.model.TikTokVideoRef
import com.stick.core.result.StickError
import com.stick.core.result.StickResult
import com.stick.stickersource.StickerSource
import com.stick.stickersource.StickerSource.Capability
import com.stick.stickersource.model.DownloadedAsset
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Acquires animated stickers from TikTok comments via the tikwm proxy.
 *
 * Catalog search is intentionally NOT a capability here — TikTok exposes no public
 * sticker-catalog API, so that capability is served by a different registered
 * source (Giphy). This class stays focused on the one thing TikTok can do:
 * comment stickers.
 */
class TikTokStickerSource(
    private val api: TikTokApi,
    private val downloader: AssetDownloader,
    /** Safety cap so a viral video doesn't page comments forever. */
    private val maxCommentPages: Int = 12,
    private val pageSize: Int = 50,
) : StickerSource {

    override val id: String = TikTokMapper.SOURCE_ID
    override val displayName: String = "TikTok"
    override val capabilities: Set<Capability> = setOf(
        Capability.RESOLVE_VIDEO,
        Capability.SCRAPE_COMMENTS,
        Capability.DOWNLOAD,
    )

    override suspend fun resolveVideo(rawInput: String): StickResult<TikTokVideoRef> = try {
        val response = api.resolve(extractUrl(rawInput))
        val data = response.data
        if (response.code != 0 || data == null || data.id.isBlank()) {
            StickResult.Failure(StickError.NotFound(response.msg ?: "Could not resolve link"))
        } else {
            val author = data.author?.uniqueId.orEmpty()
            StickResult.Success(
                TikTokVideoRef(
                    videoId = data.id,
                    authorId = author,
                    canonicalUrl = if (author.isNotBlank()) {
                        "https://www.tiktok.com/@$author/video/${data.id}"
                    } else {
                        "https://www.tiktok.com/video/${data.id}"
                    },
                ),
            )
        }
    } catch (t: Throwable) {
        StickResult.Failure(StickError.from(t))
    }

    override fun stickersFromComments(
        video: TikTokVideoRef,
    ): Flow<StickResult<RemoteSticker>> = flow {
        var cursor = 0L
        var page = 0
        var emittedAny = false
        val seen = HashSet<String>()
        while (page < maxCommentPages) {
            val response = try {
                fetchWithRateLimitRetry(video.videoId, cursor)
            } catch (t: Throwable) {
                // Network error: only surface it if we haven't already found stickers.
                if (!emittedAny) emit(StickResult.Failure(StickError.from(t)))
                return@flow
            }

            val data = response?.data
            if (response == null || response.code != 0 || data == null) {
                if (!emittedAny) {
                    emit(StickResult.Failure(StickError.NotFound(response?.msg ?: "No comments")))
                }
                return@flow
            }

            for (comment in data.comments) {
                for (sticker in TikTokMapper.stickersFromComment(comment, video.canonicalUrl)) {
                    if (seen.add(sticker.downloadUrl)) {
                        emittedAny = true
                        emit(StickResult.Success(sticker))
                    }
                }
            }

            val more = data.hasMore && data.comments.isNotEmpty() && data.cursor > cursor
            if (!more) break
            cursor = data.cursor
            page++
        }
    }

    /**
     * Fetch one comment page, retrying on tikwm's `code:-1` rate-limit response.
     * The client already spaces requests, but a burst can still trip the limit;
     * a short backoff recovers instead of aborting the whole scan.
     */
    private suspend fun fetchWithRateLimitRetry(
        awemeId: String,
        cursor: Long,
    ): com.stick.stickersource.tiktok.dto.TikwmCommentResponse? {
        var attempt = 0
        while (attempt < RATE_LIMIT_RETRIES) {
            val response = api.comments(awemeId = awemeId, count = pageSize, cursor = cursor)
            val rateLimited = response.code == -1 &&
                response.msg?.contains("limit", ignoreCase = true) == true
            if (!rateLimited) return response
            attempt++
            delay(1_300)
        }
        return null
    }

    /** Pull the first usable TikTok link (or bare id) out of pasted share text. */
    private fun extractUrl(rawInput: String): String {
        val input = rawInput.trim()
        URL_REGEX.find(input)?.let { return it.value }
        if (BARE_ID_REGEX.matches(input)) return "https://www.tiktok.com/video/$input"
        return input
    }

    override suspend fun searchCatalog(query: CatalogQuery): StickResult<List<RemoteSticker>> =
        StickResult.Success(emptyList())

    override suspend fun download(
        sticker: RemoteSticker,
        onProgress: (Float) -> Unit,
    ): StickResult<DownloadedAsset> = downloader.download(sticker, onProgress)

    private companion object {
        const val RATE_LIMIT_RETRIES = 3
        val URL_REGEX = Regex(
            """https?://(?:www\.|m\.|vm\.|vt\.)?tiktok\.com/\S+""",
            RegexOption.IGNORE_CASE,
        )
        val BARE_ID_REGEX = Regex("""^\d{6,25}$""")
    }
}
