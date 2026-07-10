package com.stick.stickersource.tiktok

import com.stick.core.model.CatalogQuery
import com.stick.core.model.RemoteSticker
import com.stick.core.model.TikTokVideoRef
import com.stick.core.result.StickError
import com.stick.core.result.StickResult
import com.stick.stickersource.StickerSource
import com.stick.stickersource.StickerSource.Capability
import com.stick.stickersource.model.DownloadedAsset
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
        val response = api.resolve(rawInput.trim())
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
        val seen = HashSet<String>()
        while (page < maxCommentPages) {
            val response = try {
                api.comments(awemeId = video.videoId, count = pageSize, cursor = cursor)
            } catch (t: Throwable) {
                emit(StickResult.Failure(StickError.from(t)))
                return@flow
            }

            val data = response.data
            if (response.code != 0 || data == null) {
                emit(StickResult.Failure(StickError.NotFound(response.msg ?: "No comments")))
                return@flow
            }

            for (comment in data.comments) {
                for (sticker in TikTokMapper.stickersFromComment(comment, video.canonicalUrl)) {
                    if (seen.add(sticker.downloadUrl)) emit(StickResult.Success(sticker))
                }
            }

            val more = data.hasMore && data.comments.isNotEmpty() && data.cursor > cursor
            if (!more) break
            cursor = data.cursor
            page++
        }
    }

    override suspend fun searchCatalog(query: CatalogQuery): StickResult<List<RemoteSticker>> =
        StickResult.Success(emptyList())

    override suspend fun download(
        sticker: RemoteSticker,
        onProgress: (Float) -> Unit,
    ): StickResult<DownloadedAsset> = downloader.download(sticker, onProgress)
}
