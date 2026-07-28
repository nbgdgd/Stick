package com.trialtracker.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        DealEntity::class,
        InstalledAppEntity::class,
        FavoriteEntity::class,
        SeenDealEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class TrialDatabase : RoomDatabase() {
    abstract fun dealDao(): DealDao
    abstract fun installedAppDao(): InstalledAppDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun seenDealDao(): SeenDealDao

    companion object {
        @Volatile
        private var instance: TrialDatabase? = null

        fun get(context: Context): TrialDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                TrialDatabase::class.java,
                "trial_tracker.db",
            ).fallbackToDestructiveMigration().build().also { instance = it }
        }
    }
}
