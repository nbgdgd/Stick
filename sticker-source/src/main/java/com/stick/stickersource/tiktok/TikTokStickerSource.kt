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
 * The primary [StickerSource]: acquires animated stickers from TikTok comments
 * and the comment-sticker catalog.
 *
 * It composes small, independently testable collaborators — [TikTokUrlResolver],
 * [TikTokApi], [TikTokMapper] and [AssetDownloader] — so that a TikTok change
 * touches only the relevant collaborator. This whole class can also be replaced
 * wholesale by registering a different [StickerSource] in the registry.
 */
class TikTokStickerSource(
    private val api: TikTokApi,
    private val urlResolver: TikTokUrlResolver,
    private val downloader: AssetDownloader,
    /** Safety cap so a viral video doesn't page comments forever. */
    private val maxCommentPages: Int = 20,
) : StickerSource {

    override val id: String = TikTokMapper.COMMENT_SOURCE_ID
    override val displayName: String = "TikTok"
    override val capabilities: Set<Capability> = setOf(
        Capability.RESOLVE_VIDEO,
        Capability.SCRAPE_COMMENTS,
        Capability.SEARCH_CATALOG,
        Capability.DOWNLOAD,
    )

    override suspend fun resolveVideo(rawInput: String): StickResult<TikTokVideoRef> =
        urlResolver.resolve(rawInput)

    override fun stickersFromComments(
        video: TikTokVideoRef,
    ): Flow<StickResult<RemoteSticker>> = flow {
        var cursor = 0L
        var page = 0
        val seen = HashSet<String>()
        while (page < maxCommentPages) {
            val response = try {
                api.comments(awemeId = video.videoId, cursor = cursor)
            } catch (t: Throwable) {
                emit(StickResult.Failure(StickError.from(t)))
                return@flow
            }

            for (comment in response.comments) {
                val sticker = TikTokMapper.stickerFromComment(comment, video.canonicalUrl) ?: continue
                // De-duplicate identical stickers reused across many comments.
                if (seen.add(sticker.downloadUrl)) {
                    emit(StickResult.Success(sticker))
                }
            }

            if (response.hasMore != 1 || response.comments.isEmpty()) break
            cursor = response.cursor
            page++
        }
    }

    override suspend fun searchCatalog(query: CatalogQuery): StickResult<List<RemoteSticker>> =
        try {
            val dto = api.catalog(
                keyword = query.text,
                cursor = (query.page.toLong() * query.pageSize),
                count = query.pageSize,
            )
            val stickers = dto.stickers.mapNotNull(TikTokMapper::stickerFromCatalog)
            StickResult.Success(stickers)
        } catch (t: Throwable) {
            StickResult.Failure(StickError.from(t))
        }

    override suspend fun download(
        sticker: RemoteSticker,
        onProgress: (Float) -> Unit,
    ): StickResult<DownloadedAsset> = downloader.download(sticker, onProgress)
}
