package com.stick.app.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.stick.app.data.database.entity.HistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Insert
    suspend fun insert(entry: HistoryEntity)

    @Query("SELECT * FROM history ORDER BY processedAtEpochMs DESC LIMIT :limit")
    fun observeRecent(limit: Int = 50): Flow<List<HistoryEntity>>

    @Query("DELETE FROM history")
    suspend fun clear()
}
