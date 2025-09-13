# Trigger System

## Overview
The Trigger System allows Panda to be initiated from various entry points, enabling proactive and automated task execution. Instead of waiting for a direct voice command, users can configure triggers, such as a specific time of day or an incoming notification from another app, to launch a Panda task. This infrastructure is designed to be flexible and extensible for future trigger types.

## Features

### Multiple Trigger Types
- **Scheduled Time**: Execute tasks at a precise time. Supports day-of-the-week selection (e.g., only on weekdays).
- **Notification**: Execute tasks when a notification is received from a user-specified application.

### Robust Scheduling
- **Exact Alarms**: Uses Android's `setExactAndAllowWhileIdle` to ensure time-based triggers fire precisely when scheduled, even in low-power modes.
- **Persistent Scheduling**: A `BootReceiver` automatically reschedules all active alarms after the device reboots, ensuring no triggers are missed.

### Seamless Integration
- **Direct Task Execution**: Triggers directly invoke the `AgentService` to perform tasks without needing to go through the conversational UI.
- **Centralized Management**: A `TriggerManager` handles all trigger-related logic, including creation, storage, and scheduling.

### User-Friendly UI
- **Intuitive Flow**: A dedicated screen allows users to choose the type of trigger they want to create from a list of cards.
- **Easy Configuration**: Simple UI for setting the time, selecting days of the week, or choosing an application to monitor.
- **Unified List**: A single screen to view, enable/disable, and delete all configured triggers.
- **Permission Handling**: The UI includes dialogs to guide the user in granting the necessary special permissions for exact alarms and notification listening.

## How It Works

### 1. Trigger Creation
1.  The user navigates to the "Triggers" screen from `MainActivity`.
2.  Tapping the "+" button opens `ChooseTriggerTypeActivity`, where the user selects the trigger type (e.g., "Scheduled Time").
3.  This opens `CreateTriggerActivity`, which displays the relevant configuration options.
  *   For a **Scheduled Time** trigger, the user sets a time and selects the days of the week.
  *   For a **Notification** trigger, the user selects an application from a list of installed apps.
4.  The user provides the task instruction (e.g., "send a good morning text to Mom").
5.  Upon saving, the new `Trigger` object is passed to the `TriggerManager`.

### 2. Trigger Management
- The `TriggerManager` saves the trigger configuration to `SharedPreferences` (as a JSON string).
- If it's a scheduled trigger, the `TriggerManager` uses the `AlarmManager` to schedule a one-time exact alarm for the next valid trigger time.
- If it's a notification trigger, no alarm is scheduled. The `PandaNotificationListenerService` is responsible for monitoring it.

### 3. Trigger Execution
#### For Scheduled Triggers:
1.  The `AlarmManager` fires a `PendingIntent` at the scheduled time.
2.  The `Intent` is received by the `TriggerReceiver`.
3.  The `TriggerReceiver` starts the `AgentService`, passing the task instruction.
4.  It then immediately calls the `TriggerManager` to reschedule the alarm for the next valid day.

#### For Notification Triggers:
1.  The `PandaNotificationListenerService` is constantly running in the background (once enabled by the user).
2.  When a notification is posted by any app, the service's `onNotificationPosted` method is called.
3.  The service checks if the notification's source package name matches any enabled notification triggers stored in the `TriggerManager`.
4.  If a match is found, it extracts the notification content (title, text) from the notification extras.
5.  It fires an `Intent` to the `TriggerReceiver` with both the trigger instruction and notification content.
6.  The `TriggerReceiver` builds an enhanced instruction that includes notification context for better AI understanding.
7.  The `TriggerReceiver` starts the `AgentService` with the enhanced instruction containing both the predefined task and notification details.

## Technical Implementation

### Key Components

#### `Trigger` (data class)
- **Purpose**: A flexible data class to hold the configuration for any trigger type.
- **Key Fields**: `id`, `type` (enum: `SCHEDULED_TIME`, `NOTIFICATION`), `instruction`, `isEnabled`, and nullable fields for type-specific data (`hour`, `minute`, `daysOfWeek`, `packageName`, `appName`).

#### `TriggerManager` (singleton)
- **Purpose**: The central brain for all trigger-related logic.
- **Responsibilities**:
  - CRUD operations (add, remove, update, get) for triggers.
  - Persistence using `SharedPreferences` and `Gson`.
  - Scheduling and canceling exact alarms via `AlarmManager`.
  - Calculating the next valid trigger time for scheduled alarms based on selected days.

#### `TriggerReceiver` (BroadcastReceiver)
- **Purpose**: A single entry point for all trigger events.
- **Responsibilities**:
  - Receives `Intent` broadcasts from both `AlarmManager` and `PandaNotificationListenerService`.
  - Extracts the task instruction and optional notification content.
  - For notification triggers, enhances the instruction with notification context (title, text, package).
  - Starts the `v2.AgentService` to execute the task with the enhanced instruction.
  - Calls back to the `TriggerManager` to reschedule a time-based alarm after it has fired.

#### `BootReceiver` (BroadcastReceiver)
- **Purpose**: Ensures scheduled triggers persist across device reboots.
- **Responsibilities**:
  - Listens for the `ACTION_BOOT_COMPLETED` system broadcast.
  - Calls the `TriggerManager` to reschedule all active, time-based triggers.

#### `PandaNotificationListenerService` (NotificationListenerService)
- **Purpose**: Listens for system-wide notifications.
- **Responsibilities**:
  - Compares the package name of incoming notifications against stored notification triggers.
  - Extracts notification content (title, text) from notification extras.
  - Broadcasts an `Intent` to the `TriggerReceiver` when a match is found, including notification context.

#### UI Activities
- **`TriggersActivity`**: Displays a list of all configured triggers. Handles enabling/disabling and deleting triggers.
- **`ChooseTriggerTypeActivity`**: A new screen that provides a card-based UI for selecting a trigger type.
- **`CreateTriggerActivity`**: A dynamic screen that shows the correct configuration UI based on the type chosen in the previous step.

### File Structure
```
app/src/main/java/com/blurr/voice/triggers/
├── ui/
│   ├── AppAdapter.kt
│   ├── AppInfo.kt
│   ├── ChooseTriggerTypeActivity.kt
│   ├── CreateTriggerActivity.kt
│   ├── TriggerAdapter.kt
│   └── TriggersActivity.kt
├── BootReceiver.kt
├── PandaNotificationListenerService.kt
├── PermissionUtils.kt
├── Trigger.kt
├── TriggerManager.kt
└── TriggerReceiver.kt

app/src/main/res/
├── layout/
│   ├── activity_choose_trigger_type.xml
│   ├── activity_create_trigger.xml
│   ├── activity_triggers.xml
│   ├── item_app.xml
│   └── item_trigger.xml
└── ...
```

### Permissions
- `android.permission.RECEIVE_BOOT_COMPLETED`: Required for the `BootReceiver` to function.
- `android.permission.SCHEDULE_EXACT_ALARM`: Required to schedule precise alarms on modern Android versions.
- `android.permission.BIND_NOTIFICATION_LISTENER_SERVICE`: Required for the `PandaNotificationListenerService` to read notifications. This permission must be granted manually by the user in system settings.
