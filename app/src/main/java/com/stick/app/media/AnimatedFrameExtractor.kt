package com.stick.app.media

import android.content.Context
import android.graphics.Bitmap
import com.facebook.animated.gif.GifImage
import com.facebook.animated.webp.WebPImage
import com.facebook.common.references.CloseableReference
import com.facebook.imagepipeline.animated.base.AnimatedImage
import com.facebook.imagepipeline.animated.base.AnimatedImageResult
import com.facebook.imagepipeline.animated.impl.AnimatedDrawableBackendImpl
import com.facebook.imagepipeline.animated.impl.AnimatedImageCompositor
import com.facebook.imagepipeline.animated.util.AnimatedDrawableUtil
import com.facebook.imagepipeline.common.ImageDecodeOptions
import com.facebook.soloader.SoLoader
import java.io.File

/**
 * Decodes an animated image into a PNG frame sequence using Fresco's native
 * decoders.
 *
 * FFmpeg cannot decode animated WebP (it skips the ANIM chunk), which is exactly
 * the format TikTok serves comment-pack stickers in (`.awebp`). Fresco decodes it
 * (and animated GIF) reliably; the resulting PNG frames are then handed to FFmpeg
 * for encoding into any target. Everything is best-effort: on any failure this
 * returns `null` and the caller falls back to feeding the file to FFmpeg directly.
 */
class AnimatedFrameExtractor(private val context: Context) {

    data class Frames(val dir: File, val count: Int, val fps: Float)

    @Volatile private var soLoaderReady = false

    private fun ensureSoLoader() {
        if (!soLoaderReady) {
            runCatching { SoLoader.init(context, false) }
            soLoaderReady = true
        }
    }

    /**
     * Extract frames from [path]. Returns `null` for non-animated inputs (so the
     * caller lets FFmpeg handle them) or if decoding fails.
     */
    fun extract(path: String): Frames? = try {
        ensureSoLoader()
        val bytes = File(path).readBytes()
        val image: AnimatedImage? = when {
            isWebp(bytes) -> WebPImage.createFromByteArray(bytes, ImageDecodeOptions.defaults())
            isGif(bytes) -> GifImage.createFromByteArray(bytes)
            else -> null
        }
        if (image == null || image.frameCount <= 1) {
            image?.dispose()
            null
        } else {
            renderFrames(image)
        }
    } catch (t: Throwable) {
        null
    }

    private fun renderFrames(image: AnimatedImage): Frames {
        val width = image.width
        val height = image.height
        val count = image.frameCount
        val totalMs = image.frameDurations.sum().coerceAtLeast(1)
        val fps = (count * 1000f / totalMs).coerceIn(1f, 60f)

        val backend = AnimatedDrawableBackendImpl(
            AnimatedDrawableUtil(),
            AnimatedImageResult.forAnimatedImage(image),
            null,
            false,
        )
        val compositor = AnimatedImageCompositor(
            backend,
            false,
            object : AnimatedImageCompositor.Callback {
                override fun onIntermediateResult(index: Int, bitmap: Bitmap) = Unit
                override fun getCachedBitmap(index: Int): CloseableReference<Bitmap>? = null
            },
        )

        val dir = File(context.cacheDir, "frames_${System.currentTimeMillis()}").apply { mkdirs() }
        for (i in 0 until count) {
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            compositor.renderFrame(i, bmp)
            File(dir, "f_%05d.png".format(i)).outputStream().use {
                bmp.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            bmp.recycle()
        }
        image.dispose()
        return Frames(dir, count, fps)
    }

    private fun isWebp(b: ByteArray): Boolean =
        b.size >= 12 && b[0] == 'R'.code.toByte() && b[1] == 'I'.code.toByte() &&
            b[8] == 'W'.code.toByte() && b[9] == 'E'.code.toByte() && b[10] == 'B'.code.toByte()

    private fun isGif(b: ByteArray): Boolean =
        b.size >= 3 && b[0] == 'G'.code.toByte() && b[1] == 'I'.code.toByte() && b[2] == 'F'.code.toByte()
}
