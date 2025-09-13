import java.util.Properties
import java.io.FileInputStream
import java.io.FileOutputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.gms.google-services")
    alias(libs.plugins.ksp)
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.10"
    id("com.google.firebase.crashlytics")

}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(FileInputStream(localPropertiesFile))
}

// Load version properties
val versionProps = Properties()
val versionPropsFile = rootProject.file("version.properties")
if (versionPropsFile.exists()) {
    versionProps.load(FileInputStream(versionPropsFile))
}

android {
    namespace = "com.blurr.voice"
    compileSdk = 35

    // Common API keys and configuration - extracted to avoid duplication
    val apiKeys = localProperties.getProperty("GEMINI_API_KEYS") ?: ""
    val tavilyApiKeys = localProperties.getProperty("TAVILY_API") ?: ""
    val mem0ApiKey = localProperties.getProperty("MEM0_API") ?: ""
    val picovoiceApiKey = localProperties.getProperty("PICOVOICE_ACCESS_KEY") ?: ""
    val googleTtsApiKey = localProperties.getProperty("GOOGLE_TTS_API_KEY") ?: ""
    val googlecloudGatewayPicovoice = localProperties.getProperty("GCLOUD_GATEWAY_PICOVOICE_KEY") ?: ""
    val googlecloudGatewayURL = localProperties.getProperty("GCLOUD_GATEWAY_URL") ?: ""
    val googlecloudProxyURL = localProperties.getProperty("GCLOUD_PROXY_URL") ?: ""
    val googlecloudProxyURLKey = localProperties.getProperty("GCLOUD_PROXY_URL_KEY") ?: ""
    val revenueCatSDK = localProperties.getProperty("REVENUE_CAT_PUBLIC_URL") ?: ""
    val revenueCatApiKey = localProperties.getProperty("REVENUECAT_API_KEY") ?: ""

    val debugSha1 = "D0:A1:49:03:FD:B5:37:DF:B5:36:51:B1:66:AE:70:11:E2:59:08:33"

    defaultConfig {
        applicationId = "com.blurr.voice"
        minSdk = 24
        targetSdk = 35
        versionCode = versionProps.getProperty("VERSION_CODE", "13").toInt()
        versionName = versionProps.getProperty("VERSION_NAME", "1.0.13")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // Common build config fields - applies to all build types
        buildConfigField("String", "GEMINI_API_KEYS", "\"$apiKeys\"")
        buildConfigField("String", "TAVILY_API", "\"$tavilyApiKeys\"")
        buildConfigField("String", "MEM0_API", "\"$mem0ApiKey\"")
        buildConfigField("String", "PICOVOICE_ACCESS_KEY", "\"$picovoiceApiKey\"")
        buildConfigField("boolean", "ENABLE_DIRECT_APP_OPENING", "true")
        buildConfigField("boolean", "SPEAK_INSTRUCTIONS", "true")
        buildConfigField("String", "GOOGLE_TTS_API_KEY", "\"$googleTtsApiKey\"")
        buildConfigField("String", "GCLOUD_GATEWAY_PICOVOICE_KEY", "\"$googlecloudGatewayPicovoice\"")
        buildConfigField("String", "GCLOUD_GATEWAY_URL", "\"$googlecloudGatewayURL\"")
        buildConfigField("String", "GCLOUD_PROXY_URL", "\"$googlecloudProxyURL\"")
        buildConfigField("String", "GCLOUD_PROXY_URL_KEY", "\"$googlecloudProxyURLKey\"")
        buildConfigField("String", "REVENUE_CAT_PUBLIC_URL", "\"$revenueCatSDK\"")
        buildConfigField("String", "REVENUECAT_API_KEY", "\"$revenueCatApiKey\"")

    }

    buildTypes {
        release {
            firebaseCrashlytics {
                nativeSymbolUploadEnabled = true
            }
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            // Debug-specific field only
            buildConfigField("String", "SHA1_FINGERPRINT", "\"$debugSha1\"")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        viewBinding = true
        buildConfig = true
    }
}
val libsuVersion = "6.0.0"

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.appcompat)
    implementation(libs.generativeai)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    implementation("com.google.android.material:material:1.11.0") // or latest

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("com.squareup.okhttp3:okhttp:5.0.0-alpha.16")
    implementation("com.squareup.moshi:moshi:1.15.0")
    implementation("com.google.code.gson:gson:2.13.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    // https://mvnrepository.com/artifact/androidx.test.uiautomator/uiautomator
    implementation("androidx.test.uiautomator:uiautomator:2.3.0")

    // Porcupine Wake Word Engine
    implementation("ai.picovoice:porcupine-android:3.0.2")

    implementation("com.google.firebase:firebase-analytics")

    // Room database dependencies
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    // Import the Firebase BoM
    implementation(platform(libs.firebase.bom))

    // Add the dependency for the Firebase Authentication library
    implementation(libs.firebase.auth)

    // Add the dependency for the Google Play services library
    implementation(libs.play.services.auth)

    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-crashlytics-ndk")
    implementation(libs.firebase.firestore)
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("com.revenuecat.purchases:purchases:9.7.0")
    implementation("com.revenuecat.purchases:purchases-ui:9.7.0")
}

// Task to increment version for release builds
tasks.register("incrementVersion") {
    doLast {
        val versionFile = rootProject.file("version.properties")
        val props = Properties()
        props.load(FileInputStream(versionFile))
        
        val currentVersionCode = props.getProperty("VERSION_CODE").toInt()
        val currentVersionName = props.getProperty("VERSION_NAME")
        
        // Increment version code
        val newVersionCode = currentVersionCode + 1
        
        // Increment patch version in semantic versioning (x.y.z -> x.y.z+1)
        val versionParts = currentVersionName.split(".")
        val newPatchVersion = if (versionParts.size >= 3) {
            versionParts[2].toInt() + 1
        } else {
            1
        }
        val newVersionName = if (versionParts.size >= 2) {
            "${versionParts[0]}.${versionParts[1]}.$newPatchVersion"
        } else {
            "1.0.$newPatchVersion"
        }
        
        // Update properties
        props.setProperty("VERSION_CODE", newVersionCode.toString())
        props.setProperty("VERSION_NAME", newVersionName)
        
        // Save back to file with comments
        val output = FileOutputStream(versionFile)
        output.use { fileOutput ->
            fileOutput.write("# Version configuration for Blurr Android App\n".toByteArray())
            fileOutput.write("# This file is automatically updated during release builds\n".toByteArray())
            fileOutput.write("# Do not modify manually - use Gradle tasks to update versions\n\n".toByteArray())
            fileOutput.write("# Current version code (integer - increments by 1 each release)\n".toByteArray())
            fileOutput.write("VERSION_CODE=$newVersionCode\n\n".toByteArray())
            fileOutput.write("# Current version name (semantic version - increments patch number each release)\n".toByteArray())
            fileOutput.write("VERSION_NAME=$newVersionName".toByteArray())
        }
        
        println("Version incremented to: versionCode=$newVersionCode, versionName=$newVersionName")
    }
}

// Make release builds automatically increment version
tasks.whenTaskAdded {
    if (name == "assembleRelease" || name == "bundleRelease") {
        dependsOn("incrementVersion")
    }
}