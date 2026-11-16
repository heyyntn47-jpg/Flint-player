// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 heyyntn47

import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Release signing. Values come from keystore.properties in the project root (local builds)
// or from FLINT_* environment variables (GitHub Actions). If neither is set up, the release
// build falls back to the debug key so it still builds, but that APK can't update a signed one.
val keystoreProps = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
fun signingValue(property: String, env: String): String? =
    (keystoreProps.getProperty(property) ?: System.getenv(env))?.takeIf { it.isNotBlank() }

val releaseStoreFile = signingValue("storeFile", "FLINT_KEYSTORE_FILE")
    ?.let { rootProject.file(it) }
    ?.takeIf { it.exists() }
val releaseStorePassword = signingValue("storePassword", "FLINT_KEYSTORE_PASSWORD")
val releaseKeyAlias = signingValue("keyAlias", "FLINT_KEY_ALIAS")
val releaseKeyPassword = signingValue("keyPassword", "FLINT_KEY_PASSWORD")
val hasReleaseSigning = releaseStoreFile != null && releaseStorePassword != null &&
    releaseKeyAlias != null && releaseKeyPassword != null

android {
    namespace = "com.flint.player"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.flint.player"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    // libVLC ships large native libraries (roughly 20+ MB per ABI). Instead of one fat APK, build
    // one APK per architecture, plus a universal one that runs everywhere. Users download only
    // the one that matches their phone. (Do not combine this with ndk.abiFilters.)
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = true
        }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
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
            signingConfig = signingConfigs.getByName(if (hasReleaseSigning) "release" else "debug")
        }
    }

    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint { checkReleaseBuilds = false }

    testOptions {
        // Lets plain JVM unit tests call code that touches Android stubs without crashing
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    // Compose. No BOM: Material 3 1.5 is a release candidate and pulls its own matching
    // foundation / ui versions. These are only the minimums.
    implementation("androidx.compose.ui:ui:1.12.1")
    implementation("androidx.compose.foundation:foundation:1.12.1")
    implementation("androidx.compose.material3:material3:1.5.0-rc01")
    // Last published version of the extended icon set
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.navigation:navigation-compose:2.10.2")

    // HCT-based Material 3 color schemes from a single seed color
    implementation("com.materialkolor:material-kolor:5.0.0")

    implementation("org.videolan.android:libvlc-all:3.7.6")

    // Installs the baseline profile on first launch, so the app starts fast right after install
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")

    testImplementation("junit:junit:4.13.2")
}
