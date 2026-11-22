import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.scanread"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.scanread"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        // Where downloadable translation models are published (GitHub release assets). Forks and
        // local testing can override it: -Pscanread.modelReleasesUrl=http://localhost:8000
        val modelReleasesUrl = providers.gradleProperty("scanread.modelReleasesUrl")
            .getOrElse("https://github.com/DursunKm/scan-read/releases/download")
        buildConfigField("String", "MODEL_RELEASES_URL", "\"$modelReleasesUrl\"")

        ndk {
            // Phones (64/32-bit ARM) and x86_64 emulators; keeps native libraries out of other ABIs.
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    androidResources {
        // Builds the per-app language list (Android 13+) from the values-* folders.
        generateLocaleConfig = true
    }

    lint {
        // All user-facing text must live in string resources.
        error.add("HardcodedText")
        error.add("MissingTranslation")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)

    // Bundled (offline) Latin-script text recognition model.
    implementation(libs.mlkit.text.recognition)
    // On-device translation; language models are downloaded once, then work offline.
    implementation(libs.mlkit.translate)
    // Runs the high-quality Opus-MT translation models.
    implementation(libs.onnxruntime.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}
