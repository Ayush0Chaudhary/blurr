# STT User Input Implementation

## Overview

The `UserInputManager` class has been updated to use Speech-to-Text (STT) for handling user responses to agent questions. This implementation provides a more natural interaction method for users to communicate with the AI agent.

## Features

### 1. Speech Recognition Integration
- Uses Android's built-in `SpeechRecognizer` API
- Integrates with the existing `STTManager` class
- Supports real-time speech recognition with timeout handling
- **Multiple Attempts**: Up to 3 speech recognition attempts if no speech is detected

### 2. Robust Error Handling
- Checks if speech recognition is available on the device
- Provides fallback responses when all STT attempts fail
- Handles network errors, timeouts, and recognition failures gracefully
- Re-asks the question between attempts for better user experience

### 3. Timeout Management
- **Speech Timeout**: 30 seconds per attempt for speech input
- **Fallback Timeout**: 5 seconds for fallback response
- **Attempt Delay**: 2-second delay between attempts
- Automatic cleanup of resources

## How It Works

### 1. Question Flow
```kotlin
// Agent asks a question
val userResponse = userInputManager.askQuestion("What is your name?")
```

### 2. Speech Recognition Process
1. **Availability Check**: Verifies if speech recognition is available
2. **Multiple Attempts**: Up to 3 attempts for speech recognition
3. **Start Listening**: Activates microphone and starts recognition
4. **Timeout Handling**: Waits up to 30 seconds per attempt for user speech
5. **Re-ask Question**: Re-asks the question between attempts if needed
6. **Result Processing**: Processes recognized text or handles errors
7. **Fallback**: Uses fallback response if all STT attempts fail

### 3. Response Handling
- **Success**: Returns the recognized speech text
- **Failure**: Returns a fallback response
- **Error**: Logs errors and provides graceful degradation

## Usage in Agent Actions

The `Ask` atomic action in the `Operator` class uses this implementation:

```kotlin
"ask" -> {
    val question = args["question"]?.toString()?.trim()
    if (question != null) {
        // Speak the question to the user
        ttsManager.speakToUser(question)
        
        // Get user response using STT
        val userInputManager = UserInputManager(context)
        val userResponse = userInputManager.askQuestion(question)
        
        // Update the instruction with the user's response
        val updatedInstruction = "${infoPool.instruction}\n\n[Agent asked: $question]\n[User responded: $userResponse]"
        infoPool.instruction = updatedInstruction
    }
}
```

## Permissions Required

The following permissions are already included in `AndroidManifest.xml`:
- `RECORD_AUDIO`: For microphone access
- `RECOGNIZE_SPEECH`: For speech recognition functionality

## Error Scenarios and Handling

### 1. Speech Recognition Not Available
- **Cause**: Device doesn't support speech recognition
- **Handling**: Uses fallback response immediately
- **User Experience**: Seamless degradation

### 2. Network Errors
- **Cause**: No internet connection or server issues
- **Handling**: Falls back to simulated response
- **User Experience**: Continues operation

### 3. Timeout
- **Cause**: User doesn't speak within 30 seconds across all 3 attempts
- **Handling**: Uses fallback response after all attempts fail
- **User Experience**: Multiple chances to provide input, no hanging or freezing

### 4. Recognition Errors
- **Cause**: Poor audio quality, background noise, etc.
- **Handling**: Attempts recognition, falls back if needed
- **User Experience**: Graceful error handling

## Configuration

### Timeout Settings
```kotlin
private const val SPEECH_TIMEOUT_MS = 30000L // 30 seconds per attempt
private const val FALLBACK_TIMEOUT_MS = 5000L // 5 seconds
private const val MAX_SPEECH_ATTEMPTS = 3 // Maximum number of attempts
```

### Language Settings
Currently uses the device's default locale. Can be customized in `STTManager` if needed.

## Testing

To test the implementation:

1. **Normal Flow**: Ask a question and speak a response
2. **Multiple Attempts Test**: Ask a question, remain silent for first attempt, then speak on second attempt
3. **Timeout Test**: Ask a question and remain silent for all 3 attempts
4. **Error Test**: Test on devices without speech recognition
5. **Network Test**: Test with poor network conditions

## Future Enhancements

1. **Multiple Language Support**: Add language selection options
2. **Voice Activity Detection**: Improve speech detection accuracy
3. **Custom Wake Words**: Integrate with existing wake word detection
4. **Offline Recognition**: Add offline speech recognition capabilities
5. **Response Validation**: Add validation for user responses

## Integration with Existing Code

The implementation is designed to work seamlessly with:
- **TTSManager**: For speaking questions to users
- **AgentTaskService**: For running agent tasks
- **Operator**: For executing atomic actions
- **InfoPool**: For updating agent context with user responses

## Debugging

Enable debug logging to monitor the speech recognition process:
```kotlin
Log.d(TAG, "Speech recognized: $recognizedText")
Log.e(TAG, "Speech recognition error: $errorMessage")
Log.w(TAG, "Speech recognition failed or timed out, using fallback")
``` 