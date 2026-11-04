import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    // No kotlin-android plugin: AGP 9 compiles Kotlin itself (built-in Kotlin). The Compose compiler plugin is still separate.
    alias(libs.plugins.kotlin.compose)
}

// ---------- version ----------
// versionName comes from version.properties; versionCode = offset + build number (GitHub Actions run number).
val versionProps = Properties().apply { rootProject.file("version.properties").inputStream().use { load(it) } }
val appVersionName: String = versionProps.getProperty("versionName", "1.0.0")
val buildNumber: Int = (System.getenv("BUILD_NUMBER") ?: providers.gradleProperty("buildNumber").orNull)?.toIntOrNull() ?: 0
val appVersionCode: Int = versionProps.getProperty("versionCodeOffset", "100").toInt() + buildNumber

// ---------- update source ----------
// "owner/repo" whose GitHub Releases the in-app updater reads. Actions sets GITHUB_REPOSITORY itself.
val updateRepo: String = System.getenv("GITHUB_REPOSITORY") ?: providers.gradleProperty("updateRepo").orNull ?: "CHANGE_ME/FamilySafeTube"

// ---------- release signing ----------
// CI: KEYSTORE_FILE / KEYSTORE_PASSWORD / KEY_ALIAS / KEY_PASSWORD (decoded from repository secrets).
// Local: keystore.properties (git-ignored). Without either, release builds are debug-signed (not updatable).
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun signingValue(env: String, prop: String): String? = System.getenv(env)?.takeIf { it.isNotBlank() } ?: keystoreProps.getProperty(prop)
val releaseStoreFile: File? = signingValue("KEYSTORE_FILE", "storeFile")?.let { rootProject.file(it) }?.takeIf { it.exists() }
val hasReleaseKeystore = releaseStoreFile != null

android {
    namespace = "com.example.familysafetube"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.familysafetube"
        minSdk = 26
        targetSdk = 34   // stays at 34 until the players are reviewed for edge-to-edge behaviour changes
        versionCode = appVersionCode
        versionName = appVersionName
        buildConfigField("String", "UPDATE_REPO", "\"$updateRepo\"")
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = signingValue("KEYSTORE_PASSWORD", "storePassword")
                keyAlias = signingValue("KEY_ALIAS", "keyAlias")
                keyPassword = signingValue("KEY_PASSWORD", "keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // R8 shrinks/optimises libraries (our own code is kept, see proguard-rules.pro).
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (hasReleaseKeystore) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
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

    packaging {
        resources {
            excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/DEPENDENCIES")
        }
    }

    testOptions {
        // JVM unit tests call code that logs through android.util.Log; return defaults instead of throwing.
        unitTests.isReturnDefaultValues = true
    }

    lint {
        // CI should not fail on style hints; real errors still fail the build.
        abortOnError = false
        checkReleaseBuilds = false
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    // Core / UI
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.ktx)          // by viewModels()
    implementation(libs.androidx.lifecycle.runtime)     // lifecycleScope, repeatOnLifecycle
    implementation(libs.androidx.lifecycle.viewmodel)   // ViewModel, AndroidViewModel, viewModelScope
    implementation(libs.androidx.viewpager2)            // vertical swiping pager (player screens)

    // Jetpack Compose
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Data & background work
    implementation(libs.androidx.datastore.preferences) // settings, PIN hash, per-day usage
    implementation(libs.androidx.work.runtime)          // WorkManager (includes Kotlin/coroutine APIs)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)                         // Drive sync and the in-app updater
    implementation(libs.material)                       // needed by the Google sign-in screens (their theme extends Material Components)
    implementation(libs.play.services.auth)             // Google sign-in for Drive (no API key in the app)

    // Media
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.glide)

    testImplementation(libs.junit)
}
