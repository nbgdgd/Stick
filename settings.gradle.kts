pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Stick"

// Core shared models & utilities. Has no Android dependencies beyond the minimum
// so it can be reused by any module, including the swappable sticker source.
include(":core")

// The pluggable sticker-acquisition module. It is fully decoupled from the app so
// that when TikTok changes how stickers are stored/served, only this module needs
// to be updated. See sticker-source/README.md.
include(":sticker-source")

// The application module: UI (Compose/Material 3), persistence, media conversion,
// dependency injection and navigation.
include(":app")

// ---------------------------------------------------------------------------
// vpet-waifu — a second, independent Android app that lives in this repository.
// It shares nothing with Stick except the Gradle wrapper and the version
// catalog. See vpet-waifu/README.md.
// ---------------------------------------------------------------------------

// Pure-Kotlin simulation: stats, balance tuning, the state machine and the
// deterministic "advance the world by N minutes" core. No Android APIs, so the
// whole game loop is unit-testable on the JVM.
include(":vpet-waifu:domain")

// The Android app: overlay bubble service, Compose UI, Room persistence and the
// WorkManager catch-up tick.
include(":vpet-waifu:app")
