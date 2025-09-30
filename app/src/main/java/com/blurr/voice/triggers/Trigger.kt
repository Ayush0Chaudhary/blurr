/**
 * @file Trigger.kt
 * @brief Defines the data models for triggers and their types.
 *
 * This file contains the `TriggerType` enum and the `Trigger` data class, which are
 * fundamental to the trigger system. They provide a structured way to represent
 * different types of conditions that can initiate a task.
 */
package com.blurr.voice.triggers

import java.util.UUID

/**
 * Enumerates the different types of conditions that can activate a trigger.
 */
enum class TriggerType {
    /** A trigger that fires at a specific time of day. */
    SCHEDULED_TIME,
    /** A trigger that fires when a notification is received from a specific app. */
    NOTIFICATION,
    /** A trigger that fires when the device's charging state changes. */
    CHARGING_STATE
}

/**
 * A data class representing a single trigger condition and its associated task.
 *
 * This class holds all the information needed to define a trigger, including its type,
 * the instruction to execute, and any parameters specific to that type (like time or app name).
 *
 * @property id A unique identifier for the trigger, generated automatically.
 * @property type The [TriggerType] that defines the condition for this trigger.
 * @property instruction The natural language instruction for the agent to execute when the trigger fires.
 * @property isEnabled A flag to easily enable or disable the trigger without deleting it.
 * @property hour For [TriggerType.SCHEDULED_TIME], the hour of the day (0-23) to fire.
 * @property minute For [TriggerType.SCHEDULED_TIME], the minute of the hour (0-59) to fire.
 * @property packageName For [TriggerType.NOTIFICATION], the package name of the target application.
 * @property appName For [TriggerType.NOTIFICATION], the user-friendly name of the target application (for display).
 * @property daysOfWeek For [TriggerType.SCHEDULED_TIME], a set of integers representing the days of the week
 *                     (1 for Sunday, 2 for Monday, etc.) on which the trigger should be active.
 * @property chargingStatus For [TriggerType.CHARGING_STATE], the status to trigger on ("Connected" or "Disconnected").
 */
data class Trigger(
    val id: String = UUID.randomUUID().toString(),
    val type: TriggerType,
    val instruction: String,
    var isEnabled: Boolean = true,
    val hour: Int? = null,
    val minute: Int? = null,
    val packageName: String? = null,
    val appName: String? = null,
    val daysOfWeek: Set<Int> = setOf(1, 2, 3, 4, 5, 6, 7),
    val chargingStatus: String? = null
)
