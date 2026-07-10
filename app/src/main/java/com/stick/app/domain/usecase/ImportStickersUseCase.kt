package com.stick.app.domain.usecase

import com.stick.app.data.repository.StickerRepository
import com.stick.app.domain.converter.MediaConverter
import com.stick.core.model.RemoteSticker
import com.stick.core.result.StickResult
import com.stick.stickersource.StickerSource
import com.stick.stickersource.StickerSourceRegistry
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Coordinates the acquisition module and the library for the import flow. Keeps
 * the [com.stick.app.ui.screen.import.ImportViewModel] free of source/registry
 * plumbing and makes the flow unit-testable with a fake registry.
 */
class ImportStickersUseCase @Inject constructor(
    private val registry: StickerSourceRegistry,
    private val repository: StickerRepository,
    private val converter: MediaConverter,
) {
    /** Resolve any TikTok link/share/short-link into a canonical video reference. */
    suspend fun resolve(rawInput: String) =
        primary(StickerSource.Capability.RESOLVE_VIDEO)?.resolveVideo(rawInput)
            ?: StickResult.Failure(com.stick.core.result.StickError.Unsupported("No source can resolve links"))

    /** Stream animated stickers found in the video's comments, as they arrive. */
    fun scanComments(video: com.stick.core.model.TikTokVideoRef): Flow<StickResult<RemoteSticker>> =
        (primary(StickerSource.Capability.SCRAPE_COMMENTS)
            ?: error("No comment-scraping source registered"))
            .stickersFromComments(video)

    /**
     * Download [stickers] and persist them, de-duplicating and probing accurate
     * metadata. Reports overall progress across the batch through [onEach].
     */
    suspend fun downloadAndSave(
        stickers: List<RemoteSticker>,
        onEach: (index: Int, total: Int, sticker: RemoteSticker) -> Unit = { _, _, _ -> },
    ): List<StickResult<Unit>> {
        val results = ArrayList<StickResult<Unit>>(stickers.size)
        stickers.forEachIndexed { index, sticker ->
            onEach(index, stickers.size, sticker)
            val source = registry.byId(sticker.sourceId)
                ?: registry.primaryFor(StickerSource.Capability.DOWNLOAD)
            val downloaded = source?.download(sticker)
                ?: StickResult.Failure(com.stick.core.result.StickError.Unsupported("No downloader"))

            results += when (downloaded) {
                is StickResult.Failure -> downloaded
                is StickResult.Success -> {
                    // Probe accurate FPS/frames/duration before saving.
                    val probed = converter.probe(downloaded.value.localPath).getOrNull()
                    val enriched = if (probed != null) {
                        downloaded.value.copy(info = probed.copy(fileSizeBytes = probed.fileSizeBytes))
                    } else {
                        downloaded.value
                    }
                    repository.save(enriched)
                    StickResult.Success(Unit)
                }
            }
        }
        return results
    }

    private fun primary(capability: StickerSource.Capability): StickerSource? =
        registry.primaryFor(capability)
}
