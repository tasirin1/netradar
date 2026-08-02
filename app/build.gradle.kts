plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.tasirin.network.radar"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tasirin.network.radar"
        minSdk = 21
        targetSdk = 35
        versionCode = 3
        versionName = "2.0"
    }

    signingConfigs {
        create("release") {
            storeFile = file(System.getenv("KEYSTORE_PATH") ?: "scanner.keystore")
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "scanner123"
            keyAlias = System.getenv("KEY_ALIAS") ?: "scanner"
            keyPassword = System.getenv("KEY_PASSWORD") ?: "scanner123"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.4"
    }
}

dependencies {
    // Core
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.activity:activity-compose:1.8.2")

    // Compose
    // 2024.06.00 = Compose UI 1.6.8 + Material3 1.2.1 (berisi banyak perbaikan bug
    // LazyColumn/SubcomposeLayout yang menyebabkan crash "pending composition has
    // not been applied" saat item beranimasi/diperbarui selama deep scan).
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.foundation:foundation")

    // Lifecycle + ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    debugImplementation("androidx.compose.ui:ui-tooling")

    // Unit test (logika murni JVM)
    testImplementation("junit:junit:4.13.2")
}
