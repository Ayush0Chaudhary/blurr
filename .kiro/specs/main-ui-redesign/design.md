# Design Document

## Overview

The main UI redesign transforms the Panda app's interface into a cleaner, more focused experience centered around a large delta (triangle) symbol that serves as the primary visual indicator of the app's state. The design removes clutter while maintaining essential functionality and adds a Pro upgrade banner for better monetization visibility.

## Architecture

### Current Structure Analysis
- **Base Navigation**: `activity_base_navigation.xml` provides the bottom navigation framework
- **Main Content**: `activity_main.xml` and `activity_main_content.xml` contain the current UI elements
- **Navigation**: Bottom navigation with 5 tabs (Triggers, Moments, Home/Triangle, Upgrade, Settings)

### New Structure
The redesign will modify the main content area while preserving the navigation framework and essential functionality.

## Components and Interfaces

### 1. Pro Upgrade Banner
**Location**: Top of the main screen
**Design**:
- Horizontal banner with subtle background (using existing `panel_background` color)
- Left-aligned text: "Get more with Panda Pro"
- Right-aligned "Upgrade" button with accent styling
- Height: 56dp for comfortable touch target
- Margins: 16dp horizontal, 8dp vertical

### 2. Central Delta Symbol
**Location**: Center of the main screen
**Design**:
- Large triangle outline (120dp x 120dp)
- Stroke width: 3dp
- State-based colors:
  - Idle: `#FFFFFF` (white)
  - Listening: `#FF9800` (orange/amber)
  - Processing: `#2196F3` (blue)
  - Error: `#F44336` (red)
- Positioned using center gravity in parent container
- Replaces current wave animation

### 3. Status Text
**Location**: Below the delta symbol
**Design**:
- Text like "Listening...", "Ready", "Processing"
- Color: `#FFFFFF`
- Size: 16sp
- Centered horizontally
- 24dp margin from delta symbol

### 4. Essential UI Elements (Preserved)
**Bottom Section**:
- "How to wake up Panda" link - positioned at bottom with existing styling
- Permission buttons - maintain current visibility logic
- Developer email link - keep existing position and styling
- Free tasks remaining text - maintain current styling and position

### 5. Removed Elements
- "Hey Panda here" text (`karan_textview_gradient`)
- "Assistant at your command" text (`subtitle_textview`)
- "Start Conversation" button (`startConversationButton`)
- "Setup Wake Word" button (`saveKeyButton`)

## Data Models

### State Management Integration
The delta symbol will integrate with the existing `ConversationalAgentService` and `SpeechCoordinator` to reflect real-time app status:

```kotlin
enum class PandaState {
    IDLE,           // App ready, no active conversation
    LISTENING,      // STT active, listening for user input
    PROCESSING,     // LLM processing user request
    SPEAKING,       // TTS active, Panda is speaking
    THINKING,       // Showing thinking indicator
    ERROR           // Error state (STT errors, service issues)
}

data class DeltaState(
    val state: PandaState,
    val color: Int,
    val statusText: String
)
```

### Service Integration Points
- **ConversationalAgentService.isRunning**: Determines if service is active
- **SpeechCoordinator.isListening**: Indicates STT listening state
- **SpeechCoordinator.isSpeaking**: Indicates TTS speaking state
- **VisualFeedbackManager.showThinkingIndicator()**: Processing state
- **STT/TTS error callbacks**: Error state triggers

### Color Mapping
```kotlin
object StateColors {
    const val IDLE = 0xFFFFFFFF        // White - ready state
    const val LISTENING = 0xFFFF9800   // Orange/Amber - listening
    const val PROCESSING = 0xFF2196F3  // Blue - thinking/processing
    const val SPEAKING = 0xFF4CAF50    // Green - speaking
    const val ERROR = 0xFFF44336       // Red - error state
}
```

## Layout Structure

### New Main Layout Hierarchy
```
RelativeLayout (root)
├── LinearLayout (pro_banner)
│   ├── TextView (banner_text)
│   └── Button (upgrade_button)
├── LinearLayout (center_content)
│   ├── ImageView (delta_symbol)
│   └── TextView (status_text)
├── ScrollView (bottom_content)
│   ├── TextView (wake_word_help_link)
│   ├── TextView (tasks_remaining_textview)
│   └── TextView (developer_email_link)
└── LinearLayout (permission_section)
    ├── TextView (permission_status)
    └── Button (manage_permissions)
```

## Error Handling

### State Transition Errors
- Invalid state transitions should default to IDLE state
- Color application failures should fall back to white (#FFFFFF)
- Missing status text should display "Ready"

### UI Rendering Errors
- Delta symbol rendering failures should show a fallback circle
- Banner visibility issues should gracefully hide the banner
- Permission section errors should maintain current error handling

## Testing Strategy

### Unit Tests
NO TEST

## Implementation Considerations

### Service Integration
- **State Monitoring**: Create a `PandaStateManager` that observes service states
- **Callback Integration**: Hook into existing STT/TTS callbacks for real-time updates
- **Service Lifecycle**: Handle service start/stop events for state transitions
- **Error Handling**: Monitor service errors and reflect in delta color

### Performance
- Use vector drawables for delta symbol to ensure crisp rendering
- Implement smooth color transitions using ValueAnimator (300ms duration)
- Minimize layout redraws during state changes
- Cache color values to avoid repeated calculations

### State Transition Logic
```kotlin
class PandaStateManager {
    fun updateState() {
        val newState = when {
            !ConversationalAgentService.isRunning -> PandaState.IDLE
            speechCoordinator.isSpeaking -> PandaState.SPEAKING
            speechCoordinator.isListening -> PandaState.LISTENING
            visualFeedbackManager.isThinkingIndicatorVisible -> PandaState.PROCESSING
            hasRecentError -> PandaState.ERROR
            else -> PandaState.IDLE
        }
        updateDeltaColor(newState)
    }
}
```

### Accessibility
- Add proper content descriptions for delta symbol states
- Ensure color changes are accompanied by text changes
- Maintain existing accessibility features for preserved elements
- Add haptic feedback for state changes

### Backward Compatibility
- Preserve existing navigation behavior
- Maintain current permission handling logic
- Keep existing settings and preferences intact
- Ensure existing service callbacks continue to work

## Visual Design Specifications

### Typography
- Banner text: 16sp, medium weight
- Status text: 16sp, regular weight
- Preserved elements: maintain current specifications

### Spacing
- Banner margins: 16dp horizontal, 8dp top
- Delta symbol: centered with 32dp margins
- Status text: 24dp below delta symbol
- Bottom elements: maintain current spacing

### Colors
- Background: existing `background_color` (#101010)
- Banner background: existing `panel_background` (#1C1C1E)
- Text colors: maintain existing white (#FFFFFF) and accent colors