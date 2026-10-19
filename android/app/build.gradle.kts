import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.guardpay.device"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.guardpay.device"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        // Reachable backend base URL. Emulator loopback is 10.0.2.2;
        // physical devices need the dev machine's LAN IP (see README).
        val apiBaseUrl =
            project.findProperty("guardpayApiBaseUrl") as String?
                ?: "http://10.0.2.2:8080"
        // Development pairing token (build-time only, never hard-coded).
        // Pass with -PguardpayDeviceToken=... ; empty means backend 401s.
        val deviceToken = project.findProperty("guardpayDeviceToken") as String? ?: ""
        buildConfigField("String", "GUARDPAY_API_BASE_URL", "\"$apiBaseUrl\"")
        buildConfigField("String", "GUARDPAY_DEVICE_TOKEN", "\"$deviceToken\"")
    }

    buildFeatures {
        buildConfig = true
    }
    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// Match the Java 17 target above: without this, Kotlin floats to the
// toolchain JDK (22 in this environment) and Gradle fails the build on
// inconsistent JVM-target compatibility.
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    testImplementation("junit:junit:4.13.2")
}
