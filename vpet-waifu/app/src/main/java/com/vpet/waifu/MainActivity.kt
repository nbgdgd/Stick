package com.vpet.waifu

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.vpet.waifu.service.OverlayPermission
import com.vpet.waifu.service.PetOverlayService
import com.vpet.waifu.ui.PetViewModel
import com.vpet.waifu.ui.VPetApp
import com.vpet.waifu.ui.theme.VPetTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: PetViewModel by viewModels()

    /**
     * `SYSTEM_ALERT_WINDOW` is granted in a settings screen, not a dialog, so
     * the only way to learn the answer is to re-read it when we come back.
     */
    private var overlayPermissionGranted by mutableStateOf(false)

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* best effort */ }

    private val overlaySettingsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            overlayPermissionGranted = OverlayPermission.isGranted(this)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        overlayPermissionGranted = OverlayPermission.isGranted(this)
        requestNotificationPermissionIfNeeded()
        bindBubbleToggleToService()

        setContent {
            VPetTheme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()

                // Coming back from Settings by any route (back gesture, recents)
                // must refresh the answer, not just the launcher callback.
                LaunchedEffect(Unit) {
                    lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                        overlayPermissionGranted = OverlayPermission.isGranted(this@MainActivity)
                    }
                }

                VPetApp(
                    state = state,
                    viewModel = viewModel,
                    overlayPermissionGranted = overlayPermissionGranted,
                    onGrantOverlayPermission = {
                        overlaySettingsLauncher.launch(OverlayPermission.settingsIntent(this))
                    },
                )
            }
        }
    }

    /**
     * The preference is the source of truth; the service just follows it. That
     * keeps "is she on screen?" answerable after a reboot, when nothing has
     * touched the UI.
     */
    private fun bindBubbleToggleToService() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState
                    .distinctUntilChanged { old, new -> old.settings.bubbleEnabled == new.settings.bubbleEnabled }
                    .collect { state ->
                        if (state.settings.bubbleEnabled && OverlayPermission.isGranted(this@MainActivity)) {
                            PetOverlayService.start(this@MainActivity)
                        } else {
                            PetOverlayService.stop(this@MainActivity)
                        }
                    }
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        // Without it the foreground service still runs, but its ongoing
        // notification is silently dropped — worth asking once.
        if (!granted) notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
