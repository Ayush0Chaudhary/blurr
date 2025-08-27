# TTS Debug Mode Feature

## Overview

The TTS (Text-to-Speech) Debug Mode feature allows you to control when TTS output is active. This is useful for debugging purposes where you want to hear TTS feedback during development but disable it in production or when you don't need it.

## How It Works

The TTSManager automatically detects if the app is running in debug mode using `BuildConfig.DEBUG`:

- **Debug Builds**: TTS is enabled by default
- **Release Builds**: TTS is disabled by default

## Behavior

### When Debug Mode is Enabled (Default in Debug Builds)
- All `speakText()` calls will actually speak the text
- Console logs show: `"TTS: Speaking 'your text here'"`
- TTS initialization log shows: `"TTS Singleton is ready with Audio Session ID: X (Debug Mode: true)"`

### When Debug Mode is Disabled (Default in Release Builds)
- All `speakText()` calls are silently ignored
- Console logs show: `"TTS: Skipping speech in release mode - 'your text here'"`
- TTS initialization log shows: `"TTS Singleton is ready with Audio Session ID: X (Debug Mode: false)"`

## Usage

### Automatic Mode (Recommended)
The TTSManager automatically uses the build configuration:

```kotlin
// This will speak only in debug builds
ttsManager.speakText("Hello World")
```

### User-Facing Messages (Always Spoken)
For important messages that should always be spoken to the user:

```kotlin
// This will always speak regardless of debug mode
ttsManager.speakToUser("Task completed successfully")
```

### Manual Override
You can manually control the debug mode at runtime:

```kotlin
val ttsManager = TTSManager.getInstance(context)

// Disable TTS even in debug builds
ttsManager.setDebugMode(false)

// Enable TTS even in release builds
ttsManager.setDebugMode(true)

// Check current status
val isEnabled = ttsManager.isDebugModeEnabled()
```

## Implementation Details

### TTSManager Changes
- Added `isDebugMode` property that reads from `BuildConfig.SPEAK_INSTRUCTIONS`
- Modified `speakText()` method to check debug mode before speaking
- Added `speakToUser()` method that always speaks regardless of debug mode
- Added `setDebugMode()` method for manual override
- Added `isDebugModeEnabled()` method to check current status
- Enhanced logging to show debug mode status

### Fallback Behavior
If `BuildConfig.DEBUG` is not available (e.g., during development), the system defaults to `true` (enabled) for safety.

## Benefits

1. **Development**: Full TTS feedback during debugging
2. **Production**: No unwanted TTS output in release builds
3. **Flexibility**: Can be overridden at runtime if needed
4. **Performance**: Avoids unnecessary TTS processing in release mode
5. **User Experience**: No unexpected speech in production apps

## Example Scenarios

### Scenario 1: Development Debugging
```kotlin
// In debug builds, this will speak
ttsManager.speakText("Agent started to perform task...")
// Output: "TTS: Speaking 'Agent started to perform task...'"
```

### Scenario 2: Production Release
```kotlin
// In release builds, this will be ignored
ttsManager.speakText("Agent started to perform task...")
// Output: "TTS: Skipping speech in release mode - 'Agent started to perform task...'"
```

### Scenario 3: Manual Control
```kotlin
// Force disable TTS
ttsManager.setDebugMode(false)
ttsManager.speakText("This won't speak")
// Output: "TTS: Skipping speech in release mode - 'This won't speak'"

// Force enable TTS
ttsManager.setDebugMode(true)
ttsManager.speakText("This will speak")
// Output: "TTS: Speaking 'This will speak'"
```

### Scenario 4: User-Facing Messages
```kotlin
// This always speaks regardless of debug mode
ttsManager.speakToUser("Your task has been completed")
// Output: "TTS: Speaking to user - 'Your task has been completed'"

// Even with debug mode disabled
ttsManager.setDebugMode(false)
ttsManager.speakToUser("Important message for user")
// Output: "TTS: Speaking to user - 'Important message for user'"
```

## Migration

No code changes are required for existing TTS calls. The feature is backward compatible:

- Existing `speakText()` calls will automatically respect the debug mode
- No need to modify any existing code
- All TTS calls will work as before in debug builds
- All TTS calls will be silently ignored in release builds

## Troubleshooting

- **TTS not speaking in debug builds**: Check if `BuildConfig.DEBUG` is properly set
- **TTS speaking in release builds**: Verify the build configuration
- **Manual override not working**: Ensure you're calling `setDebugMode()` before `speakText()` 