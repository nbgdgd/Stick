plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.trialtracker.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.trialtracker.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        vectorDrawables { useSupportLibrary = true }

        // Remote catalog endpoint. Overridable per build type so a fork can point
        // at its own GitHub Pages / Firebase Hosting copy without touching code.
        buildConfigField(
            "String",
            "CATALOG_URL",
            "\"https://raw.githubusercontent.com/nbgdgd/stick/main/catalog/deals.json\"",
        )
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            // The debug signing config is attached on purpose: this project ships
            // an installable APK directly. Swap in a real keystore before
            // publishing to Play.
            signingConfig = signingConfigs.getByName("debug")
            // R8 stays off for the shipped build: the APK here is handed to users
            // directly and there is no device in the loop to smoke-test a shrunk
            // build against. proguard-rules.pro already carries the keep rules for
            // when it is switched on.
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)

    implementation(libs.okhttp)
    implementation(libs.coil.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    // Renders the Compose screens on the JVM so the layout can be checked
    // against the design without a device. See ui/HomeScreenRenderTest.
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.test.manifest)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
}

tasks.withType<Test>().configureEach {
    // The render test writes PNGs rather than comparing them, so it always records.
    systemProperty("roborazzi.test.record", "true")
    // Robolectric's native graphics runtime can only be initialised once per JVM,
    // and it clashes with the plain-JVM parser tests sharing the same fork.
    setForkEvery(1)
}
