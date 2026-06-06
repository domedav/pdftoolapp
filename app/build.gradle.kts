import java.text.SimpleDateFormat
import java.util.Date

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.domedav.pdftool"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.domedav.pdftool"
        minSdk = 24
        targetSdk = 37
        
        // Auto-incrementing version logic
        val buildNumberFile = rootProject.file(".buildnum")
        var buildNumber = 1 // Default if file not found or empty

        if (buildNumberFile.exists()) {
            try {
                buildNumber = buildNumberFile.readText().trim().toInt()
            } catch (e: Exception) {
                println("Warning: Could not read .buildnum file. Using default build number 1. Error: ${e.message}")
            }
        }

        // Increment build number for this build
        buildNumber++

        // Write back the new build number
        buildNumberFile.writeText(buildNumber.toString())

        versionCode = buildNumber
        val date = Date()
        val dateFormat = SimpleDateFormat("yyyy.MM.dd")
        val formattedDate = dateFormat.format(date)
        versionName = "$formattedDate.$buildNumber"
        
        // A vectorDrawables szükséges a Compose ikonokhoz
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = true
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    
    //noinspection WrongGradleMethod
    kotlin {
        jvmToolchain(21)
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    androidResources {
        ignoreAssetsPattern = "assets/*"
    }
}

dependencies {
    // --- CORE ANDROID & COMPOSE ---
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.material3)
    
    // Splash Screen
    implementation(libs.androidx.core.splashscreen)

    // Morph animációkhoz
    implementation(libs.androidx.graphics.shapes)

    // Képbetöltés
    implementation(libs.coil.compose)
    
    // Drag & Drop Grid
    implementation(libs.reorderable)

    // DEBUG (Csak fejlesztés közben kell)
    debugImplementation(libs.androidx.ui.tooling.preview) // Moved from implementation
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}