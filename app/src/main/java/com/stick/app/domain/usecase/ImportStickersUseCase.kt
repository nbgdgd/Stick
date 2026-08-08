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
     * Runs **every** comment-capable source and emits the de-duplicated union of
     * what they find. The page scraper and the public API each reach comments the
     * other misses (the scraper uses TikTok's own signed requests to page past the
     * anonymous API cap; the API returns replies the scraper never scrolls to), so
     * merging them yields strictly more stickers than either alone. Duplicates —
     * the same asset seen by both sources — are collapsed by their asset id.
     */
    fun scanComments(video: com.stick.core.model.TikTokVideoRef): Flow<StickResult<RemoteSticker>> = flow {
        val sources = registry.withCapability(StickerSource.Capability.SCRAPE_COMMENTS)
        if (sources.isEmpty()) {
            emit(StickResult.Failure(com.stick.core.result.StickError.Unsupported("No comment source")))
            return@flow
        }

        val seen = HashSet<String>()
        var emitted = 0
        var lastFailure: StickResult.Failure? = null
        for (source in sources) {
            source.stickersFromComments(video)
                .catch { /* a broken source must not abort the others */ }
                .collect { result ->
                    when (result) {
                        is StickResult.Success -> {
                            if (seen.add(dedupKey(result.value))) {
                                emitted++
                                emit(result)
                            }
                        }
                        is StickResult.Failure -> lastFailure = result
                    }
                }
        }
        // Only surface an error when nothing at all came through.
        if (emitted == 0) lastFailure?.let { emit(it) }
    }

    /**
     * Identity used to collapse the same sticker seen by more than one source.
     * TikTok asset URLs carry a stable 32-hex id; when present it is the reliable
     * key even though the two sources build slightly different URLs around it.
     * Falls back to the download URL, then the source-local id.
     */
    private fun dedupKey(sticker: RemoteSticker): String =
        ASSET_ID.find(sticker.downloadUrl)?.groupValues?.get(1)
            ?: sticker.downloadUrl.ifBlank { sticker.id }

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

    private companion object {
        /** The 32-hex asset id embedded in every TikTok sticker/image URL. */
        val ASSET_ID = Regex("/([0-9a-f]{32})")
    }
}
