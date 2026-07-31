package com.vpet.waifu.data

import com.vpet.waifu.TestPet
import com.vpet.waifu.data.db.toEntity
import com.vpet.waifu.data.db.toSnapshot
import com.vpet.waifu.domain.EventKind
import com.vpet.waifu.domain.Focus
import com.vpet.waifu.domain.PetRequest
import com.vpet.waifu.domain.RequestKind
import com.vpet.waifu.domain.Occupations
import com.vpet.waifu.domain.PetEvent
import com.vpet.waifu.domain.PetSnapshot
import com.vpet.waifu.domain.Shop
import com.vpet.waifu.domain.Upgrades
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * That the new state actually survives being written down.
 *
 * The upgrades are the whole reason to earn money, so an upgrade that is bought
 * and then forgotten on the next launch is worse than not having them. Room's
 * mapping is hand-written, which is exactly the kind of code where a field gets
 * added to one side and not the other.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PetPersistenceTest {

    private lateinit var pet: TestPet

    @Before
    fun setUp() {
        pet = TestPet()
    }

    @After
    fun tearDown() = pet.close()

    @Test
    fun `everything on the snapshot survives a round trip`() {
        val rich = PetSnapshot.initial(pet.clock.nowMillis).copy(
            owned = setOf(Upgrades.DEFAULT_OUTFIT, "fridge", "laptop", "outfit_mint"),
            outfit = "outfit_mint",
            event = PetEvent(EventKind.COLD, day = 19_680, seenAt = 42L),
            lastMealId = "ramen",
            repeatedMeals = 3,
            passiveSince = 1_700_000_000_000L,
            passiveBank = 0.4f,
            passiveDay = 19_681,
            passivePaidToday = 640,
            bondPoints = 137,
            bondDay = 19_681,
            bondToday = 12,
            sickSince = 1_700_000_100_000L,
            runDownMinutes = 34.5f,
            request = PetRequest(RequestKind.FOOD, "ramen", 1_700_000_500_000L, 157_000L),
            lastRequestSlot = 157_000L,
            storyChapter = 3,
            storySeen = 2,
            focus = Focus.SCHOLAR,
            shiftsWorked = 21,
            lessonsDone = 8,
            gamesPlayed = 40,
            mealsFed = 90,
            giftsGiven = 4,
            sicknessesNursed = 2,
            totalEarned = 12_345,
            bornAt = 1_699_000_000_000L,
        )

        val restored = rich.toEntity().toSnapshot()

        assertEquals(rich.owned, restored.owned)
        assertEquals(rich.outfit, restored.outfit)
        assertEquals(rich.event, restored.event)
        assertEquals(rich.lastMealId, restored.lastMealId)
        assertEquals(rich.repeatedMeals, restored.repeatedMeals)
        // The tip jar's bookkeeping, which is the difference between "she saved
        // up while you watched" and "the day's allowance resets on every launch".
        assertEquals(rich.passiveSince, restored.passiveSince)
        assertEquals(rich.passiveBank, restored.passiveBank, 0.001f)
        assertEquals(rich.passiveDay, restored.passiveDay)
        assertEquals(rich.passivePaidToday, restored.passivePaidToday)
        // Her inner life. Bond lost on restart would be the cruellest bug the
        // app could have, so it is pinned field by field.
        assertEquals(rich.bondPoints, restored.bondPoints)
        assertEquals(rich.bondDay, restored.bondDay)
        assertEquals(rich.bondToday, restored.bondToday)
        assertEquals(rich.sickSince, restored.sickSince)
        assertEquals(rich.runDownMinutes, restored.runDownMinutes, 0.001f)
        assertEquals(rich.request, restored.request)
        assertEquals(rich.lastRequestSlot, restored.lastRequestSlot)
        assertEquals(rich.storyChapter, restored.storyChapter)
        assertEquals(rich.storySeen, restored.storySeen)
        assertEquals(rich.focus, restored.focus)
        assertEquals(rich.shiftsWorked, restored.shiftsWorked)
        assertEquals(rich.lessonsDone, restored.lessonsDone)
        assertEquals(rich.gamesPlayed, restored.gamesPlayed)
        assertEquals(rich.mealsFed, restored.mealsFed)
        assertEquals(rich.giftsGiven, restored.giftsGiven)
        assertEquals(rich.sicknessesNursed, restored.sicknessesNursed)
        assertEquals(rich.totalEarned, restored.totalEarned)
        assertEquals(rich.bornAt, restored.bornAt)
    }

    @Test
    fun `the tip jar pays into the save file, not just into the screen`() = runBlocking {
        val before = pet.repository.snapshotNow().progress.money

        pet.clock.nowMillis += 15 * 3_000L
        pet.repository.tick()

        val stored = pet.database.petStateDao().load()!!.toSnapshot()
        assertEquals(before + 15, stored.progress.money)
    }

    @Test
    fun `an upgrade bought is still owned after a reload`() = runBlocking {
        val fridge = Upgrades.byId("fridge")!!
        pet.give(fridge.price)

        pet.repository.buyUpgrade(fridge)

        val reloaded = pet.database.petStateDao().load()!!.toSnapshot()
        assertTrue(reloaded.owns("fridge"))
    }

    @Test
    fun `an outfit worn is still worn after a reload`() = runBlocking {
        val mint = Upgrades.byId("outfit_mint")!!
        pet.give(mint.price)
        pet.repository.buyUpgrade(mint)

        pet.repository.wear(Upgrades.DEFAULT_OUTFIT)

        assertEquals(
            Upgrades.DEFAULT_OUTFIT,
            pet.database.petStateDao().load()!!.toSnapshot().outfit,
        )
    }

    @Test
    fun `a row written by an older build still loads`() {
        // Everything the migration adds is defaulted, so a v3 row arrives with
        // empty strings and zeroes. It must produce a playable pet, not a crash.
        val legacy = PetSnapshot.initial(pet.clock.nowMillis).toEntity().copy(
            owned = "",
            outfit = "",
            eventKind = "NOT_AN_EVENT",
        )

        val restored = legacy.toSnapshot()

        assertEquals(setOf(Upgrades.DEFAULT_OUTFIT), restored.owned)
        assertEquals(Upgrades.DEFAULT_OUTFIT, restored.outfit)
        assertEquals(null, restored.event)
    }

    @Test
    fun `she remembers being fed the same thing`() = runBlocking {
        val onigiri = Shop.byId("onigiri")!!
        pet.give(onigiri.price * 5)

        repeat(3) {
            pet.clock.advanceMinutes(30)
            pet.repository.buy(onigiri)
        }

        val stored = pet.database.petStateDao().load()!!.toSnapshot()
        assertEquals("onigiri", stored.lastMealId)
        assertEquals(3, stored.repeatedMeals)
    }

    @Test
    fun `a different meal resets the streak`() = runBlocking {
        val onigiri = Shop.byId("onigiri")!!
        val ramen = Shop.byId("ramen")!!
        pet.give(500)

        repeat(3) {
            pet.clock.advanceMinutes(30)
            pet.repository.buy(onigiri)
        }
        pet.clock.advanceMinutes(30)
        pet.repository.buy(ramen)

        assertEquals(1, pet.repository.peek().repeatedMeals)
    }

    @Test
    fun `an upgrade she owns keeps working across a shift`() = runBlocking {
        val laptop = Upgrades.byId("laptop")!!
        val cafe = Occupations.WORK.first()
        pet.give(laptop.price)
        pet.repository.buyUpgrade(laptop)
        assertFalse(pet.repository.peek().owned.isEmpty())

        pet.repository.startOccupation(cafe)
        pet.clock.advanceMinutes(cafe.durationMinutes + 1L)
        val done = pet.repository.tick()

        assertTrue("she should still own it after clocking off", done.owns("laptop"))
        assertTrue("and it should have paid a premium", done.lastOutcome!!.money > cafe.payout)
    }
}
