package com.vpet.waifu.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.vpet.waifu.data.db.PetDatabase
import com.vpet.waifu.domain.Upgrades
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The migrations, actually executed.
 *
 * Every other test in the project opens an in-memory database that Room
 * creates directly at the newest version — so the migration SQL itself never
 * ran under test. That is exactly how a broken `ALTER TABLE` shipped: the app
 * worked on every fresh install and crashed on launch for every *upgrading*
 * install, which is all real players.
 *
 * This test builds the original v1 table by hand, runs the entire migration
 * chain over it, and then demands the result match — column for column — the
 * schema Room creates from scratch today. Any future migration typo, missing
 * column or drift between entity and migration fails here first.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationChainTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private val chain = PetDatabase.ALL_MIGRATIONS.toList()

    @Test
    fun `the full chain runs and lands on exactly the modern schema`() {
        val db = v1Database()
        chain.forEach { it.migrate(db) }

        val migrated = columnsOf(db)
        val fresh = freshColumns()

        assertEquals("migrated schema drifted from the entity", fresh, migrated)
    }

    @Test
    fun `a phase-one pet survives the journey with her clock and her birthday`() {
        val db = v1Database()
        chain.forEach { it.migrate(db) }

        db.query("SELECT hunger, lastTickAt, bornAt, bondPoints, journal FROM pet_state").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(80f, cursor.getFloat(0), 0.001f)
            assertEquals(1_700_000_000_000L, cursor.getLong(1))
            // Her birthday is backfilled from the oldest timestamp she carried.
            assertEquals(1_700_000_000_000L, cursor.getLong(2))
            assertEquals(0, cursor.getInt(3))
            assertEquals("", cursor.getString(4))
        }
    }

    /**
     * The migrated row has to be *loadable*, not merely shaped right.
     *
     * A column that arrives with a default the mapper rejects — an empty theme,
     * a streak read as "already claimed today" — passes the schema check above
     * and still ruins the first launch after the update.
     */
    @Test
    fun `an upgraded save loads as a playable pet with everything new at zero`() {
        val db = v1Database()
        chain.forEach { it.migrate(db) }

        db.query(
            "SELECT streakDays, bestStreak, lastLoginDay, dayOffDay, theme, pendingDaily FROM pet_state",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
            assertEquals(0, cursor.getInt(1))
            // Zero on both day columns reads as "never", so she collects her
            // first check-in and can take a day off the moment she is updated.
            assertEquals(0L, cursor.getLong(2))
            assertEquals(0L, cursor.getLong(3))
            assertEquals(Upgrades.DEFAULT_THEME, cursor.getString(4))
            assertEquals(0, cursor.getInt(5))
        }
    }

    @Test
    fun `the chain the app is handed is the whole chain`() {
        // The failure this catches is invisible on every fresh install and
        // fatal on every upgrading one: a migration written but never
        // registered, leaving a gap Room can only answer by crashing.
        val steps = PetDatabase.ALL_MIGRATIONS.map { it.startVersion to it.endVersion }

        // The version Room itself stamps on a fresh file, so this cannot drift
        // from the @Database annotation the way a typed-out number would.
        assertEquals((1 until freshVersion()).map { it to it + 1 }, steps)
    }

    /** The v1 table, exactly as the first release created it, with one pet in it. */
    private fun v1Database(): SupportSQLiteDatabase {
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(null) // in-memory
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            """
                            CREATE TABLE pet_state (
                                id INTEGER NOT NULL PRIMARY KEY,
                                hunger REAL NOT NULL,
                                energy REAL NOT NULL,
                                mood REAL NOT NULL,
                                activity TEXT NOT NULL,
                                lastTickAt INTEGER NOT NULL,
                                lastInteractionAt INTEGER NOT NULL
                            )
                            """.trimIndent(),
                        )
                        db.execSQL(
                            "INSERT INTO pet_state VALUES " +
                                "(0, 80.0, 80.0, 70.0, 'AWAKE', 1700000000000, 1700000000000)",
                        )
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build(),
        )
        return helper.writableDatabase
    }

    /** The column set Room builds when there is no history at all. */
    private fun freshColumns(): Set<String> = withFreshDatabase { columnsOf(it) }

    /** The schema version Room stamps on a file it created itself. */
    private fun freshVersion(): Int = withFreshDatabase { it.version }

    private fun <T> withFreshDatabase(read: (SupportSQLiteDatabase) -> T): T {
        val room = Room.inMemoryDatabaseBuilder(context, PetDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        return try {
            read(room.openHelper.writableDatabase)
        } finally {
            room.close()
        }
    }

    private fun columnsOf(db: SupportSQLiteDatabase): Set<String> =
        db.query("PRAGMA table_info(pet_state)").use { cursor ->
            val names = mutableSetOf<String>()
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            while (cursor.moveToNext()) names += cursor.getString(nameIndex)
            names
        }
}
