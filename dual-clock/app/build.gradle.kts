import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// ---- Release identity ----------------------------------------------------------------------
// Android installs an update over an existing app only if it has the same signing certificate
// and a versionCode no lower than the installed one. Both come from the environment, so local
// and PR builds need no secrets:
//   RELEASE_TAG   "v1.2.3" -> versionName "1.2.3", versionCode 10203 (set by the release workflow)
//   VERSION_CODE  explicit override (the CI upgrade test builds old=1, new=2)
//   SIGNING_*     the release keystore; without it, assembleRelease produces an unsigned APK

val releaseTag: String? = providers.environmentVariable("RELEASE_TAG").orNull
val tagVersion: Triple<Int, Int, Int>? = releaseTag?.let { tag ->
    val m = Regex("""^v(\d+)\.(\d+)\.(\d+)$""").matchEntire(tag)
        ?: error("RELEASE_TAG must look like v1.2.3, got '$tag'")
    val (major, minor, patch) = m.destructured
    require(minor.toInt() < 100 && patch.toInt() < 100) { "minor and patch must be below 100: $tag" }
    Triple(major.toInt(), minor.toInt(), patch.toInt())
}
val appVersionCode: Int = providers.environmentVariable("VERSION_CODE").orNull?.toInt()
    ?: tagVersion?.let { (major, minor, patch) -> major * 10_000 + minor * 100 + patch }
    ?: 1
val appVersionName: String = tagVersion?.let { (major, minor, patch) -> "$major.$minor.$patch" } ?: "0.0.0-dev"
val signingKeystore: String? = providers.environmentVariable("SIGNING_KEYSTORE_PATH").orNull

android {
    namespace = "com.dg.dualclock"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.dg.dualclock"
        minSdk = 26 // native java.time and font resources in RemoteViews
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (signingKeystore != null) {
            create("release") {
                storeFile = file(signingKeystore)
                storePassword = providers.environmentVariable("SIGNING_STORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("SIGNING_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("SIGNING_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release") // null: unsigned output
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true // BuildConfig.DEBUG gates the "simulate time" control (SPEC A3)
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.glance.appwidget)
    implementation(libs.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    debugImplementation(libs.compose.ui.tooling)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
