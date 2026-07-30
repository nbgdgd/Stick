package com.vpet.waifu.data

import com.vpet.waifu.TestPet
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The save file, against a real Room database running on Robolectric. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PetRepositoryTest {

    private lateinit var pet: TestPet

    @Before
    fun setUp() {
        pet = TestPet()
    }

    @After
    fun tearDown() = pet.close()

    @Test
    fun `a fresh pet starts unsaved and is created on the first write`() = runBlocking {
        assertNull(pet.database.petStateDao().load())

        pet.repository.feed()

        assertNotNull(pet.database.petStateDao().load())
    }

    @Test
    fun `peek advances the world without writing to disk`() = runBlocking {
        pet.repository.feed()
        val storedBefore = pet.database.petStateDao().load()!!

        pet.clock.advanceMinutes(30)
        val peeked = pet.repository.peek()

        // The caller sees thirty minutes of decay…
        assertTrue(peeked.stats.hunger < storedBefore.hunger)
        // …but nothing has been persisted, so a widget redraw is not a move.
        assertEquals(storedBefore, pet.database.petStateDao().load())
    }

    @Test
    fun `tick persists the decay that peek only previewed`() = runBlocking {
        pet.repository.feed()
        pet.clock.advanceMinutes(30)

        pet.repository.tick()

        assertEquals(pet.repository.peek().stats.hunger, pet.database.petStateDao().load()!!.hunger, 0.001f)
    }

    @Test
    fun `state survives a round trip through the database`() = runBlocking {
        pet.repository.startOccupation(com.vpet.waifu.domain.Occupations.WORK.first())
        pet.repository.pet()

        val reopened = pet.repository.peek()

        assertEquals(com.vpet.waifu.domain.PetActivity.WORKING, reopened.activity)
        assertEquals("cafe", reopened.session?.occupationId)
    }

    @Test
    fun `pill effects survive a round trip`() = runBlocking {
        val advance = com.vpet.waifu.domain.Shop.byId("advance")!!
        pet.earnAtLeast(advance.price)
        pet.repository.buy(advance)

        val reloaded = pet.repository.peek()

        assertTrue(reloaded.hasEffect(com.vpet.waifu.domain.EffectKind.HUNGER_SURGE, pet.clock.nowMillis()))
    }

    @Test
    fun `the energy drink is the one thing in the shop that sells energy`() = runBlocking {
        val drink = com.vpet.waifu.domain.Shop.byId("energy_drink")!!
        pet.earnAtLeast(drink.price)
        // Wear her out first so the gain has somewhere to go.
        pet.clock.advanceMinutes(40)
        val tired = pet.repository.tick()

        val after = pet.repository.buy(drink)

        assertTrue(
            "energy ${after.stats.energy} should beat ${tired.stats.energy}",
            after.stats.energy > tired.stats.energy,
        )
        assertEquals(tired.progress.money - drink.price, after.progress.money)
        // And the caffeine crash is recorded, not silent.
        assertTrue(after.hasEffect(com.vpet.waifu.domain.EffectKind.EXHAUSTION, pet.clock.nowMillis()))
    }
}
