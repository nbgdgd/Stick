package com.stick.app.domain.usecase

import com.stick.app.data.repository.StickerRepository
import com.stick.app.domain.converter.MediaConverter
import com.stick.core.model.RemoteSticker
import com.stick.core.result.StickResult
import com.stick.stickersource.StickerSource
import com.stick.stickersource.StickerSourceRegistry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
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

    /**
     * Stream stickers found in the video's comments, as they arrive.
     *
     * Tries every comment-capable source in registration order and stops at the
     * first one that actually yields something. The page scraper is registered
     * first because it can reach comments the public API hides, but it depends on
     * TikTok's markup — so when it comes up empty the API source still runs and
     * the result is never worse than the API alone.
     */
    fun scanComments(video: com.stick.core.model.TikTokVideoRef): Flow<StickResult<RemoteSticker>> = flow {
        val sources = registry.withCapability(StickerSource.Capability.SCRAPE_COMMENTS)
        if (sources.isEmpty()) {
            emit(StickResult.Failure(com.stick.core.result.StickError.Unsupported("No comment source")))
            return@flow
        }

        var lastFailure: StickResult.Failure? = null
        for (source in sources) {
            var emitted = 0
            source.stickersFromComments(video)
                .catch { /* try the next source instead of failing the whole scan */ }
                .collect { result ->
                    when (result) {
                        is StickResult.Success -> {
                            emitted++
                            emit(result)
                        }
                        is StickResult.Failure -> lastFailure = result
                    }
                }
            if (emitted > 0) return@flow
        }
        // Nothing anywhere: surface the last real error, if there was one.
        lastFailure?.let { emit(it) }
    }

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
