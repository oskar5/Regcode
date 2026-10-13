import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Release signing values are read, in order of precedence, from:
//   1. keystore.properties   (local file, git-ignored)
//   2. local.properties      (local file, git-ignored)
//   3. environment variables (CI: CODEEDITOR_STORE_FILE, ...)
val signingProps = Properties().apply {
    listOf("local.properties", "keystore.properties").forEach { name ->
        rootProject.file(name).takeIf { it.exists() }?.inputStream()?.use { load(it) }
    }
}

fun signingValue(key: String, env: String): String? =
    signingProps.getProperty(key)?.takeIf { it.isNotBlank() }
        ?: System.getenv(env)?.takeIf { it.isNotBlank() }

val releaseStoreFile = signingValue("storeFile", "CODEEDITOR_STORE_FILE")
val releaseStorePassword = signingValue("storePassword", "CODEEDITOR_STORE_PASSWORD")
val releaseKeyAlias = signingValue("keyAlias", "CODEEDITOR_KEY_ALIAS")
val releaseKeyPassword = signingValue("keyPassword", "CODEEDITOR_KEY_PASSWORD")

val hasReleaseKeystore = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { !it.isNullOrBlank() }

// Never silently fall back to the debug key for a Play build.
val isReleaseBuild = gradle.startParameter.taskNames.any {
    it.contains("release", ignoreCase = true) || it.contains("bundle", ignoreCase = true)
}
if (isReleaseBuild && !hasReleaseKeystore) {
    throw GradleException(
        "Release signing is not configured.\n" +
            "Copy keystore.properties.template to keystore.properties, or add the keys " +
            "to local.properties, or export CODEEDITOR_STORE_FILE / CODEEDITOR_STORE_PASSWORD / " +
            "CODEEDITOR_KEY_ALIAS / CODEEDITOR_KEY_PASSWORD."
    )
}

android {
    namespace = "com.codeeditor"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.codeeditor"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        create("release") {
            storeFile = releaseStoreFile?.let { rootProject.file(it) }
            storePassword = releaseStorePassword
            keyAlias = releaseKeyAlias
            keyPassword = releaseKeyPassword
            // v2 + v3 are what Play Console verifies today. v1 (JAR) is not
            // needed because minSdk = 26 (v2 works from Android 7.0 / API 24).
            enableV1Signing = false
            enableV2Signing = true
            enableV3Signing = true
        }
    }

    buildTypes {
        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
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
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.documentfile)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.turbine)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
