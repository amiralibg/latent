plugins {
    // AGP 9 has built-in Kotlin support, so there is no kotlin-android plugin here.
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
