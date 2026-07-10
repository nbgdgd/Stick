package com.stick.app.di

import android.content.Context
import com.stick.app.domain.converter.MediaConverter
import com.stick.app.media.FfmpegFrameFormatConverter
import com.stick.app.media.FfmpegRunner
import com.stick.app.media.FrameFormatConverter
import com.stick.app.media.LottiePacker
import com.stick.app.media.Media3MediaConverter
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Wires the media pipeline. Swapping the frame-format encoder (e.g. dropping in a
 * bundled FFmpeg build) is a one-line change to [provideFrameFormatConverter].
 */
@Module
@InstallIn(SingletonComponent::class)
object MediaModule {

    @Provides
    @Singleton
    fun provideFrameFormatConverter(): FrameFormatConverter =
        // Bind a real FfmpegRunner / LottiePacker here to enable GIF/WebP/APNG/TGS.
        // The defaults degrade gracefully with a clear message (WebM/MP4 still work).
        FfmpegFrameFormatConverter(
            runner = FfmpegRunner.Unavailable,
            lottiePacker = LottiePacker.Unavailable,
        )

    @Provides
    @Singleton
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun provideMediaConverter(
        @ApplicationContext context: Context,
        frameFormatConverter: FrameFormatConverter,
    ): MediaConverter = Media3MediaConverter(context, frameFormatConverter)
}
