import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("kotlin-kapt")
    id("com.google.dagger.hilt.android")
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
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            val apiKeys = localProperties.getProperty("GEMINI_API_KEYS") ?: ""
            val tavilyApiKeys = localProperties.getProperty("TAVILY_API") ?: ""
            buildConfigField("String", "GEMINI_API_KEYS", "\"$apiKeys\"")
            buildConfigField("String", "TAVILY_API", "\"$tavilyApiKeys\"")
            val mem0ApiKey = localProperties.getProperty("MEM0_API") ?: ""
            buildConfigField("String", "MEM0_API", "\"$mem0ApiKey\"")
            val picovoiceApiKey = localProperties.getProperty("PICOVOICE_ACCESS_KEY") ?: ""
            buildConfigField("String", "PICOVOICE_ACCESS_KEY", "\"$picovoiceApiKey\"")
            buildConfigField("boolean", "ENABLE_DIRECT_APP_OPENING", "true")
            buildConfigField("boolean", "SPEAK_INSTRUCTIONS", "true")
        }
        debug {
            val apiKeys = localProperties.getProperty("GEMINI_API_KEYS") ?: ""
            val tavilyApiKeys = localProperties.getProperty("TAVILY_API") ?: ""
            buildConfigField("String", "TAVILY_API", "\"$tavilyApiKeys\"")
            buildConfigField("String", "GEMINI_API_KEYS", "\"$apiKeys\"")
            val mem0ApiKey = localProperties.getProperty("MEM0_API") ?: ""
            buildConfigField("String", "MEM0_API", "\"$mem0ApiKey\"")
            val picovoiceApiKey = localProperties.getProperty("PICOVOICE_ACCESS_KEY") ?: ""
            buildConfigField("String", "PICOVOICE_ACCESS_KEY", "\"$picovoiceApiKey\"")
            buildConfigField("boolean", "ENABLE_DIRECT_APP_OPENING", "true")
            buildConfigField("boolean", "SPEAK_INSTRUCTIONS", "true")
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
    composeOptions {
        // This line is crucial. It links the Compose Compiler to the Kotlin version.
        kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get()
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

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
    implementation(libs.material)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.moshi)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.uiautomator)
    implementation(libs.porcupine.android)

    // Hilt dependencies using the version catalog
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

kapt {
    correctErrorTypes = true
}
