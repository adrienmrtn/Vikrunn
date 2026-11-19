plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.atala.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.atala.app"
        minSdk = 26
        targetSdk = 35
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
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("com.google.maps.android:maps-compose:6.2.1")
    implementation("com.revenuecat.purchases:purchases:8.10.6")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
