# Speech Coordinator Implementation

## Overview

The `SpeechCoordinator` class provides centralized coordination between Text-to-Speech (TTS) and Speech-to-Text (STT) operations to prevent them from running simultaneously. This ensures a better user experience by avoiding audio conflicts and providing a smooth, natural interaction flow.

## Problem Solved

### Before SpeechCoordinator
- TTS and STT could run simultaneously
- Users experienced audio conflicts and poor interaction quality
- No coordination between speech operations
- Potential for feedback loops and audio interference

### After SpeechCoordinator
- TTS and STT never run simultaneously
- Smooth, coordinated speech interactions
- Better user experience with clear turn-taking
- Proper resource management and cleanup

## Architecture

### Design Pattern
- **Singleton Pattern**: Ensures single instance across the app
- **Coordinator Pattern**: Manages interaction between multiple components
- **Mutex-based Synchronization**: Thread-safe coordination using Kotlin coroutines

### Key Components
1. **TTSManager Integration**: Uses existing TTSManager singleton
2. **STTManager Integration**: Creates and manages STTManager instances
3. **State Tracking**: Monitors speaking and listening states
4. **Mutex Locking**: Ensures exclusive access to speech operations

## Features

### 1. Coordinated Speech Operations
- **TTS Priority**: When TTS is speaking, STT waits for completion
- **STT Priority**: When STT is listening, TTS stops and waits
- **Automatic Coordination**: No manual coordination required

### 2. Smart Timing
- **TTS Duration Estimation**: Estimates speaking duration based on text length
- **Graceful Delays**: Appropriate pauses between operations
- **Timeout Handling**: Prevents infinite waiting

### 3. State Management
- **Real-time State Tracking**: Monitors current speech operations
- **State Queries**: Check if speaking, listening, or any speech active
- **Completion Waiting**: Wait for speech operations to complete

### 4. Error Handling
- **Graceful Degradation**: Handles failures without crashing
- **Resource Cleanup**: Proper cleanup on errors
- **Logging**: Comprehensive logging for debugging

## API Reference

### Core Methods

#### `speakText(text: String, forceSpeak: Boolean = false)`
Speaks text using TTS, ensuring STT is not listening.
```kotlin
// Normal speaking
speechCoordinator.speakText("Hello, how can I help you?")

// Force speaking (stops any ongoing listening)
speechCoordinator.speakText("Important message", forceSpeak = true)
```

#### `speakToUser(text: String, forceSpeak: Boolean = false)`
Speaks text to user (always spoken regardless of debug mode).
```kotlin
// Always speak to user
speechCoordinator.speakToUser("Please respond to my question")
```

#### `startListening(onResult, onError, onListeningStateChange, waitForTTS = true)`
Starts STT listening, ensuring TTS is not speaking.
```kotlin
speechCoordinator.startListening(
    onResult = { text -> println("Recognized: $text") },
    onError = { error -> println("Error: $error") },
    onListeningStateChange = { listening -> println("Listening: $listening") }
)
```

### Utility Methods

#### `stopListening()`
Stops any ongoing STT listening.
```kotlin
speechCoordinator.stopListening()
```

#### `isCurrentlySpeaking(): Boolean`
Checks if TTS is currently speaking.
```kotlin
if (speechCoordinator.isCurrentlySpeaking()) {
    println("TTS is active")
}
```

#### `isCurrentlyListening(): Boolean`
Checks if STT is currently listening.
```kotlin
if (speechCoordinator.isCurrentlyListening()) {
    println("STT is active")
}
```

#### `isSpeechActive(): Boolean`
Checks if any speech operation is in progress.
```kotlin
if (speechCoordinator.isSpeechActive()) {
    println("Speech operation in progress")
}
```

#### `waitForSpeechCompletion()`
Waits for any ongoing speech operations to complete.
```kotlin
speechCoordinator.waitForSpeechCompletion()
```

## Usage Examples

### 1. Basic Question-Answer Flow
```kotlin
// Ask a question
speechCoordinator.speakToUser("What is your name?")

// Listen for response
speechCoordinator.startListening(
    onResult = { name -> 
        println("User said: $name")
        speechCoordinator.speakToUser("Nice to meet you, $name")
    },
    onError = { error -> 
        println("Error: $error")
    },
    onListeningStateChange = { listening -> 
        println("Listening state: $listening")
    }
)
```

