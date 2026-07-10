package com.stick.app.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.stick.app.data.database.entity.StickerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StickerDao {

    @Upsert
    suspend fun upsert(sticker: StickerEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoringDuplicate(sticker: StickerEntity): Long

    @Query("SELECT * FROM stickers ORDER BY createdAtEpochMs DESC")
    fun observeAll(): Flow<List<StickerEntity>>

    @Query("SELECT * FROM stickers WHERE isFavorite = 1 ORDER BY createdAtEpochMs DESC")
    fun observeFavorites(): Flow<List<StickerEntity>>

    @Query("SELECT * FROM stickers WHERE collectionId = :collectionId ORDER BY createdAtEpochMs DESC")
    fun observeByCollection(collectionId: String): Flow<List<StickerEntity>>

    /** Substring search over name + keywords. */
    @Query(
        """
        SELECT * FROM stickers
        WHERE name LIKE '%' || :query || '%' OR keywords LIKE '%' || :query || '%'
        ORDER BY createdAtEpochMs DESC
        """,
    )
    fun search(query: String): Flow<List<StickerEntity>>

    @Query("SELECT * FROM stickers WHERE id = :id")
    suspend fun findById(id: String): StickerEntity?

    /** Duplicate detection: same bytes → same hash. */
    @Query("SELECT * FROM stickers WHERE contentHash = :hash LIMIT 1")
    suspend fun findByContentHash(hash: String): StickerEntity?

    @Query("UPDATE stickers SET isFavorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: String, favorite: Boolean)

    @Query("UPDATE stickers SET collectionId = :collectionId WHERE id = :id")
    suspend fun setCollection(id: String, collectionId: String?)

    @Query("DELETE FROM stickers WHERE id = :id")
    suspend fun delete(id: String)

    /**
     * Remove exact duplicates, keeping the earliest of each [contentHash] group.
     * Returns the ids that were deleted so their files can be cleaned up.
     */
    @Query(
        """
        DELETE FROM stickers WHERE id IN (
            SELECT id FROM stickers s
            WHERE id <> (
                SELECT id FROM stickers
                WHERE contentHash = s.contentHash
                ORDER BY createdAtEpochMs ASC LIMIT 1
            )
        )
        """,
    )
    suspend fun removeDuplicates(): Int
}
