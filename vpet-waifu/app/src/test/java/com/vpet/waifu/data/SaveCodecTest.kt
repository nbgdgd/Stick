package com.vpet.waifu.data

import com.vpet.waifu.TestPet
import com.vpet.waifu.data.db.PetStateEntity
import com.vpet.waifu.data.db.toEntity
import com.vpet.waifu.data.db.toSnapshot
import com.vpet.waifu.domain.Shop
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.lang.reflect.Modifier

/**
 * The save file, out of the app and back in.
 *
 * A pet raised over months lives in one row in private storage: a reinstall or
 * a new phone ends her. That makes this codec the one piece of the app where a
 * silent omission is unrecoverable — a field the encoder forgets is a field
 * every restored pet loses forever — so the round trip is checked field by
 * field, and the field list itself is checked against the entity.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SaveCodecTest {

    private lateinit var pet: TestPet

    @Before
    fun setUp() {
        pet = TestPet()
    }

    @After
    fun tearDown() = pet.close()

    /** A save with every column carrying something distinctive. */
    private fun populated(): PetStateEntity = PetStateEntity(
        hunger = 61.5f,
        energy = 42.25f,
        mood = 88.75f,
        activity = "WORKING",
        lastTickAt = 1_700_000_000_000L,
        lastInteractionAt = 1_699_999_000_000L,
        money = 12_345,
        exp = 6_789,
        sessionOccupationId = "cafe",
        sessionStartedAt = 1_699_998_000_000L,
        sessionEndsAt = 1_700_001_000_000L,
        sessionAccruedPay = 41.5f,
        sessionPaidOut = 41,
        sessionAccruedExp = 12.25f,
        sessionPaidExp = 12,
        effects = "HASTE:1700002000000,OVERTIME:1700003000000",
        owned = "outfit_uniform,theme_default,fridge,laptop,theme_cozy",
        outfit = "outfit_mint",
        theme = "theme_cozy",
        eventKind = "LUCKY_DAY",
        eventDay = 19_680,
        eventSeenAt = 1_699_997_000_000L,
        lastMealId = "ramen",
        repeatedMeals = 3,
        outcomeOccupationId = "idol",
        outcomeKind = "WORK",
        outcomeMoney = 480,
        outcomeExp = 60,
        outcomeQuality = "GREAT",
        outcomeCancelled = true,
        outcomeAt = 1_699_996_000_000L,
        emote = "LOVED",
        emoteUntil = 1_700_000_500_000L,
        passiveSince = 1_699_995_000_000L,
        passiveBank = 0.75f,
        passiveDay = 19_681,
        passivePaidToday = 640,
        bondPoints = 137,
        bondDay = 19_681,
        bondToday = 12,
        sickSince = 1_699_994_000_000L,
        runDownMinutes = 34.5f,
        requestKind = "FOOD",
        requestItemId = "onigiri",
        requestUntil = 1_700_004_000_000L,
        lastRequestSlot = 157_000L,
        storyChapter = 4,
        storySeen = 3,
        focus = "SCHOLAR",
        shiftsWorked = 21,
        lessonsDone = 8,
        gamesPlayed = 40,
        mealsFed = 90,
        giftsGiven = 4,
        sicknessesNursed = 2,
        totalEarned = 98_765,
        bornAt = 1_690_000_000_000L,
        journal = "SHIFT_DONE:cafe:105:1700000200000|DAILY:5:525:1700000300000",
        bestCatch = 21,
        bestRhythm = 140,
        bestMemory = 9,
        goalWeek = 2_810L,
        goalBaseline = 17,
        goalRewarded = true,
        celebratedMilestone = 30,
        streakDays = 5,
        bestStreak = 11,
        lastLoginDay = 19_681,
        dayOffDay = 19_680,
        pendingDaily = 525,
    )

    @Test
    fun `every field survives the round trip`() {
        val original = populated()

        val restored = SaveCodec.decode(SaveCodec.encode(original)).getOrThrow()

        assertEquals(original, restored)
    }

    /**
     * The guard the round trip above cannot give on its own: a field added to
     * the entity and forgotten here would still round-trip, because both sides
     * of the comparison would carry its default.
     */
    @Test
    fun `the file carries every column the row has`() {
        val payload = JSONObject(SaveCodec.encode(populated())).getJSONObject("save")

        val columns = PetStateEntity::class.java.declaredFields
            .filterNot { Modifier.isStatic(it.modifiers) }
            .map { it.name }
            // The row id is always the singleton; a save file has no use for it.
            .filterNot { it == "id" }

        columns.forEach { assertTrue("$it is missing from the save file", payload.has(it)) }
    }

    @Test
    fun `a file goes through a stream unharmed`() {
        val original = populated()
        val bytes = ByteArrayOutputStream()

        assertTrue(SaveCodec.exportTo(original, bytes).isSuccess)
        val restored = SaveCodec.importFrom(ByteArrayInputStream(bytes.toByteArray())).getOrThrow()

        assertEquals(original, restored)
        assertEquals(original.toSnapshot(), restored.toSnapshot())
    }

    @Test
    fun `somebody else's file is refused instead of decoded`() {
        listOf(
            "",
            "not json at all",
            """{"hello":"world"}""",
            """{"format":"vpet-save","version":1,"save":{}}""",
        ).forEach { text ->
            assertTrue("'$text' should not parse as a save", SaveCodec.decode(text).isFailure)
        }
    }

    @Test
    fun `a file from a newer build is refused rather than guessed at`() {
        val ahead = JSONObject(SaveCodec.encode(populated()))
            .put("version", SaveCodec.VERSION + 1)
            .toString()

        assertTrue(SaveCodec.decode(ahead).isFailure)
    }

    @Test
    fun `a damaged file does not become a damaged pet`() {
        val root = JSONObject(SaveCodec.encode(populated()))
        // A field edited after the fact — the shape a truncated or mangled
        // copy takes by the time it reaches us.
        root.getJSONObject("save").put("money", 999_999)

        assertTrue(SaveCodec.decode(root.toString()).isFailure)
    }

    @Test
    fun `the checksum does not depend on the order the keys come back in`() {
        val root = JSONObject(SaveCodec.encode(populated()))
        val save = root.getJSONObject("save")
        val shuffled = JSONObject()
        save.keys().asSequence().toList().shuffled().forEach { shuffled.put(it, save.get(it)) }

        val reordered = JSONObject()
            .put("format", root.getString("format"))
            .put("version", root.getInt("version"))
            .put("checksum", root.getString("checksum"))
            .put("save", shuffled)

        assertEquals(populated(), SaveCodec.decode(reordered.toString()).getOrThrow())
    }

    // --- the repository end ---------------------------------------------------

    @Test
    fun `exporting and re-importing restores the same pet`() = runBlocking {
        pet.give(4_000)
        pet.repository.feed()
        val before = pet.repository.snapshotNow()
        val file = ByteArrayOutputStream()
        assertTrue(pet.repository.exportSave(file).isSuccess)

        // Somebody else's pet, in the same save slot.
        pet.repository.buy(Shop.byId("onigiri")!!)
        pet.clock.advanceMinutes(120)
        assertNotEquals(before.progress.money, pet.repository.peek().progress.money)

        val restored = pet.repository.importSave(ByteArrayInputStream(file.toByteArray()))

        assertTrue(restored.isSuccess)
        val stored = pet.database.petStateDao().load()!!
        assertEquals(PetStateEntity.SINGLETON_ID, stored.id)
        assertEquals(before.toEntity(), stored)
    }

    @Test
    fun `a file that will not parse leaves the pet alone`() = runBlocking {
        pet.give(1_000)
        val before = pet.database.petStateDao().load()!!

        val result = pet.repository.importSave(ByteArrayInputStream("nonsense".toByteArray()))

        assertTrue(result.isFailure)
        assertEquals(before, pet.database.petStateDao().load()!!)
    }

    @Test
    fun `the exported file is the pet as she is now, not as she was last written`() = runBlocking {
        pet.repository.tick()
        pet.clock.advanceMinutes(90)
        val file = ByteArrayOutputStream()

        pet.repository.exportSave(file)

        val exported = SaveCodec.importFrom(ByteArrayInputStream(file.toByteArray())).getOrThrow()
        assertEquals(pet.clock.nowMillis, exported.lastTickAt)
        assertFalse("ninety minutes of decay should be in the file", exported.hunger >= 80f)
    }
}
