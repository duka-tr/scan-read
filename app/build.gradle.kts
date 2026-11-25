import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Release signing key, kept out of the repository (see README: keystore.properties).
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use(::load)
}

android {
    namespace = "tr.duka.scanread"
    compileSdk = 36

    defaultConfig {
        applicationId = "tr.duka.scanread"
        minSdk = 26
        targetSdk = 36
        versionCode = 4
        versionName = "0.1.3"

        // Where downloadable translation models are published (GitHub release assets). Forks and
        // local testing can override it: -Pscanread.modelReleasesUrl=http://localhost:8000
        val modelReleasesUrl = providers.gradleProperty("scanread.modelReleasesUrl")
            .getOrElse("https://github.com/duka-tr/scan-read/releases/download")
        buildConfigField("String", "MODEL_RELEASES_URL", "\"$modelReleasesUrl\"")

        // In-app tips through Google Play Billing. Off by default: selling through the store makes
        // the developer a "trader" whose address is shown publicly. Enable with -Pscanread.tips=true.
        val tipsEnabled = providers.gradleProperty("scanread.tips").getOrElse("false").toBoolean()
        buildConfigField("boolean", "TIPS_ENABLED", tipsEnabled.toString())

        // Where the build is distributed. "github" builds check GitHub releases for updates; Play
        // builds must not update outside Google Play. Build for Play with -Pscanread.distribution=play.
        val distribution = providers.gradleProperty("scanread.distribution").getOrElse("github")
        buildConfigField("boolean", "UPDATE_CHECK_ENABLED", (distribution == "github").toString())
        buildConfigField(
            "String",
            "RELEASES_API_URL",
            "\"https://api.github.com/repos/duka-tr/scan-read/releases?per_page=20\"",
        )
    }

    signingConfigs {
        if (keystoreProperties.isNotEmpty()) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    // One APK per CPU architecture for direct downloads (a phone needs only one set of native
    // libraries). Google Play builds the same split from the app bundle automatically, and the
    // bundle task refuses to run while APK splits are enabled, so Play builds turn them off.
    splits {
        abi {
            isEnable = providers.gradleProperty("scanread.distribution").getOrElse("github") != "play"
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = false
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
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)

    // Optional tips through Google Play.
    implementation(libs.billing.ktx)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.json)
}
