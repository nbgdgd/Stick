package com.vpet.waifu.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.vpet.waifu.domain.PetProgress
import com.vpet.waifu.domain.Upgrades

@Database(entities = [PetStateEntity::class], version = 4, exportSchema = false)
abstract class PetDatabase : RoomDatabase() {
    abstract fun petStateDao(): PetStateDao

    companion object {
        const val NAME = "vpet.db"

        /**
         * Phase 1 → Phase 2: money, EXP, jobs, pill effects and reactions.
         *
         * A real migration rather than a destructive one — anyone who already
         * raised a pet on the Phase 1 build keeps her stats and her clock.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val columns = listOf(
                    "money INTEGER NOT NULL DEFAULT ${PetProgress.START_MONEY}",
                    "exp INTEGER NOT NULL DEFAULT 0",
                    "sessionOccupationId TEXT",
                    "sessionStartedAt INTEGER NOT NULL DEFAULT 0",
                    "sessionEndsAt INTEGER NOT NULL DEFAULT 0",
                    "effects TEXT NOT NULL DEFAULT ''",
                    "outcomeOccupationId TEXT",
                    "outcomeKind TEXT",
                    "outcomeMoney INTEGER NOT NULL DEFAULT 0",
                    "outcomeExp INTEGER NOT NULL DEFAULT 0",
                    "outcomeQuality TEXT",
                    "outcomeCancelled INTEGER NOT NULL DEFAULT 0",
                    "outcomeAt INTEGER NOT NULL DEFAULT 0",
                    "emote TEXT",
                    "emoteUntil INTEGER NOT NULL DEFAULT 0",
                )
                columns.forEach { db.execSQL("ALTER TABLE pet_state ADD COLUMN $it") }
            }
        }

        /**
         * Wages moved from a lump sum at clock-out to per-minute accrual, which
         * needs somewhere to bank the fraction of a coin not yet handed over.
         *
         * A shift that is mid-flight during the upgrade simply starts accruing
         * from zero; she keeps her wallet, and the worst case is one shift paid
         * from the moment of the update rather than from its start.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                listOf(
                    "sessionAccruedPay REAL NOT NULL DEFAULT 0",
                    "sessionPaidOut INTEGER NOT NULL DEFAULT 0",
                    "sessionAccruedExp REAL NOT NULL DEFAULT 0",
                    "sessionPaidExp INTEGER NOT NULL DEFAULT 0",
                ).forEach { db.execSQL("ALTER TABLE pet_state ADD COLUMN $it") }
            }
        }

        /**
         * Things she keeps: upgrades, the outfit she is wearing, the day's
         * event, and what you last fed her.
         *
         * Anyone upgrading keeps their wallet and their level and simply starts
         * owning nothing but the default outfit — which is exactly right, since
         * there was nothing permanent to own before this.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                listOf(
                    "owned TEXT NOT NULL DEFAULT '${Upgrades.DEFAULT_OUTFIT}'",
                    "outfit TEXT NOT NULL DEFAULT '${Upgrades.DEFAULT_OUTFIT}'",
                    "eventKind TEXT",
                    "eventDay INTEGER NOT NULL DEFAULT 0",
                    "eventSeenAt INTEGER NOT NULL DEFAULT 0",
                    "lastMealId TEXT",
                    "repeatedMeals INTEGER NOT NULL DEFAULT 0",
                ).forEach { db.execSQL("ALTER TABLE pet_state ADD COLUMN $it") }
            }
        }
    }
}
