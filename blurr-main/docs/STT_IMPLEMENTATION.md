# Speech-to-Text (STT) Implementation

## Overview
This implementation adds voice command functionality to the Blurr app, allowing users to speak their instructions and have them automatically executed. The feature follows a press-and-hold pattern similar to popular voice assistants, with automatic task execution and TTS feedback.

## Features

### Press & Hold Voice Command
- **Press & Hold**: User presses and holds the microphone button
- **Record**: App starts recording audio from the microphone
- **Visual Feedback**: Button changes color and status text updates
- **Speak**: User speaks their instruction
- **Release**: User releases the button
- **Process**: App stops recording and processes the speech
- **Announce**: TTS announces the task being performed
- **Execute**: App automatically executes the task
- **Feedback**: Results are announced via TTS

## Implementation Details

### Files Added/Modified

#### New Files:
1. **`STTManager.kt`** - Core speech recognition manager
2. **`voice_input_button_bg.xml`** - Button background with press states
3. **`ic_mic.xml`** - Microphone icon (updated)

#### Modified Files:
1. **`MainActivity.kt`** - Added voice input integration
2. **`activity_main.xml`** - Added voice input UI components
3. **`strings.xml`** - Added voice input strings
4. **`AndroidManifest.xml`** - Added speech recognition permission

### Key Components

#### STTManager Class
- Handles Android's SpeechRecognizer API
- Manages recording states and callbacks
- Provides error handling and logging
- Supports press-and-hold functionality

#### UI Components
- **Voice Input Button**: Circular microphone button with press states
- **Status Text**: Shows current state ("Hold to Speak" / "Listening...")
- **Visual Feedback**: Button changes color when pressed

#### Permissions
- `RECORD_AUDIO` - Required for microphone access
- `RECOGNIZE_SPEECH` - Required for speech recognition

## Usage Flow

1. **User presses and holds** the microphone button
2. **Button turns red** and status shows "Listening..."
3. **User speaks** their instruction (e.g., "Add milk to my shopping list")
4. **User releases** the button
5. **App processes** the speech and converts to text
6. **TTS announces** the task being performed
7. **App automatically executes** the task without user intervention
8. **Results are announced** via TTS

## Error Handling

The implementation includes comprehensive error handling for:
- Speech recognition not available
- Microphone permission denied
- Network errors
- No speech detected
- Recognition timeouts
- Server errors

## Integration with Existing Features

The voice command seamlessly integrates with existing features:
- **Deep Search**: Voice commands work with the deep search functionality
- **Agent Tasks**: Voice instructions automatically trigger agent tasks
- **Vision Modes**: Voice commands work with both XML and Screenshot modes
- **TTS**: Provides automatic feedback and task announcements
- **Automatic Execution**: No manual button press required after voice input

## Technical Notes

- Uses Android's built-in SpeechRecognizer API
- Supports multiple languages (uses device default)
- Handles partial results for real-time feedback
- Properly manages lifecycle and cleanup
- Thread-safe UI updates using runOnUiThread

## Future Enhancements

Potential improvements could include:
- Real-time speech visualization
- Multiple language support
- Offline speech recognition
- Voice command shortcuts
- Custom wake words 