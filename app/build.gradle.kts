plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
}

android {
    namespace = "com.codexlocal"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.codexlocal"
        minSdk = 33
        targetSdk = 36
        versionCode = 4
        versionName = "1.4"

        vectorDrawables {
            useSupportLibrary = true
        }

        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file("codexlocal.keystore")
            storePassword = "codexlocal"
            keyAlias = "codexlocal"
            keyPassword = "codexlocal"
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("release")
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

    kotlin {
        jvmToolchain(17)
        compileOptions {
            targetCompatibility = JavaVersion.VERSION_17
        }
    }

    buildFeatures {
        viewBinding = true
    }

    packaging {
        // obligatoire : les backends GGML sont charges dynamiquement depuis nativeLibraryDir
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.documentfile)
    implementation(libs.material)
    implementation(libs.kotlinx.coroutines.android)

    implementation(project(":lib"))
}
