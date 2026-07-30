package com.stick.app.ui.screen.login

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.delay

/**
 * In-app TikTok sign-in.
 *
 * Credentials are typed on TikTok's own page — this app never sees them. Once the
 * session cookie exists it is copied into app-private storage so API calls run
 * as the signed-in user, which is what makes the full comment list reachable.
 *
 * The cookie is captured by **polling**, not by page-load callbacks: TikTok's
 * login is a single-page app, so after a successful login no further
 * `onPageFinished` fires and a callback-only capture silently missed the session.
 * Leaving the screen also captures whatever is there, so pressing Back after a
 * successful login still saves it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TikTokLoginScreen(
    onBack: () -> Unit,
    onSignedIn: () -> Unit,
    viewModel: TikTokLoginViewModel = hiltViewModel(),
) {
    var loading by remember { mutableStateOf(true) }
    var status by remember { mutableStateOf("Waiting for sign-in…") }

    fun currentCookies(): String? {
        val cm = CookieManager.getInstance()
        cm.flush()
        return cm.getCookie("https://www.tiktok.com")
            ?: cm.getCookie("https://tiktok.com")
    }

    fun captureNow(finish: Boolean) {
        val cookies = currentCookies()
        if (!cookies.isNullOrBlank() && cookies.contains("sessionid")) {
            viewModel.saveSession(cookies) { if (finish) onSignedIn() else Unit }
            status = "Signed in ✓"
        } else if (finish) {
            onBack()
        }
    }

    // Poll for the session cookie — survives SPA navigation that fires no callback.
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            val cookies = currentCookies()
            if (!cookies.isNullOrBlank() && cookies.contains("sessionid")) {
                status = "Signed in ✓"
                viewModel.saveSession(cookies, onSignedIn)
                break
            }
        }
    }

    // Back must not lose a session that was just established.
    BackHandler { captureNow(finish = true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sign in to TikTok") },
                navigationIcon = {
                    IconButton(onClick = { captureNow(finish = true) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = { captureNow(finish = true) }) { Text("Done") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            Text(
                "$status  ·  Log in on TikTok's own page — Stick never sees your " +
                    "password, it only keeps the session so it can read every comment.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            AndroidView(
                // weight(), not fillMaxSize(): inside a Column a fillMaxSize child
                // is measured against the full column height and the WebView ended
                // up with no usable viewport — that was the blank white area.
                modifier = Modifier.fillMaxWidth().weight(1f),
                factory = { context ->
                    CookieManager.getInstance().apply {
                        setAcceptCookie(true)
                    }
                    WebView(context).apply {
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            loadWithOverviewMode = true
                            useWideViewPort = true
                            javaScriptCanOpenWindowsAutomatically = true
                            setSupportMultipleWindows(false)
                            mediaPlaybackRequiresUserGesture = false
                        }
                        // A WebChromeClient is required for pages that use JS
                        // dialogs/popups; without it TikTok's login can hang blank.
                        webChromeClient = WebChromeClient()
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                loading = false
                                captureNow(finish = false)
                            }

                            override fun doUpdateVisitedHistory(
                                view: WebView?,
                                url: String?,
                                isReload: Boolean,
                            ) {
                                // Fires on SPA route changes too.
                                captureNow(finish = false)
                            }
                        }
                        loadUrl("https://www.tiktok.com/login")
                    }
                },
                onRelease = { it.destroy() },
            )
        }
    }

    // Persist cookies when the screen goes away for any reason.
    DisposableEffect(Unit) {
        onDispose {
            val cookies = currentCookies()
            if (!cookies.isNullOrBlank() && cookies.contains("sessionid")) {
                viewModel.saveSession(cookies) {}
            }
        }
    }
}
