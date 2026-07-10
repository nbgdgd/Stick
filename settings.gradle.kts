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
