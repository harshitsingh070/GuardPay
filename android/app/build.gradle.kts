import java.util.Properties
import org.gradle.api.tasks.Sync
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
        // Values come from -P flags or android/local.properties (gitignored).
        val localProperties =
            Properties().apply {
                rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use {
                    load(it)
                }
            }
        fun propertyOrDefault(name: String, default: String): String =
            (project.findProperty(name) as String?) ?: localProperties.getProperty(name) ?: default

        val apiBaseUrl = propertyOrDefault("guardpayApiBaseUrl", "http://10.0.2.2:8080")
        buildConfigField("String", "GUARDPAY_API_BASE_URL", "\"$apiBaseUrl\"")
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

// Bundle the production React build into the APK. Run `npm run build` in
// frontend/ first (or use scripts/build-android-apk.ps1, which does both).
val syncWebAssets by tasks.registering(Sync::class) {
    from(rootProject.file("../frontend/dist"))
    into(layout.projectDirectory.dir("src/main/assets"))
}

tasks.named("preBuild") {
    dependsOn(syncWebAssets)
}
