buildscript {
    repositories {
        google()
    }
    dependencies {
        // AGP 8.13 bundles R8 8.13.x, which cannot read Kotlin 2.4 metadata
        // (needs R8 >= 9.1.29, see https://developer.android.com/build/kotlin-support).
        classpath(libs.r8)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.roborazzi) apply false
}
