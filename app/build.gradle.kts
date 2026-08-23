plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.tvbrowser"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.tvbrowser"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(libs.leanback)
    implementation(libs.leanback.preference)
    implementation(libs.appcompat)
    implementation(libs.webkit)
    implementation(libs.tvprovider)
    implementation(libs.lifecycle.runtime)
    implementation(libs.coroutines.android)
}
