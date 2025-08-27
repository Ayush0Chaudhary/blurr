# 🐼 Panda: Your Personal AI Phone Operator  

**You touch grass. I'll touch your glass.**  
[![Join Discord](https://img.shields.io/badge/Join%20Discord-5865F2?style=for-the-badge&logo=discord&logoColor=white)](https://discord.gg/FhyfrZBq)
[![Apply for Internal Test](https://img.shields.io/badge/Apply%20Now%20For%20Closed%20Testing-34A853?style=for-the-badge&logo=googleforms&logoColor=white)](https://docs.google.com/forms/d/e/1FAIpQLScgviOQ13T8Z5sYD6KOLAPex4H_St0ubWNmuRIsXweFzRVrSw/viewform?usp=dialog)

Apply for the closed test: [google form](https://docs.google.com/forms/d/e/1FAIpQLScgviOQ13T8Z5sYD6KOLAPex4H_St0ubWNmuRIsXweFzRVrSw/viewform?usp=dialog)
---

# Demos:

#### 5 task demo: 
https://github.com/user-attachments/assets/cf76bb00-2bf4-4274-acad-d9f4c0d47188

#### Text My Brother Happy Birthday
https://github.com/user-attachments/assets/bac5726a-64b3-4cbf-b116-0ebc369bcec0

**Panda** is a proactive, on-device AI agent for Android that autonomously understands natural language commands and operates your phone's UI to achieve them. Inspired by the need to make modern technology more accessible, Panda acts as your personal operator, capable of handling complex, multi-step tasks across different applications.

[![Project Status: WIP](https://img.shields.io/badge/project%20status-wip-yellow.svg)](https://wip.vost.pt/)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](https://opensource.org/licenses/MIT)
[![Kotlin Version](https://img.shields.io/badge/Kotlin-1.9.22-7F52FF.svg?logo=kotlin)](https://kotlinlang.org)

## Core Capabilities

* 🧠 **Intelligent UI Automation:** Panda sees the screen, understands the context of UI elements, and performs actions like tapping, swiping, and typing to navigate apps and complete tasks.
* 📢 **High Qaulity voice:** Panda have high quality voice by GCS's Chirp  
* 💾 **Persistent & Personalized local Memory:** Panda remembers key facts about you and learned procedures across sessions. It learns your preferences, contacts, and habits to become a truly personalized assistant over time.

## Architecture Overview

Panda is built on a sophisticated multi-agent system written entirely in Kotlin. This architecture separates responsibilities, allowing for more complex and reliable reasoning.

* **Eyes & Hands (The Actuator):** The **Android Accessibility Service** serves as the agent's physical connection to the device, providing the low-level ability to read the screen element hierarchy and programmatically perform touch gestures.
* **The Brain (The LLM):** All high-level reasoning, planning, and analysis are powered by **LLM** models. This is where decisions are made.
* **The Agent:**
    * **Operator:** This is executor with Notepad.


## 🚀 Getting Started

### Prerequisites
* Android Studio (latest version recommended)
* An Android device or emulator with API level 26+
* Some Gemini keys, sample ENV
```
sdk.dir=
GEMINI_API_KEYS=
```

### Installation

1.  **Clone the repository:**
    ```bash
    git clone [https://github.com/ayush0chaudhary/blurr.git](https://github.com/ayush0chaudhary/blurr.git)
    cd blurr
    ```

2.  **Set up API Keys:**
    This project uses a `local.properties` file to securely handle API keys. This file is included in `.gitignore` and should never be committed.
    * Create a file named `local.properties` in the root directory of the project.
    * Add your API keys to this file in the following format:
        ```properties
         sdk.dir=
         GEMINI_API_KEYS=
        ```

3.  **Build & Run:**
    * Open the project in Android Studio.
    * Let Gradle sync all the dependencies.
    * Run the app on your selected device or emulator.

4.  **Enable Accessibility Service:**
    * On the first run, the app will prompt you to grant Accessibility permission.
    * Click "Grant Access" and enable the "Panda" service in your phone's settings. This is required for the agent to see and control the screen.

## 🗺️ What's Next for Panda (Roadmap)

Panda is currently a powerful proof-of-concept, and the roadmap is focused on making it a truly indispensable assistant.

* [ ] **NOT UPDATED:** List not updated

## 🤝 Contributing

Contributions are welcome! If you have ideas for new features or improvements, feel free to open an issue or submit a pull request.

## 📜 License

This project is licensed under the MIT License - see the [LICENSE.md](LICENSE.md) file for details.

### A small video to help you understand what the project is about. 
https://github.com/user-attachments/assets/b577072e-2f7f-42d2-9054-3a11160cf87d

Write you api key in in local.properties, more keys you use, better is the speed 😉

# View logs in real-time
adb logcat | grep GeminiApi

## Star History

[![Star History Chart](https://api.star-history.com/svg?repos=Ayush0Chaudhary/blurr&type=Timeline)](https://www.star-history.com/#Ayush0Chaudhary/blurr&Timeline)
