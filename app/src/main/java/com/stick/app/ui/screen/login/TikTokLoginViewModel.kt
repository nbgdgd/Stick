package com.stick.app.ui.screen.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stick.app.data.repository.TikTokSessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Persists the captured TikTok session and signals the UI to leave the login screen. */
@HiltViewModel
class TikTokLoginViewModel @Inject constructor(
    private val sessionRepository: TikTokSessionRepository,
) : ViewModel() {

    /** Last value written, so repeated captures don't spam DataStore… */
    private var lastSaved: String? = null

    /**
     * Save the cookie header. Called repeatedly by the poller/callbacks, so it
     * skips no-op writes but still persists a *refreshed* cookie (TikTok rotates
     * these during login — an earlier "save once" guard dropped the final value).
     */
    fun saveSession(cookieHeader: String, onSaved: () -> Unit) {
        if (cookieHeader == lastSaved) return
        lastSaved = cookieHeader
        viewModelScope.launch {
            sessionRepository.save(cookieHeader)
            onSaved()
        }
    }
}
