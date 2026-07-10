package com.stick.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stick.app.data.repository.SettingsRepository
import com.stick.app.data.repository.UserSettings
import com.stick.app.ui.StickApp
import com.stick.app.ui.theme.StickTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Single-activity host. The theme is driven by persisted [UserSettings] so a
 * change (light/dark/AMOLED, dynamic color) applies instantly across the app.
 *
 * The initial share/view [Intent] is forwarded to the composition so a shared
 * TikTok link deep-links straight into the import flow.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val sharedLink = extractSharedLink(intent)

        setContent {
            val settings by settingsRepository.settings
                .collectAsStateWithLifecycle(initialValue = UserSettings())

            StickTheme(
                themeMode = settings.themeMode,
                dynamicColor = settings.dynamicColor,
            ) {
                StickApp(initialSharedLink = sharedLink)
            }
        }
    }

    /** Pull a TikTok URL out of a SEND (share) or VIEW (open link) intent. */
    private fun extractSharedLink(intent: Intent?): String? = when (intent?.action) {
        Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
        Intent.ACTION_VIEW -> intent.dataString
        else -> null
    }?.takeIf { it.contains("tiktok", ignoreCase = true) }
}
