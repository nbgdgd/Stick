package com.vpet.waifu.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.vpet.waifu.MainActivity
import com.vpet.waifu.R
import com.vpet.waifu.data.PetPreferences
import com.vpet.waifu.domain.PetSnapshot
import com.vpet.waifu.domain.PetTuning
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** The one thing worth interrupting the player for, right now. */
enum class PetAlert(val id: Int) {
    /** She finished a shift and the result is waiting. */
    SHIFT_DONE(2_001),

    /** She is going to start losing mood over this. */
    HUNGRY(2_002),

    /** She cannot be sent anywhere until she rests. */
    EXHAUSTED(2_003),

    /** Something happened today. */
    EVENT(2_004),

    /** Nobody has looked in for days — the one alert that is not a reaction. */
    MISSED(2_007),

    /** She fell ill — half the game is blocked until she is treated. */
    SICK(2_005),

    /** She is asking for something, and the wish has a window. */
    REQUEST(2_006),
}

/**
 * Tells the player something happened while they were not looking.
 *
 * The game runs on real time and its whole loop depends on the player choosing
 * to open it, which — with no notifications at all — meant remembering it
 * existed. That is not a design, it is an oversight.
 *
 * Deliberately quiet: at most one alert per situation, and each one is only
 * raised on the *transition* into it, so a pet who has been hungry for six
 * hours is mentioned once rather than every quarter of an hour.
 */
@Singleton
class PetNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: PetPreferences,
    private val tuning: PetTuning,
) {

    private val manager = NotificationManagerCompat.from(context)

    /** Raises whatever [snapshot] warrants that [previous] did not. */
    suspend fun notifyChanges(previous: PetSnapshot?, snapshot: PetSnapshot, nowMillis: Long) {
        if (!allowed()) return
        ensureChannel()

        val name = petName()

        // A finished shift is the one thing the player actively waited for.
        if (snapshot.lastOutcome != null && previous?.lastOutcome == null) {
            show(
                PetAlert.SHIFT_DONE,
                context.getString(R.string.notify_shift_title, name),
                context.getString(R.string.notify_shift_body, snapshot.lastOutcome!!.money),
            )
        }

        if (crossedDown(previous?.stats?.hunger, snapshot.stats.hunger, tuning.hungryThreshold)) {
            show(
                PetAlert.HUNGRY,
                context.getString(R.string.notify_hungry_title, name),
                context.getString(R.string.notify_hungry_body),
            )
        }

        if (crossedDown(previous?.stats?.energy, snapshot.stats.energy, tuning.tiredThreshold)) {
            show(
                PetAlert.EXHAUSTED,
                context.getString(R.string.notify_tired_title, name),
                context.getString(R.string.notify_tired_body),
            )
        }

        val event = snapshot.event
        if (event != null && !event.acknowledged && previous?.event?.day != event.day) {
            show(
                PetAlert.EVENT,
                context.getString(R.string.notify_event_title),
                context.getString(eventBodyRes(event.kind.name)),
            )
        }

        // Falling ill is the one transition that blocks half the game — it is
        // exactly what notifications exist for.
        if (snapshot.isSick && previous?.isSick == false) {
            show(
                PetAlert.SICK,
                context.getString(R.string.notify_sick_title, name),
                context.getString(R.string.notify_sick_body),
            )
        }

        // The only alert that is not a reaction to a transition: everything
        // else here fires because something changed, which by definition never
        // happens for a player who has stopped opening the app.
        val awayDays = ((nowMillis - snapshot.lastInteractionAt) / DAY_MILLIS).toInt()
        if (awayDays >= MISSED_AFTER_DAYS && previous == null) {
            show(
                PetAlert.MISSED,
                context.getString(R.string.notify_missed_title),
                context.getString(R.string.notify_missed_body, awayDays),
            )
        }

        // A fresh wish. Compared by slot so the same request never fires twice.
        val request = snapshot.request
        if (request != null && previous?.request?.slot != request.slot) {
            show(
                PetAlert.REQUEST,
                context.getString(R.string.notify_request_title, name),
                context.getString(R.string.notify_request_body),
            )
        }
    }

    /** Clears an alert the player has now dealt with. */
    fun clear(alert: PetAlert) = manager.cancel(alert.id)

    /**
     * Only on the way down, and only once.
     *
     * Without the previous value there is no transition to detect, so the first
     * ever tick stays silent rather than announcing a state the player has been
     * looking at all along.
     */
    private fun crossedDown(before: Float?, after: Float, threshold: Float): Boolean =
        before != null && before > threshold && after <= threshold

    private suspend fun allowed(): Boolean {
        if (!preferences.settings.first().notificationsEnabled) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    private suspend fun petName(): String =
        preferences.settings.first().petName.ifBlank { context.getString(R.string.app_name) }

    private fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notify_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = context.getString(R.string.notify_channel_description) }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    @Suppress("MissingPermission") // Checked in `allowed`, which every caller goes through.
    private fun show(alert: PetAlert, title: String, body: String) {
        val open = PendingIntent.getActivity(
            context,
            alert.id,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_pet_notification)
            // The shade tints the small icon with this; without it the app's
            // own colour never appears anywhere outside the app.
            .setColor(NOTIFICATION_ACCENT)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        runCatching { manager.notify(alert.id, notification) }
    }

    private fun eventBodyRes(kindName: String): Int = when (kindName) {
        "LUCKY_DAY" -> R.string.event_lucky_day
        "COLD" -> R.string.event_cold
        "LETTER" -> R.string.event_letter
        "INSPIRED" -> R.string.event_inspired
        else -> R.string.event_restless
    }

    private companion object {
        const val DAY_MILLIS = 24L * 60 * 60 * 1000
        const val MISSED_AFTER_DAYS = 2
        const val NOTIFICATION_ACCENT = 0xFFA855F7.toInt()

        const val CHANNEL_ID = "pet_events"
    }
}
