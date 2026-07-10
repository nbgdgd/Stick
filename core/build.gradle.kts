plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

// Pure Kotlin/JVM module. Kept free of Android dependencies on purpose so the
// domain models and result types can be shared with any module (app, tests,
// and the swappable sticker source) without pulling in the Android framework.
dependencies {
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}
