# Interactive Dialogue System

## Overview
The Interactive Dialogue System enhances the Blurr app by automatically detecting unclear user instructions and asking clarifying questions before executing tasks. This ensures more accurate and effective task execution.

## Features

### Automatic Clarification Detection
- **Instruction Analysis**: AI analyzes user instructions for clarity
- **Smart Question Generation**: Automatically generates relevant clarifying questions
- **Context-Aware**: Considers the type of task and required information

### Interactive Dialogue Interface
- **Modal Interface**: Clean, focused dialogue screen
- **Progress Tracking**: Shows question progress (e.g., "2 of 3")
- **Dual Input Methods**: Both text and voice input support
- **TTS Integration**: Questions are spoken aloud for accessibility

### Seamless Integration
- **Automatic Flow**: No manual intervention required
- **Enhanced Instructions**: Answers are automatically added to the original instruction
- **Error Handling**: Graceful handling of cancellations and errors

## How It Works

### 1. Instruction Analysis
When a user provides an instruction (via voice or text), the system:
1. Analyzes the instruction using the `ClarificationAgent`
2. Determines if additional information is needed
3. Generates specific clarifying questions if required

### 2. Clarification Process
If clarification is needed:
1. **Dialogue Launch**: Opens the `DialogueActivity`
2. **Question Presentation**: Shows questions one by one
3. **User Response**: User can answer via text or voice
4. **Progress Tracking**: Shows current question number
5. **Enhanced Instruction**: Combines original instruction with answers

### 3. Task Execution
After clarification:
1. **Enhanced Instruction**: Original instruction + clarification answers
2. **Automatic Execution**: Task is executed with complete information
3. **TTS Feedback**: Results are announced via text-to-speech

## Example Scenarios

### Scenario 1: Messaging Task
**User Input**: "Message my brother happy birthday"

**System Analysis**: Needs clarification
- Missing: Brother's name in contacts
- Missing: Preferred messaging app

**Clarification Questions**:
1. "What is the name of your brother saved in the phone?"
2. "Which messaging app would you prefer to use?"

**Enhanced Instruction**:
```
Message my brother happy birthday

Additional information:
- What is the name of your brother saved in the phone?: John Smith
- Which messaging app would you prefer to use?: WhatsApp
```

### Scenario 2: Clear Instruction
**User Input**: "Open WhatsApp"

**System Analysis**: Clear instruction
- No clarification needed
- Task executed directly

## Technical Implementation

### Key Components

#### ClarificationAgent
- **Purpose**: Analyzes instructions and generates clarifying questions
- **Input**: User instruction
- **Output**: Status (CLEAR/NEEDS_CLARIFICATION) and questions list

#### DialogueActivity
- **Purpose**: Handles the interactive clarification process
- **Features**: 
  - Question display
  - Voice and text input
  - Progress tracking
  - TTS integration

#### MainActivity Integration
- **Clarification Check**: Automatically checks if clarification is needed
- **Dialogue Launch**: Seamlessly launches dialogue when required
- **Enhanced Execution**: Executes tasks with complete information

### File Structure
```
app/src/main/java/com/example/blurr/
├── agent/
│   └── ClarificationAgent.kt          # Instruction analysis
├── DialogueActivity.kt                # Dialogue interface
├── MainActivity.kt                    # Integration logic
└── res/
    ├── layout/
    │   └── activity_dialogue.xml      # Dialogue UI
    └── values/
        └── strings.xml                # Dialogue strings
```

## User Experience Flow

### Voice Command Flow
1. **User**: Presses and holds microphone button
2. **User**: Speaks instruction
3. **System**: Analyzes instruction
4. **If Clear**: Executes task directly
5. **If Unclear**: 
   - Announces "I need to ask some questions"
   - Opens dialogue interface
   - Asks questions one by one
   - User answers via voice or text
   - Executes enhanced task

### Text Input Flow
1. **User**: Types instruction in input field
2. **User**: Presses "Perform Task" button
3. **System**: Analyzes instruction
4. **If Clear**: Executes task directly
5. **If Unclear**: 
   - Opens dialogue interface
   - Asks questions one by one
   - User answers via voice or text
   - Executes enhanced task

## Benefits

### For Users
- **Better Accuracy**: Tasks are executed with complete information
- **Reduced Errors**: Fewer failed attempts due to unclear instructions
- **Natural Interaction**: Feels like talking to a helpful assistant
- **Accessibility**: Voice input and TTS support

### For System
- **Higher Success Rate**: More tasks completed successfully
- **Better User Satisfaction**: Users get what they intended
- **Reduced Frustration**: Fewer failed task attempts
- **Learning Capability**: Can improve over time with more examples

## Future Enhancements

### Potential Improvements
1. **Context Memory**: Remember user preferences from previous interactions
2. **Smart Defaults**: Suggest common answers based on user history
3. **Multi-turn Dialogue**: Handle complex multi-step clarifications
4. **Learning System**: Improve question generation based on user feedback
5. **Customization**: Allow users to set default preferences

### Advanced Features
1. **Predictive Questions**: Anticipate common clarification needs
2. **Contextual Awareness**: Consider current app state and recent actions
3. **Natural Language Understanding**: Better understanding of user intent
4. **Proactive Suggestions**: Suggest related tasks or optimizations 