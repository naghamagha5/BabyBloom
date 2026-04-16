plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    id("androidx.room") version "2.6.1"
}

android {
    namespace = "com.babybloom"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.babybloom"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    // ── Core ────────────────────────────────────────────────────────────────
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation(libs.androidx.compose.foundation.layout)
    implementation(libs.androidx.ui.graphics)

    // ── Compose ─────────────────────────────────────────────────────────────
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // ── Lifecycle + ViewModel ───────────────────────────────────────────────
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)

    // ── Room ────────────────────────────────────────────────────────────────
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // ── DataStore ───────────────────────────────────────────────────────────
    implementation(libs.datastore.preferences)

    // ── Navigation ──────────────────────────────────────────────────────────
    implementation(libs.navigation.compose)

    // ── Hilt ────────────────────────────────────────────────────────────────
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // ── Coroutines ──────────────────────────────────────────────────────────
    implementation(libs.coroutines.android)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-guava:1.7.3")

    // ── Security ────────────────────────────────────────────────────────────
    implementation(libs.security.crypto)
    implementation("org.mindrot:jbcrypt:0.4")

    // ── Media (ExoPlayer) ───────────────────────────────────────────────────
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")

    // ── ML Kit ──────────────────────────────────────────────────────────────
    implementation("com.google.mlkit:face-detection:16.1.7")

    // ── CameraX ─────────────────────────────────────────────────────────────
    val cameraVersion = "1.3.4"
    implementation("androidx.camera:camera-core:$cameraVersion")
    implementation("androidx.camera:camera-camera2:$cameraVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraVersion")
    implementation("androidx.camera:camera-view:$cameraVersion")

    // ── Charts ──────────────────────────────────────────────────────────────
    implementation("com.patrykandpatrick.vico:compose:2.0.0")
    implementation("com.patrykandpatrick.vico:compose-m3:2.0.0")

    // ── Gson ────────────────────────────────────────────────────────────────
    implementation("com.google.code.gson:gson:2.10.1")

    // ── Testing ─────────────────────────────────────────────────────────────
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    implementation("io.coil-kt:coil-compose:2.5.0")
}

room {
    schemaDirectory("$projectDir/schemas")
}