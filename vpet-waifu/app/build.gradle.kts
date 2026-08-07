plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.vpet.waifu"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.vpet.waifu"
        minSdk = 26
        targetSdk = 36
        versionCode = 9
        versionName = "1.4.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        // The launcher name comes from here rather than straight from
        // strings.xml, so a build type can override it. Two builds that install
        // side by side under different applicationIds still looked identical in
        // the launcher — same name, same icon — which is how somebody ends up
        // testing yesterday's build and reporting that nothing was fixed.
        manifestPlaceholders["appLabel"] = "@string/app_name"

    }

    // The store key never lives in the repo: the four RELEASE_* values arrive
    // as -P properties (or gradle.properties on the machine that holds the
    // keystore). Without them the release build stays unsigned, same as before.
    val hasReleaseKey = listOf(
        "RELEASE_STORE_FILE", "RELEASE_STORE_PASSWORD", "RELEASE_KEY_ALIAS", "RELEASE_KEY_PASSWORD",
    ).all { project.hasProperty(it) }
    if (hasReleaseKey) {
        signingConfigs {
            create("release") {
                storeFile = file(project.property("RELEASE_STORE_FILE") as String)
                storePassword = project.property("RELEASE_STORE_PASSWORD") as String
                keyAlias = project.property("RELEASE_KEY_ALIAS") as String
                keyPassword = project.property("RELEASE_KEY_PASSWORD") as String
            }
        }
    }

/**
 * The one thing that must never reach a store.
 *
 * `assets/pets/` holds sprite packs, and the pack used to develop this is a
 * character somebody else owns. It is gitignored, so a clean checkout is
 * clean — but a *developer's* tree is not, and the release APK is built from
 * that tree. "Remember to move the folder before you publish" is not a
 * safeguard, it is a thing to forget once.
 *
 * So the release build refuses to run while anything is in there. Debug and
 * preview are untouched: testing with a pack is the whole reason it exists.
 */
val checkNoBundledPacks = tasks.register("checkNoBundledPacks") {
    val packs = layout.projectDirectory.dir("src/main/assets/pets")
    doLast {
        val present = packs.asFile.listFiles()?.filter { it.isDirectory }.orEmpty()
        if (present.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("Release build refused: sprite packs are in the tree.")
                    present.forEach { appendLine("  src/main/assets/pets/" + it.name) }
                    appendLine()
                    appendLine("These are development-only assets and at least one of them is")
                    appendLine("a character the project does not own. Move them out before")
                    appendLine("building anything for publication:")
                    appendLine("  mv app/src/main/assets/pets ~/vpet-packs-parked")
                },
            )
        }
    }
}

    buildTypes {
        debug {
            // Debug and release shared one applicationId, so the phone treated
            // them as the same app signed by two different keys and refused the
            // second install outright — "package conflicts with an existing
            // package". Now a test build sits beside the real one instead of
            // fighting it, and neither can overwrite the other's save.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            manifestPlaceholders["appLabel"] = "Вайфу DEBUG"
        }
        /**
         * A build to hand somebody, that will install whatever is already on
         * their phone.
         *
         * Optimised exactly like release — same R8, same shrinking, so it is a
         * fair thing to judge the app's smoothness by — but under its own
         * applicationId and signed with the debug key. A different package id
         * means the installer has nothing to compare signatures against, so it
         * simply installs, beside anything else, with its own save.
         */
        create("preview") {
            initWith(getByName("release"))
            applicationIdSuffix = ".preview"
            versionNameSuffix = "-preview"
            signingConfig = signingConfigs.getByName("debug")
            manifestPlaceholders["appLabel"] = "Вайфу TEST"
            isMinifyEnabled = true
            isShrinkResources = true
            matchingFallbacks += listOf("release")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (hasReleaseKey) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlinOptions {
        jvmTarget = "21"
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            // Store screenshots are rendered by a unit test on demand; without
            // the flag it does nothing, so ordinary runs stay fast.
            all { it.systemProperty("storeshots", providers.gradleProperty("storeshots").getOrElse("")) }
        }
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(project(":vpet-waifu:domain"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.activity.compose)

    // The overlay bubble is a Compose tree hosted by a Service, so the service
    // has to be a lifecycle + saved-state owner for the ComposeView to attach.
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.savedstate.ktx)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Background stat ticking that survives the app being swiped away.
    implementation(libs.work.runtime.ktx)
    implementation(libs.hilt.work)
    ksp(libs.hilt.androidx.compiler)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Home-screen widget.
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)

    implementation(libs.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    // Robolectric runs the Android framework on the JVM, which is the only way
    // to exercise Room, RemoteViews and the bitmap encoder without a device —
    // this container has no KVM, so an emulator is not an option.
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.room.testing)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.test.manifest)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.espresso.core)
}

// The guard runs before anything release-shaped is packaged. Wired by name
// rather than by variant API so it covers the bundle as well as the APK —
// itch.io takes the APK, a store takes the bundle, and both come from here.
afterEvaluate {
    listOf("packageRelease", "bundleRelease").forEach { name ->
        tasks.findByName(name)?.dependsOn("checkNoBundledPacks")
    }
}
