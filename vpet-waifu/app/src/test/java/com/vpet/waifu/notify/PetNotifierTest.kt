package com.vpet.waifu.notify

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.vpet.waifu.data.PetPreferences
import com.vpet.waifu.domain.ActivityOutcome
import com.vpet.waifu.domain.EventKind
import com.vpet.waifu.domain.OccupationKind
import com.vpet.waifu.domain.OutcomeQuality
import com.vpet.waifu.domain.PetEvent
import com.vpet.waifu.domain.PetSnapshot
import com.vpet.waifu.domain.PetStats
import com.vpet.waifu.domain.PetTuning
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

private const val NOW = 1_700_373_600_000L

/**
 * Telling the player something happened.
 *
 * The game runs on real time and its loop depended entirely on the player
 * remembering it existed. The risk in fixing that is the opposite failure —
 * an app that nags — so the tests here are mostly about *silence*: only on the
 * transition into a state, only once, and never for something the player is
 * already looking at.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PetNotifierTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val tuning = PetTuning()
    private lateinit var notifier: PetNotifier

    private val manager get() =
        shadowOf(context.getSystemService(NotificationManager::class.java))

    @Before
    fun setUp() {
        // Robolectric denies runtime permissions until they are granted, which
        // is the same gate production goes through — see the last test.
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        notifier = PetNotifier(context, PetPreferences(context), tuning)

        // Start from an empty shade. "The very first look is silent" asserts a
        // total of zero, which makes it a claim about the whole process rather
        // than about the call under test: anything another test left behind
        // fails it, and it duly failed once in a full run and never again on
        // its own. The assertion is worth keeping — it is the difference
        // between silent and quiet — so the state it reads is made local
        // instead of the assertion being weakened.
        context.getSystemService(NotificationManager::class.java).cancelAll()
    }

    private fun pet(
        hunger: Float = 80f,
        energy: Float = 80f,
        lastInteractionAt: Long = NOW,
    ) = PetSnapshot(
        stats = PetStats(hunger, energy, 70f),
        lastTickAt = NOW,
        lastInteractionAt = lastInteractionAt,
    )

    @Test
    fun `a finished shift is announced`() = runBlocking {
        val working = pet()
        val done = working.copy(
            lastOutcome = ActivityOutcome(
                occupationId = "cafe",
                kind = OccupationKind.WORK,
                money = 105,
                exp = 15,
                quality = OutcomeQuality.GOOD,
                cancelled = false,
                completedAt = NOW,
            ),
        )

        notifier.notifyChanges(working, done, NOW)

        assertNotNull(manager.getNotification(PetAlert.SHIFT_DONE.id))
    }

    @Test
    fun `an outcome that was already there is not announced again`() = runBlocking {
        val done = pet().copy(
            lastOutcome = ActivityOutcome(
                "cafe", OccupationKind.WORK, 105, 15, OutcomeQuality.GOOD, false, NOW,
            ),
        )

        notifier.notifyChanges(done, done, NOW)

        assertNull(manager.getNotification(PetAlert.SHIFT_DONE.id))
    }

    @Test
    fun `getting hungry is announced once, on the way down`() = runBlocking {
        val fine = pet(hunger = tuning.hungryThreshold + 5f)
        val hungry = pet(hunger = tuning.hungryThreshold - 1f)

        notifier.notifyChanges(fine, hungry, NOW)
        assertNotNull(manager.getNotification(PetAlert.HUNGRY.id))

        // Still hungry an hour later is not news.
        context.getSystemService(NotificationManager::class.java).cancelAll()
        notifier.notifyChanges(hungry, pet(hunger = 2f), NOW)
        assertNull(manager.getNotification(PetAlert.HUNGRY.id))
    }

    @Test
    fun `the very first look is silent`() = runBlocking {
        // No previous value means no transition — announcing a state the player
        // has been staring at since before the app started is pure noise.
        notifier.notifyChanges(null, pet(hunger = 1f, energy = 1f), NOW)

        assertEquals(0, manager.allNotifications.size)
    }

    @Test
    fun `running out of energy is announced`() = runBlocking {
        notifier.notifyChanges(
            pet(energy = tuning.tiredThreshold + 5f),
            pet(energy = tuning.tiredThreshold - 1f),
            NOW,
        )

        assertNotNull(manager.getNotification(PetAlert.EXHAUSTED.id))
    }

    @Test
    fun `recovering is not announced`() = runBlocking {
        notifier.notifyChanges(pet(hunger = 5f), pet(hunger = 90f), NOW)

        assertNull(manager.getNotification(PetAlert.HUNGRY.id))
    }

    @Test
    fun `a new day's event is announced, and only on the day it lands`() = runBlocking {
        val quiet = pet()
        val today = quiet.copy(event = PetEvent(EventKind.LETTER, day = 19_680))

        notifier.notifyChanges(quiet, today, NOW)
        assertNotNull(manager.getNotification(PetAlert.EVENT.id))

        context.getSystemService(NotificationManager::class.java).cancelAll()
        notifier.notifyChanges(today, today, NOW)
        assertNull("the same day announced twice", manager.getNotification(PetAlert.EVENT.id))
    }

    @Test
    fun `an event the player has already read is not announced`() = runBlocking {
        val read = pet().copy(event = PetEvent(EventKind.LETTER, day = 19_680, seenAt = NOW))

        notifier.notifyChanges(pet(), read, NOW)

        assertNull(manager.getNotification(PetAlert.EVENT.id))
    }

    @Test
    fun `without the permission it says nothing at all`() = runBlocking {
        shadowOf(context as Application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

        notifier.notifyChanges(
            pet(hunger = 90f),
            pet(hunger = 1f).copy(
                lastOutcome = ActivityOutcome(
                    "cafe", OccupationKind.WORK, 105, 15, OutcomeQuality.GOOD, false, NOW,
                ),
            ),
            NOW,
        )

        assertEquals(0, manager.allNotifications.size)
    }

    @Test
    fun `a player who has stopped coming back is missed, once`() = runBlocking {
        val day = 24L * 60 * 60 * 1000
        // Yesterday she was one day alone; today she is two, which is the line.
        val before = pet(lastInteractionAt = NOW - day)
        val after = pet(lastInteractionAt = NOW - 2 * day)

        notifier.notifyChanges(before, after, NOW)
        assertEquals(1, manager.allNotifications.size)

        // A third day is not a second crossing, and must stay quiet.
        notifier.notifyChanges(after, pet(lastInteractionAt = NOW - 3 * day), NOW)
        assertEquals(1, manager.allNotifications.size)
    }

    @Test
    fun `a player who came back today is not missed`() = runBlocking {
        val day = 24L * 60 * 60 * 1000
        notifier.notifyChanges(pet(lastInteractionAt = NOW - 5 * day), pet(), NOW)

        assertEquals(0, manager.allNotifications.size)
    }
}
