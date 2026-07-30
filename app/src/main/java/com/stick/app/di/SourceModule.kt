package com.stick.app.di

import android.content.Context
import com.stick.app.data.repository.TikTokSessionRepository
import com.stick.app.data.source.WebViewCommentSource
import com.stick.stickersource.StickerSource
import com.stick.stickersource.StickerSourceRegistry
import com.stick.stickersource.clipboard.ClipboardStickerSource
import com.stick.stickersource.giphy.GiphySourceFactory
import com.stick.stickersource.local.LocalFileStickerSource
import com.stick.stickersource.tiktok.AssetDownloader
import com.stick.stickersource.tiktok.TikTokSourceFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.io.File
import javax.inject.Singleton

/**
 * The single wiring point for the swappable acquisition layer.
 *
 * To replace the TikTok backend after an API change, or to add a new source,
 * change *only this file*: build the new [StickerSource] and add it to the list.
 * Registration order is priority order (see [StickerSourceRegistry]).
 */
@Module
@InstallIn(SingletonComponent::class)
object SourceModule {

    @Provides
    @Singleton
    fun provideTikTokSessionRepository(
        @ApplicationContext context: Context,
    ): TikTokSessionRepository = TikTokSessionRepository(context)

    @Provides
    @Singleton
    fun provideStickerSourceRegistry(
        @ApplicationContext context: Context,
        sessionRepository: TikTokSessionRepository,
    ): StickerSourceRegistry {
        val downloadDir = File(context.filesDir, "downloads")

        val tikTok = TikTokSourceFactory.create(
            downloadDir = downloadDir,
            enableLogging = false,
            // Uses the signed-in session when the user has logged in, so the API
            // returns the full comment list instead of the anonymous subset.
            cookieProvider = { sessionRepository.cookiesBlocking() },
        )
        val giphy = GiphySourceFactory.create(downloadDir = downloadDir)
        // Registration order is priority. The page scraper goes first for comment
        // scanning because the public API only returns part of a busy video's
        // comments; it deliberately does NOT claim RESOLVE_VIDEO, so link
        // resolution still falls to the API source below.
        val webViewSource = WebViewCommentSource(
            context = context,
            downloader = AssetDownloader(OkHttpClient(), downloadDir),
        )
        val sources: List<StickerSource> = listOf(
            webViewSource,               // full comment list via the rendered page
            tikTok,                      // API fallback + link resolution
            giphy,                       // keyword catalog search
            LocalFileStickerSource(),    // import an existing file
            ClipboardStickerSource(),    // paste from clipboard
        )
        return StickerSourceRegistry(sources)
    }
}
