package com.stick.stickersource.tiktok

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Supplies the signed-in TikTok session for API calls.
 *
 * TikTok's public comment endpoint only returns a fraction of a busy video's
 * comments to an anonymous client — the rest require a logged-in session. The app
 * owns the login UI and cookie storage; this module only asks for a `Cookie`
 * header, so `:sticker-source` stays free of Android UI and storage concerns.
 *
 * Returning `null`/blank keeps the previous anonymous behaviour, so the app works
 * fine without logging in.
 */
fun interface TikTokCookieProvider {
    /** Current cookie header (e.g. `sessionid=…; tt_webid=…`), or null if signed out. */
    fun cookieHeader(): String?
}

/**
 * Attaches the session cookies to tiktok.com requests only.
 *
 * Scoped by host on purpose: credentials must never leak to the CDN hosts the
 * downloader talks to, or to any other source (Giphy).
 */
internal class SessionInterceptor(
    private val cookieProvider: TikTokCookieProvider,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val host = request.url.host
        if (!host.endsWith("tiktok.com", ignoreCase = true)) {
            return chain.proceed(request)
        }
        val cookies = cookieProvider.cookieHeader()
        if (cookies.isNullOrBlank()) return chain.proceed(request)

        return chain.proceed(
            request.newBuilder()
                .header("Cookie", cookies)
                .build(),
        )
    }
}
