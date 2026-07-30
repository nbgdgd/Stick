package com.stick.app.data.source

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.webkit.WebViewClient
import com.stick.core.model.CatalogQuery
import com.stick.core.model.RemoteSticker
import com.stick.core.model.StickerFormat
import com.stick.core.model.StickerOrigin
import com.stick.core.model.TikTokVideoRef
import com.stick.core.result.StickError
import com.stick.core.result.StickResult
import com.stick.stickersource.StickerSource
import com.stick.stickersource.StickerSource.Capability
import com.stick.stickersource.model.DownloadedAsset
import com.stick.stickersource.tiktok.AssetDownloader
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow
import org.json.JSONArray

/**
 * Reads comment stickers by loading the video page in an off-screen WebView and
 * harvesting the rendered comment list.
 *
 * ### Why this exists
 * TikTok's public comment API only returns a fraction of a busy video's comments
 * (measured: 86 of 206) — the rest need requests signed by TikTok's own
 * JavaScript (`X-Bogus`/`msToken`), and a logged-in session alone does not lift
 * the cap. Loading the real page lets TikTok's own scripts do that signing and
 * paginate normally; we just scroll and read what gets rendered.
 *
 * Trade-off: slower than the API and coupled to TikTok's markup, so it is
 * registered *ahead of* the API source but the API source stays as a fallback.
 */
