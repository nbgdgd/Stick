package com.stick.stickersource

import com.stick.core.model.CatalogQuery
import com.stick.core.model.RemoteSticker
import com.stick.core.model.TikTokVideoRef
import com.stick.core.result.StickResult
import com.stick.stickersource.model.DownloadedAsset
import kotlinx.coroutines.flow.Flow

/**
 * The single seam between the app and *wherever stickers come from*.
 *
 * ### Why this exists
 * TikTok changes its internal endpoints and payloads frequently. By funnelling
 * every acquisition path through this interface, a breaking change on TikTok's
 * side is contained to one implementation in this module — no ViewModel, screen,
 * database or converter needs to change. Add a new backend by implementing this
 * interface and registering it in [StickerSourceRegistry].
 *
 * Every capability is optional: a source advertises what it can do via
 * [capabilities], and the app hides/greys-out UI for anything unsupported. This
 * lets a clipboard or local-file source coexist with the full TikTok scraper
 * behind the exact same type.
 */
interface StickerSource {

    /** Stable id persisted with each sticker (e.g. "tiktok-comment"). */
    val id: String

    /** Human-readable name shown in source pickers. */
    val displayName: String

    /** What this source is able to do. Drives UI affordances. */
    val capabilities: Set<Capability>

    /**
     * Resolve a raw, user-supplied string (full URL, share link, `vm.tiktok.com`
     * short link, or bare id) into a canonical [TikTokVideoRef].
     *
     * Requires [Capability.RESOLVE_VIDEO].
     */
    suspend fun resolveVideo(rawInput: String): StickResult<TikTokVideoRef>

    /**
     * Stream every animated sticker discovered in the comments of [video].
     *
     * Emitted incrementally so the UI can show previews as pagination proceeds
     * instead of blocking on the full comment tree. Requires
     * [Capability.SCRAPE_COMMENTS].
     */
    fun stickersFromComments(video: TikTokVideoRef): Flow<StickResult<RemoteSticker>>

    /**
     * Search a catalog/library of stickers by keyword, without opening a video.
     *
     * Requires [Capability.SEARCH_CATALOG]. See the module README for how the
     * TikTok implementation discovers this catalog.
     */
    suspend fun searchCatalog(query: CatalogQuery): StickResult<List<RemoteSticker>>

    /**
     * Download the full asset for [sticker] into app storage. Progress in `0f..1f`
     * is reported through [onProgress]. Requires [Capability.DOWNLOAD].
     */
    suspend fun download(
        sticker: RemoteSticker,
        onProgress: (Float) -> Unit = {},
    ): StickResult<DownloadedAsset>

    enum class Capability {
        RESOLVE_VIDEO,
        SCRAPE_COMMENTS,
        SEARCH_CATALOG,
        DOWNLOAD,
        CLIPBOARD_IMPORT,
        LOCAL_FILE_IMPORT,
    }
}
