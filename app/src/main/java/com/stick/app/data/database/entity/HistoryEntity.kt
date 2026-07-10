package com.stick.app.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A previously processed import, so links can be re-opened from history. */
@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val rawInput: String,
    val canonicalUrl: String,
    val stickersFound: Int,
    val processedAtEpochMs: Long = System.currentTimeMillis(),
)
