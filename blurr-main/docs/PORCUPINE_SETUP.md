# Porcupine Wake Word Setup

This app now supports two wake word detection engines:

1. **STT Engine (Default)**: Uses Android's Speech-to-Text to listen for "Panda"
2. **Porcupine Engine**: Uses Picovoice's Porcupine library to listen for "Panda" (custom wake word)

## Setting up Porcupine Wake Word Detection

### 1. Automatic Key Management

The app now automatically fetches and manages Picovoice access keys from a secure API endpoint. The key is:
- Fetched automatically when needed
- Cached locally for 24 hours
- Only fetched when not present in local storage
- Handled transparently by the app

**No manual configuration required!** The app will automatically:
1. Fetch the access key from the secure API when Porcupine is first used
2. Cache the key locally for future use
3. Refresh the key automatically when it expires (24 hours)
4. Fall back to STT-based detection if the key cannot be obtained

### 2. Using the Wake Word Feature

1. Open the app
2. Select your preferred wake word engine:
   - **STT Engine**: Listens for "Panda" (works offline)
   - **Porcupine Engine**: Listens for "Panda" (more accurate, requires internet)
3. Tap "ENABLE WAKE WORD" to start listening
4. Say the wake word to activate the app

### 3. Custom Wake Words

The app is already configured to use a custom "Panda" wake word. The `Panda_en_android_v3_0_0.ppn` file is included in the assets folder.

To use different custom wake words:

1. Create a custom wake word in the [Picovoice Console](https://console.picovoice.ai/)
2. Download the `.ppn` file
3. Place it in `app/src/main/assets/`
4. Update the `PorcupineWakeWordDetector.kt` file to use the custom keyword path

Example:
```kotlin
porcupineManager = PorcupineManager.Builder()
    .setAccessKey(accessKey)
    .setKeywordPaths(arrayOf("your_custom_wake_word.ppn"))
    .build(context, wakeWordCallback)
```

### 4. Troubleshooting

- **Wake word not detected**: Check your microphone permissions
- **Service not starting**: Ensure you have the required permissions (RECORD_AUDIO, INTERNET)
- **Porcupine not working**: The app will automatically fall back to STT-based detection

#### App Crashes When Starting Wake Word Service

If the app crashes when you try to enable the wake word service:

1. **Check Permissions:**
   - Ensure microphone permission is granted
   - Check that internet permission is available

2. **Check Logs:**
   - Look for error messages containing "Porcupine" or "PicovoiceKeyManager"
   - The app will automatically fall back to STT-based detection if Porcupine fails

3. **Fallback Behavior:**
   - If Porcupine fails to initialize, the app will automatically use STT-based wake word detection
   - This ensures the wake word feature still works even if there are issues with the key fetching

4. **Manual Fallback:**
   - If you prefer to use STT-based detection, select "STT Engine" in the UI
   - This will use the built-in Android speech recognition for wake word detection

### 5. Built-in Wake Words

Porcupine supports several built-in wake words. You can change the wake word by modifying the `PorcupineWakeWordDetector.kt` file:

```kotlin
// Available built-in keywords:
// - Porcupine.BuiltInKeyword.PORCUPINE
// - Porcupine.BuiltInKeyword.BUMBLEBEE
// - Porcupine.BuiltInKeyword.PICOVOICE
// - And many more...

.setKeywords(arrayOf(Porcupine.BuiltInKeyword.BUMBLEBEE))
```

## Security Note

The Picovoice access key is now managed automatically by the app and is fetched from a secure API endpoint. The key is cached locally for performance but is not stored in version control or exposed in the app's configuration files. 