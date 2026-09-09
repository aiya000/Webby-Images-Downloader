// Root project: only declares plugin versions. See app/build.gradle.kts for the actual configuration.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
