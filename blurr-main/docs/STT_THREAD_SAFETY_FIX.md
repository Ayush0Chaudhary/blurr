# STT Thread Safety Fix

## Problem
The `STTManager` was crashing with the error:
```
SpeechRecognizer should be used only from the application's main thread
```

This occurred because `SpeechRecognizer.createSpeechRecognizer()` was being called from a background thread (IO dispatcher) in the `AgentTaskService`.

## Root Cause
- `UserInputManager` was being instantiated from `AgentTaskService.runAgentLogic()` which runs on `Dispatchers.IO`
- `UserInputManager` constructor creates an `STTManager` instance
- `STTManager` constructor immediately calls `initializeSpeechRecognizer()` which calls `SpeechRecognizer.createSpeechRecognizer()`
- `SpeechRecognizer` APIs must be called from the main thread

## Solution
Modified `STTManager` to use lazy initialization on the main thread:

### Changes Made:

1. **Removed constructor initialization**: The `SpeechRecognizer` is no longer created in the constructor
2. **Added lazy initialization**: `initializeSpeechRecognizer()` is now called only when `startListening()` is invoked
3. **Main thread enforcement**: All `SpeechRecognizer` operations are now performed on the main thread using `CoroutineScope(Dispatchers.Main)`
4. **Error handling**: Added proper exception handling for initialization failures
5. **State tracking**: Added `isInitialized` flag to prevent multiple initialization attempts

### Key Changes in STTManager.kt:

```kotlin
// Before: Immediate initialization in constructor
init {
    initializeSpeechRecognizer()
}

// After: Lazy initialization on main thread
fun startListening(...) {
    CoroutineScope(Dispatchers.Main).launch {
        initializeSpeechRecognizer()
        // ... rest of listening logic
    }
}
```

## Benefits
- **Thread safety**: All `SpeechRecognizer` operations now happen on the main thread
- **Lazy loading**: Resources are only allocated when actually needed
- **Error resilience**: Better error handling for initialization failures
- **Backward compatibility**: Existing code continues to work without changes

## Testing
The fix ensures that:
1. `UserInputManager` can be created from any thread
2. `STTManager` initialization happens safely on the main thread
3. Speech recognition works properly in the agent system
4. No crashes occur when using the "Ask" atomic action

## Usage
No changes required in calling code. The `UserInputManager` and `STTManager` APIs remain the same, but now work correctly from any thread. 