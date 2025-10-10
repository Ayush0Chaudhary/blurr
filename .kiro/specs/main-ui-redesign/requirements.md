# Requirements Document

## Introduction

This feature redesigns the main interface of the Panda app to create a cleaner, more focused user experience. The redesign centers around a large delta (triangle) symbol that serves as the primary visual indicator of the app's state, while streamlining the UI by removing unnecessary elements and adding a Pro upgrade banner.

## Requirements

### Requirement 1

**User Story:** As a user, I want to see a large delta symbol in the center of the main screen that changes color based on the app's current state, so that I can quickly understand what the app is doing. Also dar a small delta symbol with black circle bg at the bottom that mimics the large delta. this should be drawn over other apps so that it is visible when the app closes. 

#### Acceptance Criteria

1. WHEN the app is in listening state THEN the delta symbol SHALL be displayed in orange/amber color
2. WHEN the app is in idle state THEN the delta symbol SHALL be displayed in a white color
3. WHEN the app is processing THEN the delta symbol SHALL be displayed in a green color
3. WHEN the app is speaking THEN the delta symbol SHALL be displayed in a blue color
5. The delta symbol SHALL be large and prominently positioned in the center of the screen
6. The delta symbol SHALL replace the current wave animation at the bottom
7. Right side of the delta symbol should be thicker than the other sides, like in a delta symbol


### Requirement 2

**User Story:** As a user, I want to see a Pro upgrade banner at the top of the screen, so that I can easily access premium features.

#### Acceptance Criteria

1. WHEN the user is on the main screen THEN a Pro upgrade banner SHALL be displayed at the top
2. The banner SHALL contain text like "Get more with Panda Pro" and an "Upgrade" button
3. WHEN the user taps the upgrade button THEN the app SHALL navigate to the Pro upgrade screen
4. The banner SHALL be visually distinct but not intrusive to the main interface
5. If the user is Pro, dont show this banner. 

### Requirement 3

**User Story:** As a user, I want to see only essential UI elements on the main screen, so that the interface is clean and focused.

#### Acceptance Criteria

1. The "Setup Wake Word" button SHALL be removed from the main interface
2. The "Start Conversation" button SHALL be removed from the main interface
3. Text elements like "Hey Panda here" and "Assistant at your command" SHALL be removed
4. The "How to wake up Panda" text SHALL remain at the bottom of the screen
5. Permission buttons SHALL remain visible following current visibility logic
6. The developer button SHALL be removed and moved to bottom of the setting btn
7. The "Free tasks left" text SHALL only visible when 3 tasks left, otherwise dont show

### Requirement 4

**User Story:** As a user, I want the bottom navigation to remain functional with the new design, so that I can still access all app sections.

#### Acceptance Criteria

1. WHEN viewing the main screen THEN the bottom navigation SHALL remain visible
2. The bottom navigation SHALL contain: Triggers, Moments, Delta (center), Upgrade, Settings
3. The center navigation item SHALL be updated to show a delta symbol instead of current icon
4. WHEN the user taps navigation items THEN the app SHALL navigate to respective screens
5. The navigation SHALL maintain current functionality and styling

### Requirement 5

**User Story:** As a user, I want the state text (like "Listening...") to be positioned appropriately with the new delta design, so that I understand the current app status.

#### Acceptance Criteria

1. WHEN the app displays status text THEN it SHALL be positioned below the delta symbol
2. The status text SHALL be clearly readable against the background
3. The status text SHALL update appropriately based on app state
4. The text positioning SHALL not interfere with the delta symbol visibility