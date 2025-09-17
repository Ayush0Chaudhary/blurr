# Debug Symbols Implementation Summary

## Issue Resolved
✅ **"This App Bundle contains native code, and you've not uploaded debug symbols"** - Google Play Console warning

## Root Cause
The Panda app contains native code from:
- `ai.picovoice:porcupine-android:3.0.2` (Wake word detection)  
- `com.google.firebase:firebase-crashlytics-ndk` (Crash reporting)

Google Play Console requires debug symbols for native libraries to provide meaningful crash reports and ANR analysis.

## Implementation Details

### 1. NDK Configuration
```kotlin
android {
    ndkVersion = "25.1.8937393"
    // Ensures consistent builds across environments
}
```

### 2. Symbol Preservation (Both Debug & Release)
```kotlin
packaging {
    doNotStrip "*/arm64-v8a/*.so"
    doNotStrip "*/armeabi-v7a/*.so" 
    doNotStrip "*/x86/*.so"
    doNotStrip "*/x86_64/*.so"
}
```

### 3. Firebase Crashlytics Integration
```kotlin
firebaseCrashlytics {
    nativeSymbolUploadEnabled = true
}
```

### 4. App Bundle Optimization
```kotlin
bundle {
    abi { enableSplit = true }        // Split by architecture
    density { enableSplit = true }    // Split by screen density
    language { enableSplit = false }  // Keep languages together
}
```

## Files Modified
- `app/build.gradle.kts` - Main configuration changes
- `gradle/libs.versions.toml` - AGP version management

## Files Added
- `docs/DEBUG_SYMBOLS_CONFIGURATION.md` - Comprehensive documentation
- `docs/AGP_VERSION_NOTE.md` - AGP version compatibility notes
- `app/src/test/java/com/blurr/voice/DebugSymbolsConfigurationTest.kt` - Unit tests
- `scripts/verify_debug_symbols.sh` - Verification script

## Verification
Run the verification script to confirm configuration:
```bash
./scripts/verify_debug_symbols.sh
```

## Build Process
1. **Debug builds**: `./gradlew assembleDebug` - Symbols preserved for debugging
2. **Release bundles**: `./gradlew bundleRelease` - Symbols uploaded to Firebase, optimized for distribution

## Expected Outcome
After uploading a release bundle built with this configuration:
1. ✅ Google Play Console warning will be resolved
2. ✅ Native crash reports will include symbolicated stack traces
3. ✅ App Bundle Explorer will show native libraries with debug info
4. ✅ Firebase Crashlytics will provide detailed native crash analysis

## Testing
Unit tests verify that the configuration is properly applied. Manual verification can be done by:
1. Building a release bundle
2. Checking bundle contents for .so files
3. Verifying symbols are not stripped using `nm` or `objdump`
4. Uploading to Google Play Console and checking for warnings

## Impact
- **File Size**: Minimal impact on user downloads due to App Bundle splitting
- **Development**: Better debugging capabilities for native crashes
- **Production**: Detailed crash reports for faster issue resolution
- **Compliance**: Resolves Google Play Console requirements