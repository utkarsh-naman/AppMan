plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.appman.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.appman.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.4"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file("../appman-release.jks")
            storePassword = "YOUR_KEYSTORE_PASSWORD"
            keyAlias = "appman"
            keyPassword = "YOUR_KEY_PASSWORD"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    lint {
        checkReleaseBuilds = false
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    implementation(platform("androidx.compose:compose-bom:2025.01.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("com.github.MuntashirAkon:libadb-android:3.1.1") {
        exclude(
            group = "org.bouncycastle",
            module = "bcprov-jdk15to18"
        )
    }

    implementation("org.bouncycastle:bcprov-jdk18on:1.81.1")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.81.1")

    // Required by libadb for TLS keying-material export during ADB pairing.
    // Without it, libadb falls back to the platform's hidden copy, which is
    // blocked on Android 14+ (targetSdk 35).
    implementation("org.conscrypt:conscrypt-android:2.5.3")
}