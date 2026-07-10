package com.stick.stickersource.tiktok

import com.stick.core.model.MediaInfo
import com.stick.core.model.RemoteSticker
import com.stick.core.result.StickError
import com.stick.core.result.StickResult
import com.stick.stickersource.model.DownloadedAsset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

/**
 * Streams a remote asset to disk with progress reporting. Lives in the source
 * module because downloading is part of *acquisition*; probing (FPS/frames) is
 * deferred to the app's media pipeline which owns the platform decoders.
 */
class AssetDownloader(
    private val httpClient: OkHttpClient,
    /** Directory the app designates for freshly downloaded originals. */
    private val downloadDir: File,
) {
    suspend fun download(
        sticker: RemoteSticker,
        onProgress: (Float) -> Unit,
    ): StickResult<DownloadedAsset> = withContext(Dispatchers.IO) {
        try {
            if (!downloadDir.exists()) downloadDir.mkdirs()
            val target = File(downloadDir, fileName(sticker))

            val request = Request.Builder()
                .url(sticker.downloadUrl)
                // A Referer is required by TikTok's CDN for some assets.
                .header("Referer", "https://www.tiktok.com/")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext StickResult.Failure(
                        StickError.Network("HTTP ${response.code} downloading ${sticker.id}"),
                    )
                }
                val body = response.body
                    ?: return@withContext StickResult.Failure(StickError.Network("Empty body"))

                val total = body.contentLength()
                body.byteStream().use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var written = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            written += read
                            if (total > 0) onProgress((written.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
                onProgress(1f)

                val info = sticker.info.copy(
                    fileSizeBytes = target.length(),
                    format = sticker.format,
                )
                StickResult.Success(
                    DownloadedAsset(source = sticker, localPath = target.absolutePath, info = info),
                )
            }
        } catch (e: IOException) {
            StickResult.Failure(StickError.Network("Download failed for ${sticker.id}", e))
        }
    }

    private fun fileName(sticker: RemoteSticker): String {
        val safeId = sticker.id.replace(Regex("[^A-Za-z0-9_-]"), "_")
        return "${sticker.sourceId}_$safeId.${sticker.format.extension}"
    }
}
