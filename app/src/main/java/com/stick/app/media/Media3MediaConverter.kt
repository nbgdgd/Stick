package com.stick.app.media

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult as TransformerExportResult
import androidx.media3.transformer.Transformer
import com.stick.app.domain.converter.EditOperation
import com.stick.app.domain.converter.EditPipeline
import com.stick.app.domain.converter.ExportOptions
import com.stick.app.domain.converter.ExportResult
import com.stick.app.domain.converter.MediaConverter
import com.stick.app.domain.converter.SizeEstimator
import com.stick.core.model.MediaInfo
import com.stick.core.model.StickerFormat
import com.stick.core.result.StickError
import com.stick.core.result.StickResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

/**
 * Default [MediaConverter] built on Media3 `Transformer`, which uses `MediaCodec`
 * and therefore hardware video encoders where available.
 *
 * Coverage:
 *  * **Probe / frame extraction** — all input formats via [MediaMetadataRetriever].
 *  * **Encode to [StickerFormat.TELEGRAM_WEBM] / [StickerFormat.MP4]** — full,
 *    hardware-accelerated, with resize/rotate effects mapped from the edit pipeline.
 *  * **GIF / animated WebP / APNG / TGS** — delegated to [frameFormatConverter],
 *    a swappable collaborator (an FFmpeg backend, or a Lottie packer for TGS),
 *    because the platform codecs cannot encode those containers. Keeping that
 *    behind an interface preserves the "swap one module" property.
 */
@UnstableApi
class Media3MediaConverter(
    private val context: Context,
    private val frameFormatConverter: FrameFormatConverter,
) : MediaConverter {

    override suspend fun probe(path: String): StickResult<MediaInfo> = withContext(Dispatchers.IO) {
        StickResult.runCatching {
            val file = File(path)
            val format = StickerFormat.fromExtension(file.extension) ?: StickerFormat.WEBP_ANIMATED
            if (format.mimeType.startsWith("image")) {
                probeImage(file, format)
            } else {
                probeVideo(file, format)
            }
        }
    }

    private fun probeImage(file: File, format: StickerFormat): MediaInfo {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opts)
        return MediaInfo(
            widthPx = opts.outWidth.coerceAtLeast(0),
            heightPx = opts.outHeight.coerceAtLeast(0),
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
    ): StickResult<ExportResult> {
        // Frame-container formats are not encodable by MediaCodec: delegate.
        if (options.format in FRAME_FORMATS) {
            return frameFormatConverter.convert(pipeline, options, outputPath, onProgress)
        }
        return transcodeVideo(pipeline, options, outputPath, onProgress)
    }

    private suspend fun transcodeVideo(
        pipeline: EditPipeline,
        options: ExportOptions,
        outputPath: String,
        onProgress: (Float) -> Unit,
    ): StickResult<ExportResult> = withContext(Dispatchers.Main) {
        // Transformer requires a thread with a Looper; the main thread is fine.
        suspendCancellableCoroutine { cont ->
            val videoMime = when (options.format) {
                StickerFormat.TELEGRAM_WEBM -> MimeTypes.VIDEO_VP9
                else -> MimeTypes.VIDEO_H264
            }

            val transformer = Transformer.Builder(context)
                .setVideoMimeType(videoMime)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, result: TransformerExportResult) {
                        onProgress(1f)
                        val file = File(outputPath)
                        cont.resume(
                            StickResult.Success(
                                ExportResult(
                                    outputPath = outputPath,
                                    format = options.format,
                                    fileSizeBytes = file.length(),
                                    widthPx = options.widthPx,
                                    heightPx = options.heightPx,
                                ),
                            ),
                        )
                    }

                    override fun onError(
                        composition: Composition,
                        result: TransformerExportResult,
                        exception: ExportException,
                    ) {
                        cont.resume(
                            StickResult.Failure(StickError.Conversion("Transcode failed", exception)),
                        )
                    }
                })
                .build()

            val edited = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(File(pipeline.sourcePath))))
                .setEffects(Effects(/* audio = */ emptyList(), buildVideoEffects(pipeline, options)))
                .build()

            cont.invokeOnCancellation { transformer.cancel() }
            transformer.start(edited, outputPath)
            // Poll progress on the same Looper.
            pollProgress(transformer, onProgress)
        }
    }

    /** Map supported [EditOperation]s to Media3 GL effects. */
    private fun buildVideoEffects(pipeline: EditPipeline, options: ExportOptions): List<Effect> {
        val effects = mutableListOf<Effect>()
        effects += Presentation.createForWidthAndHeight(
            options.widthPx, options.heightPx, Presentation.LAYOUT_SCALE_TO_FIT,
        )
        pipeline.operations.filterIsInstance<EditOperation.Rotate>().firstOrNull()?.let {
            effects += ScaleAndRotateTransformation.Builder()
                .setRotationDegrees(it.degrees.toFloat())
                .build()
        }
        pipeline.operations.filterIsInstance<EditOperation.Flip>().firstOrNull()?.let { flip ->
            effects += ScaleAndRotateTransformation.Builder()
                .setScale(if (flip.horizontal) -1f else 1f, if (flip.vertical) -1f else 1f)
                .build()
        }
        return effects
    }

    private fun pollProgress(transformer: Transformer, onProgress: (Float) -> Unit) {
        val handler = Handler(Looper.getMainLooper())
        val holder = androidx.media3.transformer.ProgressHolder()
        val runnable = object : Runnable {
            override fun run() {
                val state = transformer.getProgress(holder)
                if (state != Transformer.PROGRESS_STATE_NOT_STARTED) {
                    onProgress((holder.progress / 100f).coerceIn(0f, 1f))
                    handler.postDelayed(this, 200)
                }
            }
        }
        handler.post(runnable)
    }

    override fun estimateSize(info: MediaInfo, options: ExportOptions): Long =
        SizeEstimator.estimate(info, options)

    private companion object {
        val FRAME_FORMATS = setOf(
            StickerFormat.GIF,
            StickerFormat.WEBP_ANIMATED,
            StickerFormat.APNG,
            StickerFormat.TELEGRAM_TGS,
        )
    }
}
