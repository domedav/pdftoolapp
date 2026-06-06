plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.domedav.pdftool"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.domedav.pdftool"
        minSdk = 24
        targetSdk = 36
        versionCode = (project.findProperty("versionCode") as? String)?.toInt() ?: 1
        versionName = (project.findProperty("versionName") as? String) ?: "1.0"
        
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
    
    kotlin {
        jvmToolchain(21)
    }

    buildFeatures {
        compose = true
        buildConfig = true
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
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    
    // Splash Screen (Android 12+)
    implementation(libs.androidx.core.splashscreen)

    // Morph animációkhoz (Success képernyő)
    implementation(libs.androidx.graphics.shapes)

    // Képbetöltés (nélkülözhetetlen)
    implementation(libs.coil.compose)
    
    // Drag & Drop Grid (Ezt érdemes megtartani, mert kézzel megírni 100+ sor lenne)
    implementation(libs.reorderable)

    // --- OPCIONÁLIS ---
    // Ha találsz helyettesítő ikonokat a 'Core' csomagban (pl. Icons.Default.*),
    // akkor ezt a sort törölheted a build idő drasztikus csökkentéséért:
    implementation(libs.androidx.material.icons.extended)

    // DEBUG (Csak fejlesztés közben kell)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}