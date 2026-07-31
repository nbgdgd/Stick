package com.vpet.waifu.di

import android.content.Context
import androidx.room.Room
import com.vpet.waifu.data.WallClock
import com.vpet.waifu.data.db.PetDatabase
import com.vpet.waifu.data.db.PetStateDao
import com.vpet.waifu.domain.PetSimulation
import com.vpet.waifu.domain.PetTuning
import com.vpet.waifu.widget.GlanceWidgetRefresher
import com.vpet.waifu.widget.WidgetRefresher
import com.vpet.waifu.widget.WidgetWaker
import com.vpet.waifu.widget.WorkManagerWidgetWaker
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

/** Binds the widget refresher so tests can substitute an observable one. */
@Module
@InstallIn(SingletonComponent::class)
abstract class WidgetModule {
    @Binds
    abstract fun bindWidgetRefresher(impl: GlanceWidgetRefresher): WidgetRefresher

    @Binds
    abstract fun bindWidgetWaker(impl: WorkManagerWidgetWaker): WidgetWaker
}

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): PetDatabase =
        Room.databaseBuilder(context, PetDatabase::class.java, PetDatabase.NAME)
            .addMigrations(
                PetDatabase.MIGRATION_1_2,
                PetDatabase.MIGRATION_2_3,
                PetDatabase.MIGRATION_3_4,
                PetDatabase.MIGRATION_4_5,
                PetDatabase.MIGRATION_5_6,
            )
            .build()

    @Provides
    fun providePetStateDao(database: PetDatabase): PetStateDao = database.petStateDao()

    /** Balance lives here so a later phase can swap in a difficulty-specific curve. */
    @Provides
    @Singleton
    fun provideTuning(): PetTuning = PetTuning()

    @Provides
    @Singleton
    fun provideSimulation(tuning: PetTuning): PetSimulation = PetSimulation(tuning)

    @Provides
    @Singleton
    fun provideClock(): WallClock = WallClock.SYSTEM

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
