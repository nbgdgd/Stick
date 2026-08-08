# `:sticker-source` — the swappable acquisition module

Everything about *where stickers come from* lives here and **nowhere else**. The
rest of the app depends only on the [`StickerSource`](src/main/java/com/stick/stickersource/StickerSource.kt)
interface and the [`StickerSourceRegistry`](src/main/java/com/stick/stickersource/StickerSourceRegistry.kt).
When TikTok changes something, you update this module and rebuild — no ViewModel,
screen, database, or converter is touched.

## Design contract

```
app  ──▶  StickerSourceRegistry  ──▶  StickerSource (interface)
                                          ├─ TikTokStickerSource   (comments + catalog)
                                          ├─ LocalFileStickerSource
                                          └─ ClipboardStickerSource
```

* The module depends on `:core` only. It never depends on `:app`, Room, Compose,
  or Hilt.
* Each source advertises `capabilities`; the UI enables/disables affordances from
  that set, so adding or removing a backend needs no UI change.
* Registration **order = priority**. `registry.primaryFor(SCRAPE_COMMENTS)`
  returns the first source that can do it.

## Research: how do we obtain TikTok animated stickers?

TikTok does **not** publish an official public API for comment stickers, and its
web endpoints (`/api/comment/list/`) require request signing (`X-Bogus` /
`msToken`) — a plain client gets nothing back. Approaches evaluated:

| Path | Verdict |
|------|---------|
| **TikTok web comment API directly** | Requires `X-Bogus`/`msToken` signing; not viable without a signer. |
| **tikwm.com proxy** (`/api/`, `/api/comment/list/`) | ✅ **Implemented.** Performs the signing server-side and returns plain JSON. Resolves full URLs *and* `vm.tiktok.com` short links, and exposes comment stickers under `comments[].images[]`. |
| **App resource / traffic analysis** | Documented escape hatch; highest maintenance, not implemented. |

### Current working backends

* **Comments** → `TikTokStickerSource` over `TikTokApi` (tikwm). Resolves a link to
  an aweme id, then pages the comment list, emitting each `images[]` URL as a
  `RemoteSticker`.
* **Catalog search** → `GiphyStickerSource` over `GiphyApi`. TikTok has no public
  sticker-catalog API, so keyword search is served by Giphy's animated stickers
  (transparent WebP). Swappable like everything else — a signed TikTok catalog can
  replace it later without touching the UI.

### If tikwm changes or dies

Because everything funnels through `StickerSource`, swapping the proxy is a change
to `TikTokApi` + `TikTokMapper` (+ the DTOs in `tiktok/dto/`). Add signing, if you
ever go direct to TikTok, as an OkHttp `Interceptor` in `TikTokSourceFactory`.

## Swapping the backend when TikTok changes

1. **Field moved/renamed** → edit the DTO in `tiktok/dto/` and the one mapping in
   `TikTokMapper`.
2. **Endpoint path/params changed** → edit `TikTokApi`.
3. **New signing required** → add an interceptor in `TikTokSourceFactory`.
4. **Whole approach changed** → write a new `StickerSource` and register it first
   in the registry. The old one can stay as a fallback.

## Legal / usage note

This module fetches stickers a user can already view in the TikTok app. Respect
TikTok's Terms of Service and creators' rights, rate-limit requests, and use it
only for content the user is entitled to save.