### 2. Agent Task Service Integration
```kotlin
// In AgentTaskService
val speechCoordinator = SpeechCoordinator.getInstance(this)

// Speak subgoal
speechCoordinator.speakText("Current subgoal: $subgoal")

// Later, when asking user questions
speechCoordinator.speakToUser("Please confirm this action")
```

### 3. User Input Manager Integration
```kotlin
// In UserInputManager
private val speechCoordinator = SpeechCoordinator.getInstance(context)

// Re-ask question between attempts
speechCoordinator.speakToUser("Please try again. $question")

// Start listening for response
speechCoordinator.startListening(
    onResult = { response -> /* handle response */ },
    onError = { error -> /* handle error */ },
    onListeningStateChange = { listening -> /* update UI */ }
)
```

## Integration Points

### 1. UserInputManager
- **Before**: Direct STTManager usage
- **After**: SpeechCoordinator for all speech operations
- **Benefits**: Automatic coordination, no conflicts

### 2. Operator (Agent Actions)
- **Before**: Direct TTSManager usage for "Speak" and "Ask" actions
- **After**: SpeechCoordinator for coordinated speech
- **Benefits**: Smooth agent-user interactions

### 3. AgentTaskService
- **Before**: Direct TTSManager usage for status updates
- **After**: SpeechCoordinator for all TTS operations
- **Benefits**: No interference with user input

### 4. WakeWordDetector
- **Note**: WakeWordDetector still uses STTManager directly
- **Reason**: Wake word detection needs to run continuously
- **Future**: Could be integrated with SpeechCoordinator for better coordination

## Configuration

### Timing Settings
```kotlin
// In SpeechCoordinator
private const val TTS_DELAY_AFTER_STT = 500L // ms
private const val STT_DELAY_AFTER_TTS = 500L // ms
private const val TTS_CHECK_INTERVAL = 100L // ms
```

### Duration Estimation
```kotlin
// Rough estimate: 100ms per character
val estimatedDuration = text.length * 100L
val minDuration = 1000L // Minimum 1 second
```

## Benefits

### 1. User Experience
- **No Audio Conflicts**: TTS and STT never overlap
- **Natural Flow**: Smooth turn-taking between system and user
- **Clear Communication**: Users always know when to speak

### 2. System Reliability
- **Thread Safety**: Mutex-based synchronization
- **Resource Management**: Proper cleanup and state tracking
- **Error Resilience**: Graceful handling of failures

### 3. Developer Experience
- **Simple API**: Easy to use coordination methods
- **Automatic Management**: No manual coordination required
- **Comprehensive Logging**: Easy debugging and monitoring

### 4. Performance
- **Efficient Coordination**: Minimal overhead for coordination
- **Smart Timing**: Appropriate delays without excessive waiting
- **Resource Optimization**: Proper cleanup prevents resource leaks

## Testing

### Test Scenarios
1. **TTS → STT Flow**: Verify TTS completes before STT starts
2. **STT → TTS Flow**: Verify STT stops before TTS starts
3. **Concurrent Requests**: Verify proper queuing and coordination
4. **Error Handling**: Verify graceful degradation on failures
5. **State Management**: Verify accurate state tracking

### Test Commands
```kotlin
// Test basic coordination
speechCoordinator.speakToUser("Test message")
speechCoordinator.startListening(/* callbacks */)

// Test state queries
assert(!speechCoordinator.isCurrentlySpeaking())
assert(speechCoordinator.isCurrentlyListening())

// Test completion waiting
speechCoordinator.waitForSpeechCompletion()
assert(!speechCoordinator.isSpeechActive())
```

## Future Enhancements

### 1. Advanced Coordination
- **Priority System**: Different priorities for different speech operations
- **Queue Management**: Queue multiple speech operations
- **Context Awareness**: Remember conversation context

### 2. Performance Optimizations
- **Better Duration Estimation**: More accurate TTS duration prediction
- **Adaptive Delays**: Dynamic delays based on device performance
- **Background Processing**: Non-blocking speech operations

### 3. Integration Improvements
- **Wake Word Integration**: Coordinate with wake word detection
- **Audio Session Management**: Better audio session handling
- **Multi-language Support**: Coordinate different language TTS/STT

### 4. Monitoring and Analytics
- **Usage Metrics**: Track speech operation patterns
- **Performance Monitoring**: Monitor coordination efficiency
- **User Feedback**: Collect user experience data 