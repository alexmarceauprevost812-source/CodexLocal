plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
}

// Sur GitHub Actions, chaque compilation a un numero plus grand que la precedente :
// chaque nouvelle version s'installe par-dessus l'ancienne.
val ciRun: Int? = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()

android {
    namespace = "ca.tilabo.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "ca.tilabo.app"
        minSdk = 33
        targetSdk = 36
        versionCode = if (ciRun != null) 100 + ciRun else 2
        versionName = if (ciRun != null) "2.0.$ciRun" else "2.0"

        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    signingConfigs {
        create("tilabo") {
            storeFile = file("tilabo.keystore")
            storePassword = "tilabo"
            keyAlias = "tilabo"
            keyPassword = "tilabo"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            if (file("tilabo.keystore").exists()) {
                signingConfig = signingConfigs.getByName("tilabo")
            } else {
                signingConfig = signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        jvmToolchain(17)
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
    implementation(libs.kotlinx.coroutines.android)
    implementation(project(":lib"))
}