class WebViewCommentSource(
    private val context: Context,
    private val downloader: AssetDownloader,
    /** How many scroll passes to make before giving up on new results. */
    private val maxScrolls: Int = 40,
    private val scrollDelayMs: Long = 900,
) : StickerSource {

    override val id: String = SOURCE_ID
    override val displayName: String = "TikTok (page)"
    override val capabilities: Set<Capability> =
        setOf(Capability.SCRAPE_COMMENTS, Capability.DOWNLOAD)

    @SuppressLint("SetJavaScriptEnabled")
    override fun stickersFromComments(
        video: TikTokVideoRef,
    ): Flow<StickResult<RemoteSticker>> = callbackFlow {
        val main = Handler(Looper.getMainLooper())
        var webView: WebView? = null
        val seen = HashSet<String>()

        main.post {
            val wv = WebView(context)
            webView = wv
            wv.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                userAgentString = CHROME_UA
            }
            // Give the page a real viewport; a zero-sized WebView renders nothing.
            wv.layout(0, 0, 1080, 2400)

            var pass = 0
            lateinit var pump: Runnable
            pump = Runnable {
                if (pass++ >= maxScrolls) {
                    close()
                    return@Runnable
                }
                wv.evaluateJavascript(HARVEST_JS) { raw ->
                    parseUrls(raw).forEach { url ->
                        if (seen.add(assetKey(url))) {
                            trySend(StickResult.Success(toSticker(url, video)))
                        }
                    }
                    main.postDelayed(pump, scrollDelayMs)
                }
            }

            wv.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    // Start harvesting once the page is up.
                    main.postDelayed(pump, 1_500)
                }

                /**
                 * TikTok's mobile page tries to hand off to the native app via a
                 * custom scheme (snssdk…/tiktok…). A WebView cannot open those and
                 * reports ERR_UNKNOWN_URL_SCHEME, so swallow anything that isn't
                 * http(s) and stay on the page.
                 */
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: android.webkit.WebResourceRequest?,
                ): Boolean {
                    val url = request?.url?.toString().orEmpty()
                    return !url.startsWith("http://") && !url.startsWith("https://")
                }

                @Deprecated("Kept for API < 24 devices")
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                    val u = url.orEmpty()
                    return !u.startsWith("http://") && !u.startsWith("https://")
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: android.webkit.WebResourceRequest?,
                    error: android.webkit.WebResourceError?,
                ) {
                    // Sub-resource failures (ads, trackers, app-handoff links) are
                    // normal on this page — only a failed main document is fatal.
                    if (request?.isForMainFrame != true) return
                    val url = request.url?.toString().orEmpty()
                    if (!url.startsWith("http")) return
                    trySend(
                        StickResult.Failure(
                            StickError.Network(error?.description?.toString() ?: "Page failed to load"),
                        ),
                    )
                    close()
                }
            }
            wv.loadUrl(video.canonicalUrl)
        }

        awaitClose {
            main.post {
                webView?.stopLoading()
                webView?.destroy()
                webView = null
            }
        }
    }

    private fun toSticker(url: String, video: TikTokVideoRef): RemoteSticker {
        val animated = url.contains(".awebp") || url.contains(".webp")
        return RemoteSticker(
            id = assetKey(url),
            sourceId = SOURCE_ID,
            name = "Sticker",
            downloadUrl = url,
            previewUrl = url,
            format = if (animated) StickerFormat.WEBP_ANIMATED else StickerFormat.JPEG,
            origin = StickerOrigin.Comment(video.canonicalUrl, "", ""),
        )
    }

    /** `evaluateJavascript` hands back a JSON string literal; unwrap it. */
    private fun parseUrls(raw: String?): List<String> = runCatching {
        if (raw.isNullOrBlank() || raw == "null") return emptyList()
        // Result arrives double-encoded: "\"[...]\"" → decode once, then parse.
        val json = if (raw.startsWith("\"")) {
            org.json.JSONTokener(raw).nextValue() as? String ?: return emptyList()
        } else {
            raw
        }
        val arr = JSONArray(json)
        (0 until arr.length()).map { arr.getString(it) }
    }.getOrDefault(emptyList())

    private fun assetKey(url: String): String =
        ASSET_ID_REGEX.find(url)?.groupValues?.get(1) ?: url.substringBefore('?')

    override suspend fun download(
        sticker: RemoteSticker,
        onProgress: (Float) -> Unit,
    ): StickResult<DownloadedAsset> = downloader.download(sticker, onProgress)

    // --- Unsupported here; the API source keeps these capabilities ----------
    override suspend fun resolveVideo(rawInput: String): StickResult<TikTokVideoRef> =
        StickResult.Failure(StickError.Unsupported("Handled by the API source"))

    override suspend fun searchCatalog(query: CatalogQuery): StickResult<List<RemoteSticker>> =
        StickResult.Success(emptyList())

    private companion object {
        const val SOURCE_ID = "tiktok-webview"
        val ASSET_ID_REGEX = Regex("""/([0-9a-f]{32})""")
        /**
         * Desktop UA on purpose: the mobile page pushes an "open in app"
         * interstitial and hides the comment list, while the desktop layout
         * renders comments inline where they can be scrolled and read.
         */
        const val CHROME_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

        /**
         * Scrolls every scrollable container (the comment panel is a nested
         * scroller, not the window) and returns the sticker image URLs currently
         * in the DOM. Avatars and video covers are filtered out by URL shape.
         */
        val HARVEST_JS = """
        (function() {
          try {
            window.scrollTo(0, document.body.scrollHeight);
            var nodes = document.querySelectorAll('div');
            for (var i = 0; i < nodes.length; i++) {
              var n = nodes[i];
              if (n.scrollHeight > n.clientHeight + 50) { n.scrollTop = n.scrollHeight; }
            }
            var out = [];
            var imgs = document.images;
            for (var j = 0; j < imgs.length; j++) {
              var s = imgs[j].currentSrc || imgs[j].src || '';
              if (!s || s.indexOf('tiktokcdn') === -1) continue;
              if (s.indexOf('-avt-') !== -1) continue;          // avatars
              if (s.indexOf('tplv-tiktokx-cropcenter') !== -1) continue; // covers
              var isSticker = s.indexOf('awebp') !== -1 ||
                              s.indexOf('sticker') !== -1 ||
                              s.indexOf('comment') !== -1;
              if (isSticker) out.push(s);
            }
            return JSON.stringify(out);
          } catch (e) { return JSON.stringify([]); }
        })();
        """.trimIndent()
    }
}
