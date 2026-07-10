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

TikTok does **not** publish an official public API for comment stickers. Three
technically viable acquisition paths were evaluated:

| Path | How | Trade-offs | Status here |
|------|-----|-----------|-------------|
| **Web comment API** (`/api/comment/list/`) | The same JSON endpoint tiktok.com calls to render a comment thread. Animated stickers appear under `sticker.animate_url` (or the legacy `image_list[].gif_url`). | Undocumented; needs browser-like headers and occasionally a signature param. Field names drift. | **Implemented** (`TikTokApi`, `TikTokMapper`). |
| **Comment-sticker catalog** (`/api/comment/sticker/list/`) | The pool the comment composer picks from. Lets users browse/search stickers **without opening a video**. | Availability varies by region/app-version; may require the same signing. | **Implemented** with a local-filter fallback (see below). |
| **App resource / traffic analysis** | Inspect the official app's bundled resources or captured traffic. | Highest maintenance, most fragile, ToS-sensitive. | **Not implemented** — documented as the escape hatch if both endpoints die. |

### Signing (`X-Bogus` / `msToken`)

Some TikTok endpoints require an anti-bot signature. That concern is deliberately
isolated so it can be added without disturbing callers: put the logic in an
OkHttp `Interceptor` inside [`TikTokSourceFactory`](src/main/java/com/stick/stickersource/tiktok/TikTokSourceFactory.kt).
The rest of the module is unaffected.

### Catalog search fallback

If keyword search is unavailable, page the full catalog and filter on-device by
`name`/`keywords`. Because search returns the domain `RemoteSticker` type, the
fallback is invisible to the app.

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
