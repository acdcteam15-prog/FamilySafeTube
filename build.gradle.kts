// AGP 9 has Kotlin built in, so there is no "kotlin-android" plugin any more. The Compose plugin below
// pins the Kotlin Gradle plugin version that AGP uses.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
