# Debug Symbols Configuration for Android App Bundle

## Overview

This document explains the debug symbols configuration implemented to resolve the Google Play Console warning:
"This App Bundle contains native code, and you've not uploaded debug symbols."

## Problem

The Panda app uses native code through several dependencies:
- `ai.picovoice:porcupine-android:3.0.2` - Wake word detection with native ARM libraries
- `com.google.firebase:firebase-crashlytics-ndk` - NDK crash reporting

When uploading an Android App Bundle to Google Play Console, debug symbols are required for proper crash analysis and debugging of native code crashes.

## Solution

### 1. NDK Version Configuration

Added explicit NDK version to ensure consistent builds:
```kotlin
android {
    ndkVersion = "25.1.8937393"
    // ...
}
```

### 2. Debug Symbol Preservation

Configured both debug and release builds to preserve native symbols:

```kotlin
buildTypes {
    release {
        firebaseCrashlytics {
            nativeSymbolUploadEnabled = true
        }
        
        packaging {
            doNotStrip "*/arm64-v8a/*.so"
            doNotStrip "*/armeabi-v7a/*.so"
            doNotStrip "*/x86/*.so"
            doNotStrip "*/x86_64/*.so"
        }
    }
    debug {
        firebaseCrashlytics {
            nativeSymbolUploadEnabled = true
        }
        
        packaging {
            doNotStrip "*/arm64-v8a/*.so"
            doNotStrip "*/armeabi-v7a/*.so"
            doNotStrip "*/x86/*.so"
            doNotStrip "*/x86_64/*.so"
        }
    }
}
```

### 3. App Bundle Configuration

Added bundle configuration for optimal native library handling:

```kotlin
bundle {
    abi {
        enableSplit = true  // Split by ABI to reduce download size
    }
    density {
        enableSplit = true  // Split by screen density
    }
    language {
        enableSplit = false // Keep all languages together
    }
}
```

## What This Solves

1. **Native Symbol Upload**: Firebase Crashlytics will automatically upload debug symbols for native crashes
2. **Symbol Preservation**: Native libraries (.so files) will retain their debug information
3. **Google Play Compatibility**: App Bundles will include proper debug symbols for crash reporting
4. **Multi-Architecture Support**: Symbols preserved for all supported ABIs (ARM64, ARMv7, x86, x86_64)

## Build Commands

### Debug Build (with symbols)
```bash
./gradlew assembleDebug
```

### Release Bundle (with symbols for upload)
```bash
./gradlew bundleRelease
```

### Verify Symbols
To verify symbols are included in your build:
```bash
# Extract APK/Bundle and check for .so files
unzip -l app/build/outputs/bundle/release/app-release.aab | grep "\.so"

# Verify symbols are not stripped
nm app/build/intermediates/merged_native_libs/release/out/lib/arm64-v8a/libporcupine.so | head -20
```

## Firebase Integration

The Firebase Crashlytics NDK component will automatically:
1. Upload debug symbols during build
2. Symbolicate native crash reports
3. Provide detailed stack traces for native crashes

## File Size Impact

- **Debug Builds**: Larger due to unstripped symbols (development only)
- **Release Bundles**: Google Play automatically optimizes downloads per device
- **Crash Reporting**: Better debugging capabilities outweigh minor size increase

## Verification

After uploading to Google Play Console:
1. Go to **Release Management > App Bundle Explorer**
2. Check that native libraries are listed with debug info
3. **Play Console > Quality > Android vitals > Crashes** should show detailed native crash reports

## Troubleshooting

### Symbols Still Missing
- Ensure `nativeSymbolUploadEnabled = true` in both build types
- Verify Firebase project is properly configured
- Check that `google-services.json` is up to date

### Build Size Concerns
- App Bundles automatically split by ABI - users only download their device's architecture
- Debug symbols are uploaded to Firebase but not included in user downloads
- Use `./gradlew bundleRelease` instead of `assembleRelease` for production

### Native Library Issues
If you see missing native libraries:
```bash
# Check what native libraries are included
./gradlew app:dependencies --configuration releaseRuntimeClasspath | grep "\.aar"
```

## Dependencies with Native Code

Current dependencies that include native libraries:
- **Picovoice Porcupine**: Wake word detection engine
- **Firebase Crashlytics NDK**: Native crash reporting
- **Room Database**: SQLite native components (if using)

All of these will now have proper debug symbol handling.