package com.trialtracker.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.layout.Box
import com.trialtracker.app.ui.AppShell
import com.trialtracker.app.ui.AppViewModel
import com.trialtracker.app.ui.screens.OnboardingScreen
import com.trialtracker.app.ui.theme.TT
import com.trialtracker.app.ui.theme.TrialTrackerTheme

class MainActivity : ComponentActivity() {

    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* optional */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            TrialTrackerTheme {
                val viewModel: AppViewModel = viewModel(
                    factory = AppViewModel.Factory(application),
                )
                val state by viewModel.state.collectAsStateWithLifecycle()

                LaunchedEffect(state.settings.onboarded, state.settings.notificationsEnabled) {
                    if (state.settings.onboarded &&
                        state.settings.notificationsEnabled &&
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    ) {
                        requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                Box(Modifier.fillMaxSize().background(TT.Background)) {
                    if (state.settings.onboarded) {
                        AppShell(state = state, viewModel = viewModel)
                    } else {
                        OnboardingScreen(onDone = viewModel::completeOnboarding)
                    }
                }
            }
        }
    }
}
