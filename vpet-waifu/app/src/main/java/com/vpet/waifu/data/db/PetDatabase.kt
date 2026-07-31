package com.vpet.waifu.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.vpet.waifu.domain.PetProgress
import com.vpet.waifu.domain.Upgrades

@Database(entities = [PetStateEntity::class], version = 7, exportSchema = false)
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

        /**
         * The tip jar — loose change arriving every few seconds.
         *
         * `passiveSince` defaults to 0, which the simulation reads as "never
         * settled" and restarts from the first tick after the update, so an
         * upgrading save is never handed a lump sum for the years before the
         * feature existed.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                listOf(
                    "passiveSince INTEGER NOT NULL DEFAULT 0",
                    "passiveBank REAL NOT NULL DEFAULT 0",
                    "passiveDay INTEGER NOT NULL DEFAULT 0",
                    "passivePaidToday INTEGER NOT NULL DEFAULT 0",
                ).forEach { db.execSQL("ALTER TABLE pet_state ADD COLUMN $it") }
            }
        }

        /**
         * Her inner life: bond, sickness, wishes, the story, her path, and the
         * counters the profile is built from.
         *
         * Everything defaults to "nothing yet", which is true for an upgrading
         * save — except her birthday, which is backfilled from the oldest
         * timestamp the row carries so "days together" honours the time already
         * spent rather than restarting it.
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                listOf(
                    "bondPoints INTEGER NOT NULL DEFAULT 0",
                    "bondDay INTEGER NOT NULL DEFAULT 0",
                    "bondToday INTEGER NOT NULL DEFAULT 0",
                    "sickSince INTEGER NOT NULL DEFAULT 0",
                    "runDownMinutes REAL NOT NULL DEFAULT 0",
                    "requestKind TEXT",
                    "requestItemId TEXT",
                    "requestUntil INTEGER NOT NULL DEFAULT 0",
                    "lastRequestSlot INTEGER NOT NULL DEFAULT 0",
                    "storyChapter INTEGER NOT NULL DEFAULT 0",
                    "storySeen INTEGER NOT NULL DEFAULT 0",
                    "focus TEXT",
                    "shiftsWorked INTEGER NOT NULL DEFAULT 0",
                    "lessonsDone INTEGER NOT NULL DEFAULT 0",
                    "gamesPlayed INTEGER NOT NULL DEFAULT 0",
                    "mealsFed INTEGER NOT NULL DEFAULT 0",
                    "giftsGiven INTEGER NOT NULL DEFAULT 0",
                    "sicknessesNursed INTEGER NOT NULL DEFAULT 0",
                    "totalEarned INTEGER NOT NULL DEFAULT 0",
                    "bornAt INTEGER NOT NULL DEFAULT 0",
                ).forEach { db.execSQL("ALTER TABLE pet_state ADD COLUMN $it") }
                db.execSQL("UPDATE pet_state SET bornAt = MIN(lastTickAt, lastInteractionAt)")
            }
        }

        /**
         * Her diary, the arcade's records, the week's goal and the calendar.
         *
         * All default to empty: an upgrading save simply starts remembering
         * from here, which is the honest reading of "nothing was written down
         * before this build existed".
         */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                listOf(
                    "journal TEXT NOT NULL DEFAULT ''",
                    "bestCatch INTEGER NOT NULL DEFAULT 0",
                    "bestRhythm INTEGER NOT NULL DEFAULT 0",
                    "bestMemory INTEGER NOT NULL DEFAULT 0",
                    "goalWeek INTEGER NOT NULL DEFAULT 0",
                    "goalBaseline INTEGER NOT NULL DEFAULT 0",
                    "goalRewarded INTEGER NOT NULL DEFAULT 0",
                    "celebratedMilestone INTEGER NOT NULL DEFAULT 0",
                ).forEach { db.execSQL("ALTER TABLE pet_state ADD COLUMN ${'$'}it") }
            }
        }
    }
}
