package com.vpet.waifu.service

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.net.toUri

/**
 * `SYSTEM_ALERT_WINDOW` cannot be granted by a normal runtime prompt — the user
 * has to flip it in a system settings screen, and Android keeps tightening the
 * path there. Everything that depends on it funnels through here so the UI can
 * check once and send the player to the right place.
 */
object OverlayPermission {

    fun isGranted(context: Context): Boolean = Settings.canDrawOverlays(context)

    /**
     * The "Display over other apps" settings page, pre-filtered to this app.
     * Some OEM builds ignore the package URI and land on the full app list; the
     * intent is still the only supported entry point.
     */
    fun settingsIntent(context: Context): Intent = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        "package:${context.packageName}".toUri(),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
