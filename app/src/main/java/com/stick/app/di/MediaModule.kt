package com.stick.app.di

import android.content.Context
import com.stick.app.domain.converter.MediaConverter
import com.stick.app.media.AndroidFfmpegRunner
import com.stick.app.media.AnimatedFrameExtractor
import com.stick.app.media.FfmpegFrameFormatConverter
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
    fun provideFrameFormatConverter(
        @ApplicationContext context: Context,
    ): FrameFormatConverter =
        // GIF/APNG/webm/mp4 encode via FFmpeg; animated WebP is pre-decoded to
        // frames by Fresco first (FFmpeg can't read animated WebP).
        FfmpegFrameFormatConverter(
            runner = AndroidFfmpegRunner(),
            lottiePacker = LottiePacker.Unavailable,
            frameExtractor = AnimatedFrameExtractor(context),
        )

    @Provides
    @Singleton
    fun provideMediaConverter(
        @ApplicationContext context: Context,
        frameFormatConverter: FrameFormatConverter,
    ): MediaConverter = Media3MediaConverter(context, frameFormatConverter)
}
