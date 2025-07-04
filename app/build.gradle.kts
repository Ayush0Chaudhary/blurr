import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(FileInputStream(localPropertiesFile))
}

android {
    namespace = "com.example.blurr"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.blurr"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Get the API keys string from the properties
// Get the API keys string from the properties
            val apiKeys = localProperties.getProperty("GEMINI_API_KEYS") ?: ""
            val tavilyApiKeys = localProperties.getProperty("TAVILY_API") ?: ""

            // This line CREATES the variable. Make sure it's here and not commented out.
            buildConfigField("String", "GEMINI_API_KEYS", "\"$apiKeys\"")
            buildConfigField("String", "TAVILY_API", "\"$tavilyApiKeys\"")
            val mem0ApiKey = localProperties.getProperty("MEM0_API") ?: ""
            buildConfigField("String", "MEM0_API", "\"$mem0ApiKey\"")

        }
        debug {
            // Also add it to the 'debug' block so it works when you run from Android Studio
            val apiKeys = localProperties.getProperty("GEMINI_API_KEYS") ?: ""
            val tavilyApiKeys = localProperties.getProperty("TAVILY_API") ?: ""
            // This line must ALSO be here.
            buildConfigField("String", "TAVILY_API", "\"$tavilyApiKeys\"")
            buildConfigField("String", "GEMINI_API_KEYS", "\"$apiKeys\"")
            val mem0ApiKey = localProperties.getProperty("MEM0_API") ?: ""
            buildConfigField("String", "MEM0_API", "\"$mem0ApiKey\"")
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

//    // Core shell API
//    implementation("com.github.topjohnwu.libsu:core:$libsuVersion")
//
//    // Optional: Root service support
//    implementation("com.github.topjohnwu.libsu:service:$libsuVersion")
//
//    // Optional: Remote file system support
//    implementation("com.github.topjohnwu.libsu:nio:$libsuVersion")
//
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("com.squareup.okhttp3:okhttp:5.0.0-alpha.16")
    implementation("com.squareup.moshi:moshi:1.15.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    // https://mvnrepository.com/artifact/androidx.test.uiautomator/uiautomator
    implementation("androidx.test.uiautomator:uiautomator:2.3.0")

}