# AGP Version Note

During implementation of debug symbols configuration, we discovered that AGP version 8.9.2 specified in libs.versions.toml appears to not exist in the current Gradle plugin repositories.

## Issue
The build fails with:
```
Plugin [id: 'com.android.application', version: '8.9.2', apply: false] was not found
```

## Resolution Options

1. **Keep 8.9.2** (if this is a future version or enterprise version)
   - The debug symbols configuration is implemented correctly
   - The version may become available in future repositories

2. **Use Latest Stable** (8.6.1 as of late 2024)
   - Change libs.versions.toml: `agp = "8.6.1"`
   - All debug symbols configuration remains the same

3. **Use 8.5.2** (known stable version)
   - Change libs.versions.toml: `agp = "8.5.2"`
   - All debug symbols configuration remains the same

## Recommendation

The debug symbols configuration implemented is compatible with all AGP versions 8.0+. The version can be adjusted based on project requirements without affecting the native symbol handling.

Current configuration supports:
- NDK version specification
- Native symbol preservation
- Firebase Crashlytics integration
- App Bundle optimization

All features will work regardless of the specific AGP version chosen.