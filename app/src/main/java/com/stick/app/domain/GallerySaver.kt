package com.stick.app.domain

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.stick.core.result.StickError
import com.stick.core.result.StickResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Copies finished sticker files into the device gallery (`Pictures/Stick`).
 *
 * Uses MediaStore so no storage permission is needed on Android 10+ (scoped
 * storage), and the files show up in Google Photos / the gallery app immediately.
 * On Android 9 and below it falls back to the public Pictures directory.
 */
object GallerySaver {

    private const val ALBUM = "Stick"

    /**
     * Save [files] to the gallery. Returns the number saved, or a failure if
     * nothing could be written. Individual failures are skipped so one bad file
     * doesn't abort a whole batch.
     */
    suspend fun saveAll(context: Context, files: List<File>): StickResult<Int> =
        withContext(Dispatchers.IO) {
            if (files.isEmpty()) return@withContext StickResult.Failure(StickError.NotFound("Nothing to save"))
            var saved = 0
            var lastError: Throwable? = null
            for (file in files) {
                try {
                    if (file.exists() && save(context, file) != null) saved++
                } catch (t: Throwable) {
                    lastError = t
                }
            }
            if (saved > 0) {
                StickResult.Success(saved)
            } else {
                StickResult.Failure(
                    StickError.Unknown(lastError?.message ?: "Could not save to gallery", lastError),
                )
            }
        }

    private fun save(context: Context, file: File): Uri? {
        val mime = mimeFor(file.extension)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$ALBUM")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val collection = if (mime.startsWith("video")) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(collection, values) ?: return null
        resolver.openOutputStream(uri)?.use { out ->
            file.inputStream().use { it.copyTo(out) }
        } ?: return null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        return uri
    }

    private fun mimeFor(ext: String): String = when (ext.lowercase()) {
        "png", "apng" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "webm" -> "video/webm"
        "mp4" -> "video/mp4"
        else -> "application/octet-stream"
    }
}
