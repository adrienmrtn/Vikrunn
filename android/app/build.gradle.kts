plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("io.github.takahirom.roborazzi")
}

android {
    namespace = "com.atala.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.atala.app"
        minSdk = 26
        targetSdk = 36
        versionCode = (System.getenv("ATALA_VERSION_CODE") ?: "1").toInt()
        versionName = "0.1.0"
        // Google Maps key for the run summary map: Gradle property or environment variable
        // ATALA_MAPS_API_KEY. Without it the summary draws the route on a plain background.
        val mapsKey = (project.findProperty("ATALA_MAPS_API_KEY") as String?) ?: System.getenv("ATALA_MAPS_API_KEY") ?: ""
        manifestPlaceholders["MAPS_API_KEY"] = mapsKey
        buildConfigField("boolean", "HAS_MAPS_KEY", if (mapsKey.isBlank()) "false" else "true")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Signed in CI with the upload key; a local release build stays unsigned.
            val keystore = System.getenv("ATALA_KEYSTORE_PATH")
            if (keystore != null) {
                signingConfig = signingConfigs.create("upload") {
                    storeFile = file(keystore)
                    storePassword = System.getenv("ATALA_KEYSTORE_PASSWORD")
                    keyAlias = System.getenv("ATALA_KEY_ALIAS")
                    keyPassword = System.getenv("ATALA_KEY_PASSWORD")
                }
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
    testOptions {
        unitTests {
            // Robolectric reads the merged resources and assets (translations, drawables, voice clips).
            isIncludeAndroidResources = true
            all {
                it.maxHeapSize = "3g"
            }
        }
    }
    sourceSets {
        // The translations, coach keywords and run voice clips are shared with the iPhone app.
        getByName("main").assets.srcDirs("src/main/assets", "../../Atala/Resources/Strings", "../../Atala/Resources/CoachWords")
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.browser:browser:1.8.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("com.google.maps.android:maps-compose:6.2.1")
    implementation("com.revenuecat.purchases:purchases:9.29.1")
    implementation("com.google.android.play:review-ktx:2.0.2")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // JVM tests: logic with Robolectric, Compose UI tests and Roborazzi screenshots (no device needed).
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("org.robolectric:robolectric:4.18.1")
    testImplementation("androidx.test:core-ktx:1.6.1")
    testImplementation(composeBom)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.60.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.60.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-junit-rule:1.60.0")
}
