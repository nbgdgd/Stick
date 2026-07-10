# Stick

A modern Android app for **saving animated stickers from TikTok comments** and
converting them into **Telegram sticker formats** — as fast and frictionless as
possible.

> Built with Kotlin, Jetpack Compose, Material 3 (Material You), MVVM,
> Coroutines/Flow, Room, Hilt and Media3.

## Highlights

| Area | What's implemented |
|------|--------------------|
| **Import** | Paste a link · open via *Share* · deep-link `tiktok.com` URLs · resolve `vm.tiktok.com` short links · auto-scan comments · stream previews · multi-select · batch download · local-file & clipboard import |
| **Catalog** | In-app search over TikTok's comment-sticker catalog — browse & save without opening a video |
| **Viewer** | Play/pause · frame-step · FPS · resolution · size · duration · frame count |
| **Editor** | Non-destructive pipeline: resize · speed · FPS · trim · opacity · crop · rotate · flip · center · background color · remove background · text · merge |
| **Export** | Telegram `.webm` · `.tgs` · GIF · WebP · MP4 · APNG, with size/FPS/quality/bitrate controls and a **live size estimate** |
| **Telegram** | One-tap share to Telegram · open @Stickers bot · multi-file export |
| **Library** | Auto-save · favorites · collections · search · filter · sort · history · duplicate removal (content-hash) |
| **UI** | Material You · light / dark / **AMOLED** themes · dynamic color · adjustable card size · grid/list |

## Architecture

Clean, modular, MVVM. The dependency graph is strictly one-directional:

```
                ┌────────────────────────────┐
   :app  ─────▶ │ :sticker-source (swappable) │ ─┐
   (UI, Room,   └────────────────────────────┘  │
    Hilt,          TikTok / clipboard / local     ├──▶ :core
    Media3)     ┌────────────────────────────┐   │   (pure-Kotlin
        └─────▶ │  MediaConverter (pluggable) │ ──┘    models + Result)
                └────────────────────────────┘
                 Media3 (HW)  +  FFmpeg backend
```

Two deliberate seams make the app resilient and testable:

1. **`:sticker-source`** — *where stickers come from*. The whole app depends only
   on the `StickerSource` interface + `StickerSourceRegistry`. When TikTok changes
   its API, **only this module changes**. See
   [`sticker-source/README.md`](sticker-source/README.md) for the research on
   TikTok's endpoints and the swap playbook.
2. **`MediaConverter` / `FrameFormatConverter`** — *how media is encoded*.
   Hardware-accelerated Media3 handles WebM/MP4; a swappable FFmpeg backend covers
   GIF/WebP/APNG and a Lottie packer covers `.tgs`.

### Modules

| Module | Type | Responsibility |
|--------|------|----------------|
| `:core` | Kotlin/JVM | Domain models (`RemoteSticker`, `MediaInfo`, `StickerFormat`), `StickResult` |
| `:sticker-source` | Android lib | Acquisition: TikTok comments + catalog, clipboard, local files |
| `:app` | Android app | Compose UI, Room library, Hilt DI, Media3 pipeline, navigation |

### Layering inside `:app`

`ui/` (Compose screens + ViewModels) → `domain/` (use cases, converter contracts)
→ `data/` (Room, DataStore repositories). ViewModels never touch Room or a
`StickerSource` directly.

## Performance

- Hardware-accelerated encoding via Media3 `Transformer` / `MediaCodec`.
- Coroutines + `Flow` throughout; comment scanning streams previews incrementally.
- Coil memory+disk cache for preview thumbnails.
- Batch downloads report aggregate progress and run off the main thread.

## Build

```bash
./gradlew :app:assembleDebug     # build the APK
./gradlew test                   # run JVM unit tests
```

Requires the Android SDK (compileSdk 35, minSdk 26) and JDK 17.

## Status & notes

This repository is a complete, documented architecture with the full feature
surface wired end-to-end. A few encoders that the Android platform cannot provide
(animated GIF/WebP/APNG, `.tgs`) are routed through the `FrameFormatConverter`
seam and ship with safe "backend not bundled" fallbacks — drop in an FFmpeg build
to enable them (see `di/MediaModule.kt`). WebM and MP4 export work out of the box.

## Legal

Stick fetches stickers a user can already view in TikTok. Respect TikTok's Terms
of Service, rate-limit requests, and use it only for content you're entitled to
save.
