package com.stick.app.data.source

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import com.stick.app.StickApplication
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
import org.json.JSONArray

/**
 * Reads comment stickers from the real TikTok page loaded in a WebView.
 *
 * ### Why this exists
 * The public comment API only returns part of a busy video's comments (measured:
 * 86 of 206) — the rest need requests signed by TikTok's own JavaScript, and
 * logging in does not lift the cap. Loading the actual page lets TikTok's scripts
 * do that signing and fetch comments normally.
 *
 * ### How it collects
 * Two independent channels, because a device log showed DOM-only scraping
 * returning nothing on every pass:
 *  1. **Network capture** — `fetch`/`XMLHttpRequest` are wrapped so every comment
 *     API response the page itself makes is scanned for sticker URLs. This works
 *     even when images are never painted.
 *  2. **DOM scraping** — images actually rendered in the comment list.
 *
 * The WebView is attached to the current window: detached, it has no real
 * viewport, so TikTok's lazy-loaded images never load (the earlier log was full
 * of "tile memory limits exceeded" and zero images).
 */
class WebViewCommentSource(
    private val context: Context,
    private val downloader: AssetDownloader,
    private val maxScrolls: Int = 45,
    private val scrollDelayMs: Long = 1_000,
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
        var host: ViewGroup? = null
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
                // Desktop site needs a desktop-width viewport to lay out the
                // comment panel at all.
                setSupportZoom(false)
                userAgentString = CHROME_UA
                blockNetworkImage = false
                loadsImagesAutomatically = true
            }

            // Attach off-screen to a real window so layout and lazy image loading
            // actually happen. Falls back to a detached view if no activity is up.
            val activity = StickApplication.ActivityTracker.activity
            if (activity != null) {
                val root = activity.window.decorView as? ViewGroup
                if (root != null) {
                    val holder = FrameLayout(activity)
                    holder.addView(wv, FrameLayout.LayoutParams(VIEW_W, VIEW_H))
                    // Off to the side: laid out and drawn, but never visible.
                    holder.translationX = -20_000f
                    root.addView(holder, FrameLayout.LayoutParams(VIEW_W, VIEW_H))
                    host = holder
                    Log.i(TAG, "webview attached to window (${VIEW_W}x$VIEW_H)")
                }
            }
            if (host == null) {
                wv.layout(0, 0, VIEW_W, VIEW_H)
                Log.i(TAG, "no activity; using detached webview (images may not load)")
            }

            var pass = 0
            lateinit var pump: Runnable
            pump = Runnable {
                if (pass++ >= maxScrolls) {
                    Log.i(TAG, "done after $pass passes, ${seen.size} unique stickers")
                    close()
                    return@Runnable
                }
                wv.evaluateJavascript(HARVEST_JS) { raw ->
                    val payload = parsePayload(raw)
                    val urls = payload.first
                    if (pass <= 3 || urls.isNotEmpty()) {
                        Log.i(TAG, "pass $pass: ${urls.size} urls (${payload.second})")
                    }
                    urls.forEach { url ->
                        if (seen.add(assetKey(url))) {
                            trySend(StickResult.Success(toSticker(url, video)))
                        }
                    }
                    main.postDelayed(pump, scrollDelayMs)
                }
            }

            wv.webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    // Install the network hook before the page's own scripts run.
                    view?.evaluateJavascript(HOOK_JS, null)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    view?.evaluateJavascript(HOOK_JS, null)
                    Log.i(TAG, "page loaded: ${url?.take(80)}")
                    main.postDelayed(pump, 2_000)
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: android.webkit.WebResourceRequest?,
                ): Boolean {
                    val u = request?.url?.toString().orEmpty()
                    return !u.startsWith("http://") && !u.startsWith("https://")
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
                    if (request?.isForMainFrame != true) return
                    Log.i(TAG, "main-frame error: ${error?.description}")
                    trySend(
                        StickResult.Failure(
                            StickError.Network(error?.description?.toString() ?: "Page failed to load"),
                        ),
                    )
                    close()
                }
            }
            Log.i(TAG, "loading ${video.canonicalUrl}")
            wv.loadUrl(video.canonicalUrl)
        }

        awaitClose {
            main.post {
                webView?.stopLoading()
                host?.let { h ->
                    (h.parent as? ViewGroup)?.removeView(h)
                    h.removeAllViews()
                }
                webView?.destroy()
                webView = null
                host = null
            }
        }
    }

    private fun toSticker(url: String, video: TikTokVideoRef): RemoteSticker {
        val animated = url.contains("awebp") || url.contains(".webp")
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

    /** Returns the URLs plus a short diagnostic string from the page. */
    private fun parsePayload(raw: String?): Pair<List<String>, String> = runCatching {
        if (raw.isNullOrBlank() || raw == "null") return emptyList<String>() to "null"
        val json = if (raw.startsWith("\"")) {
            org.json.JSONTokener(raw).nextValue() as? String ?: return emptyList<String>() to "unwrap-failed"
        } else {
            raw
        }
        val obj = org.json.JSONObject(json)
        val arr: JSONArray = obj.optJSONArray("urls") ?: JSONArray()
        val list = (0 until arr.length()).map { arr.getString(it) }
        list to obj.optString("info")
    }.getOrDefault(emptyList<String>() to "parse-failed")

    private fun assetKey(url: String): String =
        ASSET_ID_REGEX.find(url)?.groupValues?.get(1) ?: url.substringBefore('?')

    override suspend fun download(
        sticker: RemoteSticker,
        onProgress: (Float) -> Unit,
    ): StickResult<DownloadedAsset> = downloader.download(sticker, onProgress)

    override suspend fun resolveVideo(rawInput: String): StickResult<TikTokVideoRef> =
        StickResult.Failure(StickError.Unsupported("Handled by the API source"))

    override suspend fun searchCatalog(query: CatalogQuery): StickResult<List<RemoteSticker>> =
        StickResult.Success(emptyList())

    private companion object {
        const val TAG = "StickDiag"
        const val SOURCE_ID = "tiktok-webview"
        const val VIEW_W = 1600
        const val VIEW_H = 2400
        val ASSET_ID_REGEX = Regex("""/([0-9a-f]{32})""")
        /**
         * Desktop UA, not mobile. A device log showed the mobile page loading with
         * only 8 images and apiHits=0 — TikTok's mobile web never fetches comments
         * at all, it just offers to open the app. The desktop layout renders the
         * comment list and issues the /api/comment/ requests we capture.
         */
        const val CHROME_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

        /**
         * Wraps fetch/XHR so sticker URLs can be read straight out of the comment
         * responses TikTok fetches itself — these are properly signed, and they
         * arrive even if the images are never painted.
         */
        val HOOK_JS = """
        (function(){
          if (window.__stickHooked) return; window.__stickHooked = true;
          window.__stickUrls = []; window.__stickHits = 0;
          function grab(text){
            try{
              var re = /https:[^"\\\\ ]*?(?:awebp|\.image|\.jpeg|\.webp|\.gif)[^"\\\\ ]*/g, m;
              while ((m = re.exec(text))) {
                var u = m[0].replace(/\\u0026/g, '&').replace(/\\\//g, '/');
                if (u.indexOf('-avt-') !== -1) continue;
                if (u.indexOf('tiktokcdn') === -1) continue;
                window.__stickUrls.push(u);
              }
            } catch(e) {}
          }
          var of = window.fetch;
          if (of) window.fetch = function(){
            var p = of.apply(this, arguments);
            try {
              var a0 = arguments[0];
              var url = (a0 && a0.url) ? a0.url : String(a0 || '');
              if (url.indexOf('/api/comment/') !== -1) {
                window.__stickHits++;
                p.then(function(r){ try { r.clone().text().then(grab); } catch(e){} });
              }
            } catch(e) {}
            return p;
          };
          var oo = XMLHttpRequest.prototype.open, os = XMLHttpRequest.prototype.send;
          XMLHttpRequest.prototype.open = function(m, u){ this.__su = String(u||''); return oo.apply(this, arguments); };
          XMLHttpRequest.prototype.send = function(){
            var s = this;
            try {
              s.addEventListener('load', function(){
                try { if (s.__su && s.__su.indexOf('/api/comment/') !== -1) { window.__stickHits++; grab(s.responseText); } } catch(e){}
              });
            } catch(e) {}
            return os.apply(this, arguments);
          };
        })();
        """.trimIndent()

        /**
         * Scrolls every scrollable container (the comment list is a nested
         * scroller) and returns both the captured network URLs and any sticker
         * images currently in the DOM, plus counters for diagnosis.
         */
        val HARVEST_JS = """
        (function() {
          try {
            window.scrollTo(0, document.body.scrollHeight);
            var divs = document.querySelectorAll('div');
            var scrollers = 0;
            for (var i = 0; i < divs.length; i++) {
              var n = divs[i];
              if (n.scrollHeight > n.clientHeight + 50) { n.scrollTop = n.scrollHeight; scrollers++; }
            }
            var out = [];
            if (window.__stickUrls && window.__stickUrls.length) {
              out = out.concat(window.__stickUrls);
              window.__stickUrls = [];
            }
            var imgs = document.images, domHits = 0;
            for (var j = 0; j < imgs.length; j++) {
              var s = imgs[j].currentSrc || imgs[j].src || '';
              if (!s || s.indexOf('tiktokcdn') === -1) continue;
              if (s.indexOf('-avt-') !== -1) continue;
              if (s.indexOf('tplv-tiktokx-cropcenter') !== -1) continue;
              if (s.indexOf('awebp') !== -1 || s.indexOf('sticker') !== -1 || s.indexOf('comment') !== -1) {
                out.push(s); domHits++;
              }
            }
            return JSON.stringify({
              urls: out,
              info: 'imgs=' + imgs.length + ' dom=' + domHits +
                    ' scrollers=' + scrollers + ' apiHits=' + (window.__stickHits || 0)
            });
          } catch (e) {
            return JSON.stringify({ urls: [], info: 'js-error ' + e });
          }
        })();
        """.trimIndent()
    }
}
