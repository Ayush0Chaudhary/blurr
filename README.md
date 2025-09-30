# 🐼 Panda: Your Personal AI Phone Operator
**You touch grass. I'll touch your glass.**
[![Join Discord](https://img.shields.io/badge/Join%20Discord-5865F2?style=for-the-badge&logo=discord&logoColor=white)](https://discord.gg/b2hxFNXvWk)
[![Apply for Internal Test](https://img.shields.io/badge/Apply%20Now%20For%20Closed%20Testing-34A853?style=for-the-badge&logo=googleforms&logoColor=white)](https://docs.google.com/forms/d/e/1FAIpQLScgviOQ13T8Z5sYD6KOLAPex4H_St0ubWNmuRIsXweFzRVrSw/viewform?usp=dialog)
[![Ask DeepWiki](https://deepwiki.com/badge.svg)](https://deepwiki.com/Ayush0Chaudhary/blurr)

Apply for the closed test: [google form](https://docs.google.com/forms/d/e/1FAIpQLScgviOQ13T8Z5sYD6KOLAPex4H_St0ubWNmuRIsXweFzRVrSw/viewform?usp=dialog)
---

# Demos:
#### Sending Welcome message to all the new Connections on Linkedin
[![Watch the video](https://img.youtube.com/vi/JO_EWFYJJjA/hqdefault.jpg)](https://www.youtube.com/embed/JO_EWFYJJjA)


#### 5 task demo:
https://github.com/user-attachments/assets/cf76bb00-2bf4-4274-acad-d9f4c0d47188


**Panda** is a proactive, on-device AI agent for Android that autonomously understands natural language commands and operates your phone's UI to achieve them. Inspired by the need to make modern technology more accessible, Panda acts as your personal operator, capable of handling complex, multi-step tasks across different applications.

[![Project Status: WIP](https://img.shields.io/badge/project%20status-wip-yellow.svg)](https://wip.vost.pt/)
[![License: Personal Use](https://img.shields.io/badge/License-Personal%20Use%20Only-red.svg)](./LICENSE)
[![Kotlin Version](https://img.shields.io/badge/Kotlin-1.9.22-7F52FF.svg?logo=kotlin)](https://kotlinlang.org)

## Core Capabilities

*   🧠 **Intelligent UI Automation:** Panda sees the screen, understands the context of UI elements, and performs actions like tapping, swiping, and typing to navigate apps and complete tasks.
*   💬 **V1 Conversational Agent:** A voice-first assistant that can hold a conversation, answer questions, and perform simple tasks. It includes a clarification flow to resolve ambiguous requests.
*   🤖 **V2 Autonomous Agent:** A more advanced agent that operates on a **SENSE -> THINK -> ACT** loop to execute complex, multi-step tasks autonomously. It can write to its own file system to take notes and plan.
*   📢 **High-Quality Voice:** Panda uses Google Cloud's high-quality text-to-speech voices for natural-sounding interactions.
*   🔌 **Extensible Actions:** The agent's capabilities are defined by a clear, data-driven `Action` system, making it easy for developers to add new functionalities.
*   💾 **Persistent & Personalized Memory:** ⚠️ **Temporarily Disabled** - Memory functionality will be restored in a future update.

## Architecture Overview

Panda is built on a sophisticated multi-agent system written entirely in Kotlin. This architecture separates responsibilities for more reliable reasoning and execution.

### V2 - Autonomous Task Agent
The V2 agent is designed for complex task execution and operates on a continuous loop.

*   **`AgentService`**: A foreground service that hosts the V2 agent, allowing it to run long-running tasks in the background without being killed by the OS.
*   **`Agent`**: The central orchestrator of the V2 agent. It owns all the components and runs the primary **SENSE -> THINK -> ACT** loop.
*   **SENSE (Perception)**
    *   **`ScreenInteractionService`**: The core `AccessibilityService` that acts as the agent's "eyes" and "hands." It reads the screen's XML layout and performs gestures.
    *   **`Perception`**: Coordinates the "SENSE" step. It uses `Eyes` to capture raw data and `SemanticParser` to process it into a clean, LLM-friendly format.
*   **THINK (Reasoning & Memory)**
    *   **`GeminiApi`**: A robust client for communicating with the Google Gemini LLM, featuring retry logic and a secure proxy dispatcher.
    *   **`MemoryManager` & `PromptBuilder`**: Manages the agent's short-term memory, constructs the prompt for the LLM at each step, and maintains the conversation history.
*   **ACT (Execution)**
    *   **`ActionExecutor`**: The "hands" of the agent. It takes the structured `Action` command from the LLM and executes it on the device using the `ScreenInteractionService`.
    *   **`Action.kt`**: The single source of truth for all possible commands the agent can execute.

### V1 - Conversational Agent
The V1 agent is designed for voice-first, conversational interactions.

*   **`ConversationalAgentService`**: The main service that orchestrates the V1 agent loop: listening for user input, processing it, and generating a spoken response.
*   **`SpeechCoordinator`**: A singleton that manages STT (Speech-to-Text) and TTS (Text-to-Speech) operations, ensuring they don't conflict.
*   **`ClarificationAgent`**: A specialized sub-agent that determines if a user's command is ambiguous and generates follow-up questions to resolve the ambiguity.

### Shared Components
*   **`FileSystem`**: Provides a sandboxed file system within the app's private directory, allowing the V2 agent to read and write files for planning and memory.
*   **`IntentRegistry`**: A system for defining and executing high-level Android intents, such as making a phone call or sending an email.

## 🚀 Getting Started

### Prerequisites
*   Android Studio (latest version recommended)
*   An Android device or emulator with API level 26+
*   Google Gemini API Keys

### Installation

1.  **Clone the repository:**
    ```bash
    git clone https://github.com/ayush0chaudhary/blurr.git
    cd blurr
    ```

2.  **Set up API Keys:**
    This project uses a `local.properties` file to securely handle API keys. This file is included in `.gitignore` and should never be committed.
    *   Create a file named `local.properties` in the root directory of the project.
    *   Add your Gemini API key(s). You can add multiple keys, and the app will rotate through them.
        ```properties
        sdk.dir=<Your Android SDK location>
        GEMINI_API_KEYS=<YOUR_GEMINI_KEY_1>,<YOUR_GEMINI_KEY_2>
        ```
    *   **Note:** The other keys in the `local.properties` template (`TAVILY_API`, `PICOVOICE_ACCESS_KEY`, etc.) are for experimental or future features and are not required to run the core application. You can leave them blank or with placeholder text.

3.  **Build & Run:**
    *   Open the project in Android Studio.
    *   Let Gradle sync all the dependencies.
    *   Run the app on your selected device or emulator.

4.  **Enable Permissions:**
    *   On the first run, the app will guide you through a stepper to grant the necessary permissions, including the Accessibility Service, which is required for the agent to see and control the screen.

## 🗺️ What's Next for Panda (Roadmap)

*   **Short-Term:**
    *   [ ] Restore and improve the local memory system (`MemoryManager`).
    *   [ ] Enhance error handling and recovery within the V2 agent loop.
    *   [ ] Add more `Action` types (e.g., interacting with notifications, handling permissions).
    *   [ ] Refine the UI for better user experience.
*   **Mid-Term:**
    *   [ ] Implement self-healing capabilities where the agent can detect and correct its own mistakes.
    *   [ ] Introduce multi-modal input, allowing the agent to understand screen content visually (vision model) in addition to the XML hierarchy.
    *   [ ] Improve long-term memory retrieval and summarization.
*   **Long-Term:**
    *   [ ] Explore on-device model support for faster, offline operation.
    *   [ ] Deeper OS integration for more seamless control.

## 📖 Codebase Guide

This repository has been fully documented. Here is a high-level guide to the main packages:

*   `com.blurr.voice`: Core Activities, Services, and custom UI components.
*   `com.blurr.voice.agents`: High-level agent logic, such as the `ClarificationAgent`.
*   `com.blurr.voice.api`: Clients for external APIs (Gemini, TTS) and low-level device interaction (`Eyes`, `Finger`).
*   `com.blurr.voice.data`: Room database and DAOs for persistent storage (`MemoryManager`).
*   `com.blurr.voice.intents`: A system for defining and executing Android system intents (e.g., Dial, Share).
*   `com.blurr.voice.services`: Background services for features like the floating button and wake word detection.
*   `com.blurr.voice.triggers`: Logic for triggering the agent based on system events (e.g., boot, charging).
*   `com.blurr.voice.ui`: UI-related components and themes.
*   `com.blurr.voice.utilities`: Shared helper classes and managers for common tasks.
*   **`com.blurr.voice.v2`**: The next-generation autonomous agent architecture.
    *   `v2/actions`: Defines the agent's capabilities (`Action.kt`) and how to execute them (`ActionExecutor.kt`).
    *   `v2/fs`: A sandboxed file system for the agent.
    *   `v2/llm`: The Gemini API client and its data models.
    *   `v2/message_manager`: Handles short-term memory and prompt construction.
    *   `v2/perception`: Contains the logic for screen analysis and semantic parsing.

## 🤝 Contributing

Contributions are welcome! If you have ideas for new features or improvements, feel free to open an issue or submit a pull request.

## 📜 License

This project is licensed under a Personal Use License - see the [LICENSE](LICENSE) file for details.

**Personal & Educational Use:** Free to use, modify, and distribute for personal, educational, and non-commercial purposes.

**Commercial Use:** Requires a separate commercial license. Please contact the project author for licensing terms.

### A small video to help you understand what the project is about.
https://github.com/user-attachments/assets/b577072e-2f7f-42d2-9054-3a11160cf87d

Write you api key in in local.properties, more keys you use, better is the speed 😉

# View logs in real-time
adb logcat | grep GeminiApi

## Star History

[![Star History Chart](https://api.star-history.com/svg?repos=Ayush0Chaudhary/blurr&type=Timeline)](https://www.star-history.com/#Ayush0Chaudhary/blurr&Timeline)