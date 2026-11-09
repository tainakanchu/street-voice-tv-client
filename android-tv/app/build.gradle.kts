plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.roborazzi)
}

// Robolectric android-all jars, fetched through Gradle so they share its repositories/cache.
val robolectricAndroidAll: Configuration by configurations.creating {
    isTransitive = false
}
val robolectricDepsDir = layout.buildDirectory.dir("robolectric-deps")
val copyRobolectricAndroidAll by tasks.registering(Sync::class) {
    from(robolectricAndroidAll)
    into(robolectricDepsDir)
}

android {
    namespace = "com.example.streetvoicetv"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.streetvoicetv"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField("String", "STREETVOICE_BASE_URL", "\"https://streetvoice.com\"")
    }

    signingConfigs {
        create("release") {
            val ksFile = System.getenv("KEYSTORE_FILE")
            if (ksFile != null) {
                storeFile = file(ksFile)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (System.getenv("KEYSTORE_FILE") != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
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

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.maxHeapSize = "2g"
                // Run Robolectric offline: the android-all jar is resolved by Gradle
                // (robolectricAndroidAll configuration below) instead of being downloaded
                // by Robolectric at test time.
                it.dependsOn(copyRobolectricAndroidAll)
                it.systemProperty("robolectric.offline", "true")
                it.systemProperty("robolectric.dependency.dir", robolectricDepsDir.get().asFile.absolutePath)
            }
        }
    }
}

roborazzi {
    outputDir.set(file("src/test/screenshots"))
}

dependencies {
    // Compose BOM
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    // Compose for TV
    implementation(libs.tv.foundation)
    implementation(libs.tv.material)

    // Activity & Core
    implementation(libs.activity.compose)
    implementation(libs.core.ktx)

    // Lifecycle
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)

    // Navigation
    implementation(libs.navigation.compose)

    // Media3
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.ui)
    implementation(libs.media3.session)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // OkHttp
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // Kotlinx Serialization
    implementation(libs.kotlinx.serialization.json)

    // Coil (image loading)
    implementation(libs.coil.compose)

    // Testing (JVM unit + Roborazzi screenshot tests)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    robolectricAndroidAll(libs.robolectric.android.all)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(composeBom)
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    debugImplementation(libs.compose.ui.test.manifest)
}
