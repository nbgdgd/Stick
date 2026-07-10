package com.stick.app.data.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.stick.app.data.database.entity.CollectionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CollectionDao {
    @Upsert
    suspend fun upsert(collection: CollectionEntity)

    @Query("SELECT * FROM collections ORDER BY createdAtEpochMs DESC")
    fun observeAll(): Flow<List<CollectionEntity>>

    @Query("DELETE FROM collections WHERE id = :id")
    suspend fun delete(id: String)
}
