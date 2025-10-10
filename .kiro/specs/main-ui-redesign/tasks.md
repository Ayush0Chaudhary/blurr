# Implementation Plan

- [ ] 1. Create state management system for delta symbol
  - Create `PandaState` enum with IDLE, LISTENING, PROCESSING, SPEAKING, ERROR states
  - Create `PandaStateManager` class to monitor ConversationalAgentService status
  - Implement state transition logic based on service callbacks
  - _Requirements: 1.1, 1.2, 1.3, 1.4_

- [x] 2. Design and implement delta symbol drawable
  - Create vector drawable for triangle outline with configurable stroke color
  - Implement smooth color transition animations using ValueAnimator
  - Create state-to-color mapping utility class
  - _Requirements: 1.5, 1.6_

- [x] 3. Create Pro upgrade banner component
  - Design banner layout with "Get more with Panda Pro" text and "Upgrade" button
  - Implement banner styling using existing panel_background color
  - Add click handler to navigate to Pro purchase screen
  - _Requirements: 2.1, 2.2, 2.3, 2.4_

- [x] 4. Modify main activity layout structure
  - Remove "Hey Panda here" and "Assistant at your command" text elements
  - Remove "Start Conversation" and "Setup Wake Word" buttons
  - Add Pro banner at top of layout
  - Add centered delta symbol container
  - _Requirements: 3.1, 3.2, 3.3_

- [x] 5. Preserve essential UI elements
  - Keep "How to wake up Panda" link at bottom with existing styling
  - Maintain permission buttons with current visibility logic
  - Keep developer email link in existing position
  - Preserve "Free tasks left" text display
  - _Requirements: 3.4, 3.5, 3.6, 3.7_

- [x] 6. Integrate delta symbol with service state monitoring
  - Hook into ConversationalAgentService lifecycle callbacks
  - Connect to SpeechCoordinator listening/speaking state changes
  - Monitor VisualFeedbackManager thinking indicator visibility
  - Implement error state detection from STT/TTS failures
  - _Requirements: 1.1, 1.2, 1.3, 1.4_

- [x] 7. Implement status text display below delta symbol
  - Create TextView for status messages like "Listening...", "Ready", "Processing"
  - Position text 24dp below delta symbol with center alignment
  - Update text content based on current PandaState
  - Style text with white color and 16sp size
  - _Requirements: 5.1, 5.2, 5.3, 5.4_

- [x] 8. Update MainActivity to use new layout and state management
  - Modify MainActivity.kt to initialize PandaStateManager
  - Remove click handlers for deleted buttons
  - Add Pro banner click handler for navigation
  - Integrate state monitoring with activity lifecycle
  - _Requirements: 2.3, 3.1, 3.2_

- [x] 9. Update bottom navigation delta icon
  - Replace current home triangle icon with delta symbol
  - Ensure navigation functionality remains intact
  - Update navigation item styling if needed
  - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5_

- [ ] 10. Test state transitions and visual feedback
  - Write unit tests for PandaStateManager state logic
  - Test delta color changes during service state transitions
  - Verify smooth animations and proper status text updates
  - Test error state handling and recovery
  - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5_

- [ ] 11. Test UI layout and preserved functionality
  - Verify Pro banner displays correctly and navigates to purchase screen
  - Test that permission buttons maintain current visibility logic
  - Confirm essential links and text elements remain functional
  - Test responsive layout on different screen sizes
  - _Requirements: 2.1, 2.2, 2.3, 3.4, 3.5, 3.6, 3.7_

- [ ] 12. Integration testing with ConversationalAgentService
  - Test delta symbol updates during actual voice conversations
  - Verify state changes during STT listening and TTS speaking
  - Test error state display during service failures
  - Confirm status text updates match actual service state
  - _Requirements: 1.1, 1.2, 1.3, 1.4, 5.1, 5.2, 5.3_