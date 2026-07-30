package com.stick.app.media

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import com.stick.app.domain.converter.EditPipeline
import com.stick.app.domain.converter.ExportOptions
import com.stick.app.domain.converter.ExportResult
import com.stick.app.domain.converter.MediaConverter
import com.stick.app.domain.converter.SizeEstimator
import com.stick.core.model.MediaInfo
import com.stick.core.model.StickerFormat
import com.stick.core.result.StickResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Default [MediaConverter].
 *
 * Probing and single-frame extraction use the Android platform decoders
 * ([MediaMetadataRetriever] / [BitmapFactory]). All *encoding* is delegated to
 * FFmpeg via [frameFormatConverter], because FFmpeg is the only backend that can
 * take the app's inputs (static JPEG/PNG comment stickers, animated GIF/APNG) and
 * write every target — including Telegram `.webm` from a still image, which the
 * platform `Transformer` cannot do (and which used to crash export).
 */
class Media3MediaConverter(
    private val context: Context,
    private val frameFormatConverter: FrameFormatConverter,
) : MediaConverter {

    override suspend fun probe(path: String): StickResult<MediaInfo> = withContext(Dispatchers.IO) {
        val file = File(path)
        val format = StickerFormat.fromExtension(file.extension) ?: StickerFormat.WEBP_ANIMATED
        // Try image dimensions first (covers webp/gif/png/jpeg); fall back to video.
        val info = runCatching { probeImage(file, format) }.getOrNull()
            ?: runCatching { probeVideo(file, format) }.getOrNull()
            ?: MediaInfo(fileSizeBytes = file.length(), format = format)
        StickResult.Success(info)
    }

    private fun probeImage(file: File, format: StickerFormat): MediaInfo? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opts)
        if (opts.outWidth <= 0) return null
        return MediaInfo(
            widthPx = opts.outWidth,
            heightPx = opts.outHeight,
            fileSizeBytes = file.length(),
            format = format,
            hasAlpha = format.supportsTransparency,
        )
    }

    private fun probeVideo(file: File, format: StickerFormat): MediaInfo {
        MediaMetadataRetriever().use { mmr ->
            mmr.setDataSource(file.absolutePath)
            fun meta(key: Int) = mmr.extractMetadata(key)?.toLongOrNull() ?: 0L
            val durationMs = meta(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val fps = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
                ?.toFloatOrNull() ?: 0f
            return MediaInfo(
                widthPx = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH).toInt(),
                heightPx = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT).toInt(),
                durationMs = durationMs,
                fps = fps,
                frameCount = if (fps > 0) ((durationMs / 1000f) * fps).toInt() else 0,
                fileSizeBytes = file.length(),
                format = format,
            )
        }
    }

    override suspend fun extractFrame(
        path: String,
        positionMs: Long,
        outputPath: String,
    ): StickResult<String> = withContext(Dispatchers.IO) {
        StickResult.runCatching {
            MediaMetadataRetriever().use { mmr ->
                mmr.setDataSource(path)
                val bitmap = mmr.getFrameAtTime(positionMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST)
                    ?: error("No frame at ${positionMs}ms")
                File(outputPath).outputStream().use { out ->
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                }
            }
            outputPath
        }
    }

    override suspend fun convert(
        pipeline: EditPipeline,
        options: ExportOptions,
        outputPath: String,
        onProgress: (Float) -> Unit,
    ): StickResult<ExportResult> =
        // Everything goes through FFmpeg; guarded so a failure is a result, not a crash.
        try {
            frameFormatConverter.convert(pipeline, options, outputPath, onProgress)
        } catch (t: Throwable) {
            StickResult.Failure(com.stick.core.result.StickError.Conversion("Export failed", t))
        }

    override fun estimateSize(info: MediaInfo, options: ExportOptions): Long =
        SizeEstimator.estimate(info, options)
}
