package com.stick.app.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.stick.app.data.database.dao.CollectionDao
import com.stick.app.data.database.dao.HistoryDao
import com.stick.app.data.database.dao.StickerDao
import com.stick.app.data.database.entity.CollectionEntity
import com.stick.app.data.database.entity.HistoryEntity
import com.stick.app.data.database.entity.StickerEntity

@Database(
    entities = [StickerEntity::class, CollectionEntity::class, HistoryEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class StickDatabase : RoomDatabase() {
    abstract fun stickerDao(): StickerDao
    abstract fun collectionDao(): CollectionDao
    abstract fun historyDao(): HistoryDao

    companion object {
        const val NAME = "stick.db"
    }
}
