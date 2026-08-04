package com.vpet.waifu.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A copy of the save that outlives the app.
 *
 * Everything the game knows lives in its own database, which Android deletes
 * with the app. That is fine for an update — same package, same key, the data
 * is kept — but it is not fine for any of the things that actually happen while
 * a game is being built: a build handed over with a different applicationId
 * gets its own empty database, and an uninstall to clear a bad install takes a
 * month of play with it.
 *
 * So a copy is written to the public Downloads folder, where nothing the
 * installer does can reach it. It is the same JSON the manual export in
 * Settings produces, so the two are interchangeable — and on a fresh start the
 * app offers to read it back rather than restoring silently, because a stale
 * backup quietly overwriting a deliberate fresh start would be worse than the
 * problem it solves.
 *
 * Needs no permission on Android 10 and above, which is where MediaStore lets
 * an app own the files it created. Below that it is skipped: asking for
 * blanket storage access to hold one small file is not a trade worth making,
 * and the manual export in Settings still works everywhere.
 */
@Singleton
class SaveAutoBackup @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    val isSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    /** Opens the backup for writing, replacing any previous one. */
    fun openForWrite(): OutputStream? {
        if (!isSupported) return null
        return runCatching {
            val resolver = context.contentResolver
            // Replace rather than accumulate: one file that is always the
            // latest is something a person can find; twenty timestamped ones
            // are a puzzle to solve at exactly the wrong moment.
            existing()?.let { resolver.delete(it, null, null) }

            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, FILE_NAME)
                put(MediaStore.Downloads.MIME_TYPE, "application/json")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return@runCatching null
            resolver.openOutputStream(uri)
        }.getOrNull()
    }

    /** Opens the most recent backup for reading, or null if there is none. */
    fun openForRead(): InputStream? {
        if (!isSupported) return null
        return runCatching {
            existing()?.let { context.contentResolver.openInputStream(it) }
        }.getOrNull()
    }

    /** Whether a backup is sitting there waiting to be read. */
    fun exists(): Boolean = isSupported && runCatching { existing() != null }.getOrDefault(false)

    private fun existing(): Uri? {
        val resolver = context.contentResolver
        resolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Downloads._ID),
            "${MediaStore.Downloads.DISPLAY_NAME} = ?",
            arrayOf(FILE_NAME),
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(0)
                return MediaStore.Downloads.EXTERNAL_CONTENT_URI.buildUpon()
                    .appendPath(id.toString())
                    .build()
            }
        }
        return null
    }

    private companion object {
        /**
         * One fixed name, in the folder every file manager opens on first tap.
         * The player has to be able to find this without being told where to
         * look, because the moment they need it is the moment the app is gone.
         */
        const val FILE_NAME = "waifu-save-autobackup.json"
    }
}
