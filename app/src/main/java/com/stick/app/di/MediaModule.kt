package com.stick.app.di

import android.content.Context
import com.stick.app.domain.converter.MediaConverter
import com.stick.app.media.AndroidFfmpegRunner
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
    fun provideFrameFormatConverter(): FrameFormatConverter =
        // GIF / animated WebP / APNG now encode through the bundled FFmpeg backend.
        // .tgs still needs a vector source, so its packer stays a graceful stub.
        FfmpegFrameFormatConverter(
            runner = AndroidFfmpegRunner(),
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
