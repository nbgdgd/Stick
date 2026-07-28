package com.trialtracker.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DealDao {
    @Query("SELECT * FROM deals")
    fun observeAll(): Flow<List<DealEntity>>

    @Query("SELECT * FROM deals")
    suspend fun all(): List<DealEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(deals: List<DealEntity>)

    @Query("SELECT * FROM deals WHERE source = :source")
    suspend fun bySource(source: String): List<DealEntity>

    /** Drops the rows of one source that are no longer in its latest response. */
    @Query("DELETE FROM deals WHERE source = :source AND id NOT IN (:keep)")
    suspend fun deleteMissing(source: String, keep: List<String>)
}

@Dao
interface InstalledAppDao {
    @Query("SELECT * FROM installed_apps")
    fun observeAll(): Flow<List<InstalledAppEntity>>

    @Query("SELECT * FROM installed_apps")
    suspend fun all(): List<InstalledAppEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(apps: List<InstalledAppEntity>)

    @Query("DELETE FROM installed_apps")
    suspend fun clear()
}

@Dao
interface FavoriteDao {
    @Query("SELECT dealId FROM favorites")
    fun observeIds(): Flow<List<String>>

    @Query("INSERT OR REPLACE INTO favorites (dealId, savedAt) VALUES (:id, :savedAt)")
    suspend fun add(id: String, savedAt: Long)

    @Query("DELETE FROM favorites WHERE dealId = :id")
    suspend fun remove(id: String)
}

@Dao
interface SeenDealDao {
    @Query("SELECT dealId FROM seen_deals")
    suspend fun ids(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun mark(seen: List<SeenDealEntity>)
}
