package com.stick.stickersource.local

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
import java.io.File

/**
 * Imports an animation the user already has on disk. Demonstrates that a source
 * with a completely different backend plugs into the exact same interface — no
 * networking, only the [Capability.LOCAL_FILE_IMPORT] and [Capability.DOWNLOAD]
 * (here: a local copy) capabilities are advertised.
 */
class LocalFileStickerSource : StickerSource {

    override val id: String = "local-file"
    override val displayName: String = "Local file"
    override val capabilities: Set<Capability> =
        setOf(Capability.LOCAL_FILE_IMPORT, Capability.DOWNLOAD)

    /** Build a [RemoteSticker] describing a file the user picked. */
    fun fromFile(path: String): StickResult<RemoteSticker> {
        val file = File(path)
        if (!file.exists()) return StickResult.Failure(StickError.NotFound("No such file: $path"))
        // Tolerate unknown extensions — decoders sniff content anyway — so import
        // never fails on an odd file name.
        val format = StickerFormat.fromExtension(file.extension) ?: StickerFormat.PNG

        return StickResult.Success(
            RemoteSticker(
                id = file.nameWithoutExtension,
                sourceId = id,
                name = file.name,
                downloadUrl = file.toURI().toString(),
                format = format,
                info = MediaInfo(fileSizeBytes = file.length(), format = format),
                origin = StickerOrigin.LocalFile(path),
            ),
        )
    }

    override suspend fun resolveVideo(rawInput: String): StickResult<TikTokVideoRef> =
        StickResult.Failure(StickError.Unsupported("Local source cannot resolve videos"))

    override fun stickersFromComments(video: TikTokVideoRef): Flow<StickResult<RemoteSticker>> =
        emptyFlow()

    override suspend fun searchCatalog(query: CatalogQuery): StickResult<List<RemoteSticker>> =
        StickResult.Success(emptyList())

    override suspend fun download(
        sticker: RemoteSticker,
        onProgress: (Float) -> Unit,
    ): StickResult<DownloadedAsset> {
        val path = sticker.origin.let { if (it is StickerOrigin.LocalFile) it.path else null }
            ?: return StickResult.Failure(StickError.NotFound("Missing local path"))
        onProgress(1f)
        return StickResult.Success(
            DownloadedAsset(source = sticker, localPath = path, info = sticker.info),
        )
    }
}
