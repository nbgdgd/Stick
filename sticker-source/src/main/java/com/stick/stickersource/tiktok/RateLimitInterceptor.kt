package com.stick.stickersource.tiktok

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Enforces a minimum spacing between requests to a given host.
 *
 * tikwm's free tier allows **1 request/second**; without spacing the comment
 * pager (and even the first comment call right after resolve) trips the limit and
 * returns `code:-1`. This interceptor serialises tikwm calls ≥ [minIntervalMs]
 * apart. It only throttles hosts containing [hostMatch], so downloading the actual
 * sticker files from the TikTok CDN is left at full speed.
 */
internal class RateLimitInterceptor(
    private val minIntervalMs: Long,
    private val hostMatch: String,
) : Interceptor {

    private val lock = Any()
    @Volatile private var lastStartMs = 0L

    override fun intercept(chain: Interceptor.Chain): Response {
        if (chain.request().url.host.contains(hostMatch, ignoreCase = true)) {
            synchronized(lock) {
                val wait = minIntervalMs - (System.currentTimeMillis() - lastStartMs)
                if (wait > 0) {
                    try {
                        Thread.sleep(wait)
                    } catch (e: InterruptedException) {
                        Thread.currentThread().interrupt()
                    }
                }
                lastStartMs = System.currentTimeMillis()
            }
        }
        return chain.proceed(chain.request())
    }
}
