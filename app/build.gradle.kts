plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Never commit the private keystore. Configure GitHub Actions secrets for signing.
val signingStore = System.getenv("OALAB_KEYSTORE_PATH")
val signingPassword = System.getenv("OALAB_KEYSTORE_PASSWORD")
val signingAlias = System.getenv("OALAB_KEY_ALIAS")
val signingKeyPassword = System.getenv("OALAB_KEY_PASSWORD")
val stableSigningEnabled = listOf(
    signingStore, signingPassword, signingAlias, signingKeyPassword
).all { !it.isNullOrBlank() }

android {
    namespace = "ru.lisurgut.oalab"
    compileSdk = 36

    defaultConfig {
        applicationId = "ru.lisurgut.oalab"
        minSdk = 26
        targetSdk = 35
        versionCode = 12
        versionName = "0.5.3"
    }
    if (stableSigningEnabled) {
        signingConfigs {
            create("stableRelease") {
                storeFile = file(signingStore!!)
                storePassword = signingPassword
                keyAlias = signingAlias
                keyPassword = signingKeyPassword
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            if (stableSigningEnabled) signingConfig = signingConfigs.getByName("stableRelease")
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
    implementation(platform("androidx.compose:compose-bom:2026.06.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.media3:media3-session:1.11.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.11.1")
    implementation("androidx.media3:media3-exoplayer-dash:1.11.1")
    implementation("androidx.media3:media3-ui:1.11.1")
    testImplementation("junit:junit:4.13.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
