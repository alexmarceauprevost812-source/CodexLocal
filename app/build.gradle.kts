plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
}

// Version automatique : sur GitHub Actions, chaque compilation a un numero
// (GITHUB_RUN_NUMBER) toujours plus grand que le precedent, donc chaque mise a
// jour suit la precedente sans modifier ce fichier. En local : version 1.5 / code 5.
val ciRun: Int? = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()

android {
    namespace = "com.codexlocal"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.codexlocal"
        minSdk = 33
        targetSdk = 36
        versionCode = if (ciRun != null) 100 + ciRun else 5
        versionName = if (ciRun != null) "1.5.$ciRun" else "1.5"

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
            // Reuse the existing installation key when supplied by the owner.
            if (file("codexlocal.keystore").exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
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

    testOptions {
        unitTests.isIncludeAndroidResources = true
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

    testImplementation(libs.junit)
    testImplementation("org.robolectric:robolectric:4.16.1")
}
