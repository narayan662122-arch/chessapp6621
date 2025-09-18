plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.chessbottap"
    compileSdk = 29  // Changed from 34 to 29 for Android 10 compatibility

    defaultConfig {
        applicationId = "com.example.chessbottap"
        minSdk = 24
        targetSdk = 29  // Changed from 34 to 29 for Android 10
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    // Updated dependencies for Android 10 compatibility
    implementation("androidx.core:core-ktx:1.6.0")  // Downgraded for compatibility
    implementation("androidx.appcompat:appcompat:1.3.1")  // Downgraded for compatibility
    implementation("com.google.android.material:material:1.4.0")  // Downgraded for compatibility
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    
    // HTTP client for network requests
    implementation("com.squareup.okhttp3:okhttp:4.9.3")  // Compatible version for Android 10
    
    // Add kotlinx-coroutines-android for proper Android coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.5.2")
    
    // Testing dependencies
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.3")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.4.0")
}