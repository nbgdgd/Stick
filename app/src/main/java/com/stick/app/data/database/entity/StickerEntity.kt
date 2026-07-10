package com.stick.app.data.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A sticker saved in the user's library.
 *
 * [contentHash] is a hash of the downloaded bytes and is uniquely indexed so
 * duplicate imports can be detected and de-duplicated (see
 * `StickerDao.findByContentHash`).
 */
@Entity(
    tableName = "stickers",
    indices = [
        Index(value = ["contentHash"], unique = true),
        Index(value = ["isFavorite"]),
        Index(value = ["collectionId"]),
    ],
)
data class StickerEntity(
    @PrimaryKey val id: String,
    val name: String,
    val localPath: String,
    val format: String,
    val widthPx: Int,
    val heightPx: Int,
    val fps: Float,
    val durationMs: Long,
    val fileSizeBytes: Long,
    val sourceId: String,
    val originVideoUrl: String?,
    val originAuthor: String?,
    val keywords: String,           // comma-joined, for LIKE search
    val contentHash: String,
    val isFavorite: Boolean = false,
    val collectionId: String? = null,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
)
