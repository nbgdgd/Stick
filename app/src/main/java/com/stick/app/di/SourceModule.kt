package com.stick.app.di

import android.content.Context
import com.stick.stickersource.StickerSource
import com.stick.stickersource.StickerSourceRegistry
import com.stick.stickersource.clipboard.ClipboardStickerSource
import com.stick.stickersource.local.LocalFileStickerSource
import com.stick.stickersource.tiktok.TikTokSourceFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
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
    fun provideStickerSourceRegistry(
        @ApplicationContext context: Context,
    ): StickerSourceRegistry {
        val downloadDir = File(context.filesDir, "downloads")

        val tikTok = TikTokSourceFactory.create(
            downloadDir = downloadDir,
            enableLogging = false,
        )
        val sources: List<StickerSource> = listOf(
            tikTok,                      // primary: comments + catalog
            LocalFileStickerSource(),    // import an existing file
            ClipboardStickerSource(),    // paste from clipboard
        )
        return StickerSourceRegistry(sources)
    }
}
