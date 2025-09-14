package com.blurr.voice.triggers

import java.util.UUID

enum class TriggerType {
    SCHEDULED_TIME,
    NOTIFICATION,
    CHARGING_STATE
}

enum class ChargingStatus {
    CHARGING,
    DISCHARGING
}

enum class BatteryCondition {
    ABOVE,
    BELOW
}

data class Trigger(
    val id: String = UUID.randomUUID().toString(),
    val type: TriggerType,
    val instruction: String,
    var isEnabled: Boolean = true,
    // For SCHEDULED_TIME triggers
    val hour: Int? = null,
    val minute: Int? = null,
    val daysOfWeek: Set<Int> = setOf(),
    // For NOTIFICATION triggers
    val packageName: String? = null,
    val appName: String? = null, // For display purposes
    // For CHARGING_STATE triggers
    val triggerOn: ChargingStatus? = null,
    val batteryPercentage: Int? = null,
    val batteryCondition: BatteryCondition? = null
)
