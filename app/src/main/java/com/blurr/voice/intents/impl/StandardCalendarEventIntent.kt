package com.blurr.voice.intents.impl

import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.util.Log
import com.blurr.voice.intents.AppIntent
import com.blurr.voice.intents.ParameterSpec

/**
 * Creates calendar events using the standard Android ACTION_INSERT intent.
 * This follows the official Android documentation approach and is more likely
 * to be properly recognized by calendar apps.
 * 
 * This implementation uses Intent.ACTION_INSERT with Events.CONTENT_URI,
 * which will open the calendar app for the user to confirm the event.
 */
class StandardCalendarEventIntent : AppIntent {
    companion object {
        private const val TAG = "StandardCalendarEvent"
    }

    override val name: String = "CalendarEvent"

    override fun description(): String =
        "Create a calendar event using the standard Android calendar intent. This will open the calendar app for user confirmation."

    override fun parametersSpec(): List<ParameterSpec> = listOf(
        ParameterSpec(
            name = "title",
            type = "string",
            required = true,
            description = "The title/name of the calendar event."
        ),
        ParameterSpec(
            name = "location",
            type = "string",
            required = false,
            description = "The location where the event will take place."
        ),
        ParameterSpec(
            name = "start_time",
            type = "long",
            required = true,
            description = "The start time of the event in milliseconds since epoch."
        ),
        ParameterSpec(
            name = "end_time",
            type = "long",
            required = true,
            description = "The end time of the event in milliseconds since epoch."
        ),
        ParameterSpec(
            name = "description",
            type = "string",
            required = false,
            description = "Optional description or notes for the event."
        ),
        ParameterSpec(
            name = "all_day",
            type = "boolean",
            required = false,
            description = "Whether this is an all-day event (true/false)."
        ),
        ParameterSpec(
            name = "email",
            type = "string",
            required = false,
            description = "Comma-separated list of email addresses for invitees."
        )
    )

    override fun buildIntent(context: Context, params: Map<String, Any?>): Intent? {
        return try {
            createCalendarIntent(params)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create calendar intent", e)
            null
        }
    }

    /**
     * Creates the standard Android calendar intent following the official documentation.
     */
    private fun createCalendarIntent(params: Map<String, Any?>): Intent? {
        val title = params["title"]?.toString()?.trim()
        if (title.isNullOrEmpty()) {
            Log.e(TAG, "Event title is required")
            return null
        }

        val startTime = when (val start = params["start_time"]) {
            is Long -> start
            is String -> start.toLongOrNull()
            is Number -> start.toLong()
            else -> null
        }

        val endTime = when (val end = params["end_time"]) {
            is Long -> end
            is String -> end.toLongOrNull()
            is Number -> end.toLong()
            else -> null
        }

        if (startTime == null || endTime == null) {
            Log.e(TAG, "Valid start and end times are required")
            return null
        }

        if (endTime <= startTime) {
            Log.e(TAG, "End time must be after start time")
            return null
        }

        // Create the intent following Android documentation
        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            type = "vnd.android.cursor.dir/event"
            
            // Required fields
            putExtra(CalendarContract.Events.TITLE, title)
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startTime)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endTime)
            
            // Optional fields
            params["location"]?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let {
                putExtra(CalendarContract.Events.EVENT_LOCATION, it)
            }
            
            params["description"]?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let {
                putExtra(CalendarContract.Events.DESCRIPTION, it)
            }
            
            params["email"]?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let {
                putExtra(Intent.EXTRA_EMAIL, it)
            }
            
            val allDay = when (params["all_day"]) {
                is Boolean -> params["all_day"] as Boolean
                is String -> (params["all_day"] as String).toBoolean()
                else -> false
            }
            putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, allDay)
        }

        Log.d(TAG, "Created calendar intent for event: $title")
        Log.d(TAG, "Event time: $startTime to $endTime")
        
        return intent
    }
}