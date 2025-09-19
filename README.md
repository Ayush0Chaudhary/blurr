# 🐼 Panda: Your Personal AI Phone Operator  
**You touch grass. I'll touch your glass.**  
[![Join Discord](https://img.shields.io/badge/Join%20Discord-5865F2?style=for-the-badge&logo=discord&logoColor=white)](https://discord.gg/b2hxFNXvWk)
<a href='https://play.google.com/store/apps/details?id=com.blurr.voice&hl=en_US&pcampaignid=pcampaignidMKT-Other-global-all-co-prtnr-py-PartBadge-Mar2515-1'><img alt='Get it on Google Play' src='https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png' width=250/></a>
[![Ask DeepWiki](https://deepwiki.com/badge.svg)](https://deepwiki.com/Ayush0Chaudhary/blurr)
---

# Demos:

#### Explaining all the triggers of Panda
 [![Watch the video](https://img.youtube.com/vi/IDvuqmPyKZs/hqdefault.jpg)](https://www.youtube.com/embed/IDvuqmPyKZs)

#### Sending Welcome message to all the new Connections on Linkedin
 [![Watch the video](https://img.youtube.com/vi/JO_EWFYJJjA/hqdefault.jpg)](https://www.youtube.com/embed/JO_EWFYJJjA)

#### 5 task demo: 
https://github.com/user-attachments/assets/cf76bb00-2bf4-4274-acad-d9f4c0d47188


**Panda** is a proactive, on-device AI agent for Android that autonomously understands natural language commands and operates your phone's UI to achieve them. Inspired by the need to make modern technology more accessible, Panda acts as your personal operator, capable of handling complex, multi-step tasks across different applications.

[![Project Status: WIP](https://img.shields.io/badge/project%20status-wip-yellow.svg)](https://wip.vost.pt/)
[![License: Personal Use](https://img.shields.io/badge/License-Personal%20Use%20Only-red.svg)](./LICENSE)
[![Kotlin Version](https://img.shields.io/badge/Kotlin-1.9.22-7F52FF.svg?logo=kotlin)](https://kotlinlang.org)

## Core Capabilities

* 🧠 **Intelligent UI Automation:** Panda sees the screen, understands the context of UI elements, and performs actions like tapping, swiping, and typing to navigate apps and complete tasks.
* 📢 **High Qaulity voice:** Panda have high quality voice by GCS's Chirp  
* 💾 **Persistent & Personalized local Memory:** ⚠️ **Temporarily Disabled** - Panda memory is turned off as of yet. Memory functionality will be restored in a future update.

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
         GEMINI_API_KEYS=<add 2-3 keys working here>
         TAVILY_API=<not-req><add randome string>
         MEM0_API=<not-req><add randome string>
         PICOVOICE_ACCESS_KEY=<not-req><add randome string>
         GOOGLE_TTS_API_KEY=<optional - leave empty to use native Android TTS>
         GCLOUD_GATEWAY_PICOVOICE_KEY=<not needed><add randome string>
         GCLOUD_GATEWAY_URL=<not needed><add randome string>
         GCLOUD_PROXY_URL=<not needed><add randome string>
         GCLOUD_PROXY_URL_KEY=<not needed><add randome string>
         REVENUE_CAT_PUBLIC_URL=<not needed> <add randome string>
         REVENUECAT_API_KEY=<not needed> <add randome string>
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

This project is licensed under a Personal Use License - see the [LICENSE](LICENSE) file for details.

**Personal & Educational Use:** Free to use, modify, and distribute for personal, educational, and non-commercial purposes.

**Commercial Use:** Requires a separate commercial license. Please contact Panda AI for commercial licensing terms.

### A small video to help you understand what the project is about. 
https://github.com/user-attachments/assets/b577072e-2f7f-42d2-9054-3a11160cf87d

Write you api key in in local.properties, more keys you use, better is the speed 😉

# View logs in real-time
adb logcat | grep GeminiApi

## Star History

[![Star History Chart](https://api.star-history.com/svg?repos=Ayush0Chaudhary/blurr&type=Timeline)](https://www.star-history.com/#Ayush0Chaudhary/blurr&Timeline)
