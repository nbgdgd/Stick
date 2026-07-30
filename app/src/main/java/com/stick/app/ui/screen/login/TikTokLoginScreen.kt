package com.stick.app.ui.screen.login

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * In-app TikTok sign-in.
 *
 * Loads TikTok's own login page in a WebView — credentials are typed on TikTok's
 * page and are never seen, stored or transmitted by this app. Once the session
 * cookie appears in the WebView's cookie jar it is copied into app-private
 * storage so API calls can use the logged-in session, which is what makes the
 * full comment list (and therefore all stickers) reachable.
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sign in to TikTok") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            Text(
                "Log in on TikTok's own page. Stick never sees your password — it " +
                    "only keeps the session so it can read every comment.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )

            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    CookieManager.getInstance().setAcceptCookie(true)
                    WebView(context).apply {
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.userAgentString =
                            "Mozilla/5.0 (Linux; Android 14; SM-G991B) AppleWebKit/537.36 " +
                                "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                loading = false
                                // Capture the session as soon as TikTok sets it.
                                val cookies = CookieManager.getInstance()
                                    .getCookie("https://www.tiktok.com")
                                if (!cookies.isNullOrBlank() && cookies.contains("sessionid")) {
                                    viewModel.saveSession(cookies, onSignedIn)
                                }
                            }
                        }
                        loadUrl("https://www.tiktok.com/login")
                    }
                },
            )
        }
    }
}
