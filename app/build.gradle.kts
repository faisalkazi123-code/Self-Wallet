plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

// CI passes -PversionCode=<run number> so every published APK can update the previous one.
val ciVersionCode = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: 1

android {
    namespace = "com.selfwallet.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.selfwallet.app"
        minSdk = 26
        targetSdk = 36
        versionCode = ciVersionCode
        versionName = "1.0.$ciVersionCode"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        // A real release key is used when the RELEASE_* environment variables are set
        // (see README). Otherwise the bundled, public "community" key is used so that
        // APKs from this repository can always upgrade each other.
        create("release") {
            val ksPath = System.getenv("RELEASE_KEYSTORE_PATH")
            if (!ksPath.isNullOrBlank() && file(ksPath).exists()) {
                storeFile = file(ksPath)
                storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
            } else {
                storeFile = file("community.keystore")
                storePassword = "selfwallet"
                keyAlias = "selfwallet"
                keyPassword = "selfwallet"
            }
            // Sign with every scheme so older or third-party package installers accept the APK too.
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
        }
        // Google Play upload key: private, only ever used for the Play bundle (see docs/google-play).
        create("play") {
            val ksPath = System.getenv("PLAY_KEYSTORE_PATH")
            if (!ksPath.isNullOrBlank() && file(ksPath).exists()) {
                storeFile = file(ksPath)
                storePassword = System.getenv("PLAY_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("PLAY_KEY_ALIAS")
                keyPassword = System.getenv("PLAY_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
        // ./gradlew bundlePlay -> Play-ready .aab signed with the private upload key.
        create("play") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("play")
            matchingFallbacks += "release"
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
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
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.animation:animation")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.fragment:fragment-ktx:1.8.5")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
}
