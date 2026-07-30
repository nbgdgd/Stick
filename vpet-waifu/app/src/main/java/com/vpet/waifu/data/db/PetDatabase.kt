package com.vpet.waifu.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [PetStateEntity::class], version = 1, exportSchema = false)
abstract class PetDatabase : RoomDatabase() {
    abstract fun petStateDao(): PetStateDao

    companion object {
        const val NAME = "vpet.db"
    }
}
