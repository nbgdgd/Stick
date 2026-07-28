package com.trialtracker.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Loads real launcher icons for installed apps. They are the only coloured element
 * on a card, so they are worth the round trip; everything else falls back to a
 * brand-tinted glyph tile.
 */
object AppIconCache {
    private val cache = ConcurrentHashMap<String, ImageBitmap>()
    private val misses = ConcurrentHashMap.newKeySet<String>()

    suspend fun load(context: Context, packageName: String): ImageBitmap? {
        cache[packageName]?.let { return it }
        if (packageName in misses) return null
        return withContext(Dispatchers.IO) {
            val drawable: Drawable? = runCatching {
                context.packageManager.getApplicationIcon(packageName)
            }.getOrNull()
            if (drawable == null) {
                misses.add(packageName)
                null
            } else {
                val bitmap = drawable.toBitmap(SIZE_PX)
                val image = bitmap.asImageBitmap()
                cache[packageName] = image
                image
            }
        }
    }

    private fun Drawable.toBitmap(size: Int): Bitmap {
        if (this is BitmapDrawable && bitmap != null) {
            return Bitmap.createScaledBitmap(bitmap, size, size, true)
        }
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        setBounds(0, 0, size, size)
        draw(canvas)
        return bitmap
    }

    private const val SIZE_PX = 144
}

@Composable
fun rememberAppIcon(packageName: String, enabled: Boolean): State<ImageBitmap?> {
    val context = LocalContext.current
    val state = remember(packageName) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(packageName, enabled) {
        if (enabled) state.value = AppIconCache.load(context, packageName)
    }
    return state
}

/** Parses "#RRGGBB" from the catalog, falling back to the accent colour. */
fun parseColor(hex: String, fallback: Color): Color = runCatching {
    Color(android.graphics.Color.parseColor(hex))
}.getOrDefault(fallback)
