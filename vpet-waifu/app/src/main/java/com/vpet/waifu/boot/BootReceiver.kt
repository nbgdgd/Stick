package com.vpet.waifu.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.vpet.waifu.data.PetPreferences
import com.vpet.waifu.service.OverlayPermission
import com.vpet.waifu.service.PetOverlayService
import com.vpet.waifu.work.PetTickWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

/**
 * Brings the pet back after a reboot — but only if she was on screen when the
 * phone went down. Silently re-adding an overlay the player had dismissed would
 * be exactly the kind of thing that gets an app uninstalled.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var preferences: PetPreferences

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        // WorkManager restores its own jobs across reboots, but the app process
        // may not have run since install; re-asserting is cheap and idempotent.
        PetTickWorker.ensureScheduled(context)

        val pending = goAsync()
        try {
            // A receiver has ~10s and no scope of its own; reading one DataStore
            // key is well inside that budget.
            val enabled = runBlocking { preferences.bubbleEnabled.first() }
            if (enabled && OverlayPermission.isGranted(context)) {
                PetOverlayService.start(context)
            }
        } finally {
            pending.finish()
        }
    }
}
