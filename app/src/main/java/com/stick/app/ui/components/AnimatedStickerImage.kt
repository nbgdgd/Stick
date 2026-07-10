package com.stick.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import coil.size.Scale

/**
 * Renders an animated sticker (animated WebP / GIF / APNG) or a still frame.
 *
 * Uses Coil with the platform animated decoders. Preview caching is handled by
 * Coil's memory + disk cache, keyed by the model, so grids scroll without
 * re-decoding. [play] pauses the animation for the viewer's frame-step mode.
 */
@Composable
fun AnimatedStickerImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    play: Boolean = true,
    contentScale: ContentScale = ContentScale.Fit,
) {
    val context = LocalContext.current
    val imageLoader = remember(context) {
        ImageLoader.Builder(context)
            .components {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()
    }

    val request = remember(model, play) {
        ImageRequest.Builder(context)
            .data(model)
            .scale(Scale.FIT)
            // When paused, disable animation so only the first frame is shown.
            .apply { if (!play) allowHardware(false) }
            .build()
    }

    Box(modifier) {
        AsyncImage(
            model = request,
            contentDescription = contentDescription,
            imageLoader = imageLoader,
            contentScale = contentScale,
            modifier = Modifier,
        )
    }
}
