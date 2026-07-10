package com.stick.stickersource.giphy

import com.stick.core.model.CatalogQuery
import com.stick.core.model.MediaInfo
import com.stick.core.model.RemoteSticker
import com.stick.core.model.StickerFormat
import com.stick.core.model.StickerOrigin
import com.stick.core.model.TikTokVideoRef
import com.stick.core.result.StickError
import com.stick.core.result.StickResult
import com.stick.stickersource.StickerSource
import com.stick.stickersource.StickerSource.Capability
import com.stick.stickersource.giphy.dto.GiphyGif
import com.stick.stickersource.model.DownloadedAsset
import com.stick.stickersource.tiktok.AssetDownloader
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * A [StickerSource] that serves the in-app sticker catalog from Giphy's animated
 * stickers. Demonstrates the swappable design: a completely different backend
 * plugs into the same interface and only advertises the capabilities it supports
 * ([Capability.SEARCH_CATALOG] + [Capability.DOWNLOAD]).
 */
class GiphyStickerSource(
    private val api: GiphyApi,
    private val downloader: AssetDownloader,
    private val apiKey: String,
) : StickerSource {

    override val id: String = "giphy"
    override val displayName: String = "Giphy"
    override val capabilities: Set<Capability> =
        setOf(Capability.SEARCH_CATALOG, Capability.DOWNLOAD)

    override suspend fun searchCatalog(query: CatalogQuery): StickResult<List<RemoteSticker>> = try {
        val offset = query.page * query.pageSize
        val response = if (query.text.isBlank()) {
            api.trending(apiKey = apiKey, limit = query.pageSize, offset = offset)
        } else {
            api.search(apiKey = apiKey, query = query.text, limit = query.pageSize, offset = offset)
        }
        val status = response.meta?.status ?: 200
        if (status != 200) {
            StickResult.Failure(StickError.Network("Giphy error ${response.meta?.status}: ${response.meta?.msg}"))
        } else {
            StickResult.Success(response.data.mapNotNull(::toSticker))
        }
    } catch (t: Throwable) {
        StickResult.Failure(StickError.from(t))
    }

    private fun toSticker(gif: GiphyGif): RemoteSticker? {
        val original = gif.images?.original ?: return null
        val download = original.webp.ifBlank { original.url }.ifBlank { return null }
        val preview = gif.images.fixedWidthSmall?.webp
            ?.ifBlank { null }
            ?: gif.images.fixedWidth?.webp?.ifBlank { null }
            ?: download
        return RemoteSticker(
            id = gif.id,
            sourceId = id,
            name = gif.title.ifBlank { "Sticker ${gif.id}" },
            downloadUrl = download,
            previewUrl = preview,
            format = StickerFormat.WEBP_ANIMATED,
            info = MediaInfo(widthPx = original.widthPx, heightPx = original.heightPx),
            keywords = gif.title.split(' ').filter { it.isNotBlank() },
            origin = StickerOrigin.Catalog(collection = "giphy"),
        )
    }

    override suspend fun download(
        sticker: RemoteSticker,
        onProgress: (Float) -> Unit,
    ): StickResult<DownloadedAsset> = downloader.download(sticker, onProgress)

    // --- Unsupported capabilities -----------------------------------------
    override suspend fun resolveVideo(rawInput: String): StickResult<TikTokVideoRef> =
        StickResult.Failure(StickError.Unsupported("Giphy has no videos"))

    override fun stickersFromComments(video: TikTokVideoRef): Flow<StickResult<RemoteSticker>> =
        emptyFlow()
}
