# STT Multiple Attempts Feature

## Overview
Enhanced the `UserInputManager` to provide up to 3 speech recognition attempts when no speech is detected, improving user experience and reducing failed interactions.

## Problem
Previously, if speech recognition failed or no speech was detected, the system would immediately fall back to a simulated response. This could be frustrating for users who might need a second chance to provide their input.

## Solution
Implemented a retry mechanism that gives users multiple opportunities to provide speech input.

## Key Changes

### 1. UserInputManager.kt Updates

#### New Constants
```kotlin
private const val MAX_SPEECH_ATTEMPTS = 3 // Maximum number of speech recognition attempts
```

#### Enhanced Speech Recognition Logic
- **Multiple Attempts**: Up to 3 attempts for speech recognition
- **Re-asking**: The system re-asks the question between attempts
- **User Feedback**: Provides clear feedback about attempt progress
- **Graceful Degradation**: Falls back to simulated response only after all attempts fail

#### Attempt Flow
1. **First Attempt**: Initial speech recognition with 30-second timeout
2. **Subsequent Attempts**: 
   - 2-second delay for user preparation
   - Re-asks the question via TTS: "Please try again. [Original Question]"
   - 1-second pause after re-asking
   - Another 30-second speech recognition attempt
3. **Final Fallback**: If all 3 attempts fail, uses fallback response

### 2. Enhanced Logging
- Tracks attempt number in all log messages
- Provides detailed feedback about which attempt succeeded or failed
- Clear indication when all attempts are exhausted

### 3. User Experience Improvements
- **Clear Communication**: Users know they have multiple chances
- **Reduced Frustration**: No immediate failure on first attempt
- **Better Accessibility**: Accommodates users who may need time to respond
- **Natural Interaction**: Mimics human conversation patterns

## Configuration

### Timeout Settings
```kotlin
private const val SPEECH_TIMEOUT_MS = 30000L // 30 seconds per attempt
private const val FALLBACK_TIMEOUT_MS = 5000L // 5 seconds for fallback
private const val MAX_SPEECH_ATTEMPTS = 3 // Maximum attempts
```

### Timing
- **Per Attempt**: 30 seconds to speak
- **Between Attempts**: 2 seconds preparation time
- **Re-ask Delay**: 1 second after re-asking question
- **Total Maximum Time**: ~95 seconds (3 × 30s + 2 × 2s + 2 × 1s + 5s fallback)

## Usage Examples

### Successful First Attempt
```
Agent: "What is your name?"
User: "John" (speaks immediately)
Result: "John" (recognized on first attempt)
```

### Successful Second Attempt
```
Agent: "What is your name?"
User: (remains silent for 30s)
Agent: "Please try again. What is your name?"
User: "John" (speaks on second attempt)
Result: "John" (recognized on second attempt)
```

### All Attempts Failed
```
Agent: "What is your name?"
User: (remains silent for all 3 attempts)
Result: "User provided fallback response after 3 failed speech recognition attempts for: What is your name?"
```

## Benefits

1. **Improved Success Rate**: Multiple attempts increase the likelihood of successful speech recognition
2. **Better User Experience**: Users feel more supported and less frustrated
3. **Accessibility**: Accommodates users with speech difficulties or environmental challenges
4. **Robustness**: Handles temporary issues like background noise or network problems
5. **Natural Interaction**: More closely mimics human conversation patterns

## Testing Scenarios

1. **Immediate Response**: User speaks on first attempt
2. **Delayed Response**: User speaks on second or third attempt
3. **No Response**: User remains silent for all attempts
4. **Partial Recognition**: Some attempts partially recognize speech
5. **Network Issues**: Intermittent connectivity problems

## Integration

The feature integrates seamlessly with existing code:
- **Operator.kt**: No changes needed, uses existing `askQuestion()` method
- **AgentTaskService**: No changes needed, works with existing agent flow
- **TTSManager**: Uses existing `speakToUser()` method for re-asking
- **STTManager**: No changes needed, handles individual recognition attempts

## Future Enhancements

1. **Configurable Attempts**: Make number of attempts configurable per question
2. **Adaptive Timeouts**: Adjust timeout based on question complexity
3. **Voice Activity Detection**: Better detection of when user is about to speak
4. **Context Awareness**: Remember previous successful patterns for similar questions
5. **User Preferences**: Allow users to set their preferred number of attempts 