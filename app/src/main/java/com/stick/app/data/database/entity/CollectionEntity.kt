package com.stick.app.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A user-defined collection (folder) that groups library stickers. */
@Entity(tableName = "collections")
data class CollectionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
)
