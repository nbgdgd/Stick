package com.stick.stickersource.clipboard

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
import com.stick.stickersource.model.DownloadedAsset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Imports a sticker whose bytes the app has already copied out of the clipboard
 * to a temp file. The app owns clipboard access (it needs a `Context`); this
 * source only turns the resulting file into a [RemoteSticker], keeping the module
 * framework-free.
 */
class ClipboardStickerSource : StickerSource {

    override val id: String = "clipboard"
    override val displayName: String = "Clipboard"
    override val capabilities: Set<Capability> =
        setOf(Capability.CLIPBOARD_IMPORT, Capability.DOWNLOAD)

    fun fromClipboardFile(path: String, format: StickerFormat): RemoteSticker =
        RemoteSticker(
            id = "clip-${System.currentTimeMillis()}",
            sourceId = id,
            name = "Clipboard sticker",
            downloadUrl = path,
            format = format,
            info = MediaInfo(format = format),
            origin = StickerOrigin.Clipboard,
        )

    override suspend fun resolveVideo(rawInput: String): StickResult<TikTokVideoRef> =
        StickResult.Failure(StickError.Unsupported("Clipboard source cannot resolve videos"))

    override fun stickersFromComments(video: TikTokVideoRef): Flow<StickResult<RemoteSticker>> =
        emptyFlow()

    override suspend fun searchCatalog(query: CatalogQuery): StickResult<List<RemoteSticker>> =
        StickResult.Success(emptyList())

    override suspend fun download(
        sticker: RemoteSticker,
        onProgress: (Float) -> Unit,
    ): StickResult<DownloadedAsset> {
        onProgress(1f)
        return StickResult.Success(
            DownloadedAsset(source = sticker, localPath = sticker.downloadUrl, info = sticker.info),
        )
    }
}
