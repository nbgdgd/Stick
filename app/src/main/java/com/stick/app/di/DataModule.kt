package com.stick.app.di

import android.content.Context
import androidx.room.Room
import com.stick.app.data.database.StickDatabase
import com.stick.app.data.database.dao.CollectionDao
import com.stick.app.data.database.dao.HistoryDao
import com.stick.app.data.database.dao.StickerDao
import com.stick.app.data.repository.SettingsRepository
import com.stick.app.data.repository.StickerRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Wires persistence and repositories. */
@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): StickDatabase =
        Room.databaseBuilder(context, StickDatabase::class.java, StickDatabase.NAME)
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideStickerDao(db: StickDatabase): StickerDao = db.stickerDao()
    @Provides fun provideCollectionDao(db: StickDatabase): CollectionDao = db.collectionDao()
    @Provides fun provideHistoryDao(db: StickDatabase): HistoryDao = db.historyDao()

    @Provides
    @Singleton
    fun provideStickerRepository(
        stickerDao: StickerDao,
        collectionDao: CollectionDao,
        historyDao: HistoryDao,
    ): StickerRepository = StickerRepository(stickerDao, collectionDao, historyDao)

    @Provides
    @Singleton
    fun provideSettingsRepository(@ApplicationContext context: Context): SettingsRepository =
        SettingsRepository(context)
}
