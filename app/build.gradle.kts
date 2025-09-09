plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)

    // Apply Google Services plugin in the app module
    id("com.google.gms.google-services")
    id("com.google.devtools.ksp")     // ← add
    id("androidx.room") version "2.6.1"   // ← add this
}

repositories {
    google()
    maven { url = uri("https://maven.google.com") }
    maven { url = uri("https://dl.google.com/dl/android/maven2/") }
    mavenCentral()
}


android {
    namespace = "com.example.agriscan"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.agriscan"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.15"
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

room {
    // This tells Room where to write the schema JSON
    schemaDirectory("$projectDir/schemas")
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.foundation.android)
    implementation(libs.androidx.foundation.android)
    implementation(libs.androidx.foundation.android)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    // Navigation & Lifecycle
    implementation("androidx.navigation:navigation-compose:2.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.2")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")

    // --- Firebase setup ---
    // Import the Firebase BoM (use the version Firebase console suggested)
    implementation(platform("com.google.firebase:firebase-bom:33.5.1"))

    // Add the Firebase products you need (no versions when using the BoM)
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-analytics-ktx")

    // For phone auth (reCAPTCHA / Play Integrity challenge during verification)
    implementation("com.google.android.gms:play-services-recaptcha:17.1.0")

    implementation("com.google.android.material:material:1.12.0")

    implementation("androidx.compose.foundation:foundation")

    // CameraX
    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4") // gives PreviewView + LifecycleCameraController
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    // Image loading
    implementation("io.coil-kt:coil-compose:2.7.0")

    implementation("com.google.firebase:firebase-storage-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")

    ksp("androidx.room:room-compiler:2.6.1")

    implementation("androidx.datastore:datastore-preferences:1.1.1")

}