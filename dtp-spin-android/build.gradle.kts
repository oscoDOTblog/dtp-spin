// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    // AGP 9 has built-in Kotlin: org.jetbrains.kotlin.android must NOT be applied.
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room.gradle) apply false
}
