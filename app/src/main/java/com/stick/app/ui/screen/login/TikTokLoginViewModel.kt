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

    private var saved = false

    /** Save once — onPageFinished can fire repeatedly as TikTok redirects. */
    fun saveSession(cookieHeader: String, onSaved: () -> Unit) {
        if (saved) return
        saved = true
        viewModelScope.launch {
            sessionRepository.save(cookieHeader)
            onSaved()
        }
    }
}
