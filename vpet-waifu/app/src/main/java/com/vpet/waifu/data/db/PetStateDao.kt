package com.vpet.waifu.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PetStateDao {

    /** Emits on every write, which is how both the app UI and the bubble stay in sync. */
    @Query("SELECT * FROM pet_state WHERE id = ${PetStateEntity.SINGLETON_ID}")
    fun observe(): Flow<PetStateEntity?>

    @Query("SELECT * FROM pet_state WHERE id = ${PetStateEntity.SINGLETON_ID}")
    suspend fun load(): PetStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(state: PetStateEntity)
}
