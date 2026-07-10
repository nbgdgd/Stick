package com.stick.stickersource.model

import com.stick.core.model.MediaInfo
import com.stick.core.model.RemoteSticker

/**
 * A [RemoteSticker] after its bytes have been written to local storage.
 *
 * The app persists [localPath] in Room and hands it to the media pipeline for
 * probing/editing/conversion.
 */
data class DownloadedAsset(
    val source: RemoteSticker,
    /** Absolute path of the file that was written. */
    val localPath: String,
    /** Metadata as probed right after download (size is always accurate). */
    val info: MediaInfo,
    val downloadedAtEpochMs: Long = System.currentTimeMillis(),
)
