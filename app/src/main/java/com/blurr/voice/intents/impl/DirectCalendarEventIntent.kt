package com.blurr.voice.intents.impl

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.util.Log
import androidx.core.content.ContextCompat
import com.blurr.voice.intents.AppIntent
import com.blurr.voice.intents.ParameterSpec
import java.util.Calendar
import java.util.TimeZone

/**
 * Creates calendar events directly in the device's default calendar
 * without opening the calendar app UI.
 * 
 * This implementation writes directly to the Calendar Provider,
 * creating events instantly without user confirmation.
 */
class DirectCalendarEventIntent : AppIntent {
    companion object {
        private const val TAG = "DirectCalendarEvent"
    }

    override val name: String = "CalendarEvent"

    override fun description(): String =
        "Create a calendar event directly in the device's default calendar without opening any UI."

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
        )
    )

    override fun buildIntent(context: Context, params: Map<String, Any?>): Intent? {
        // This method creates the event directly instead of returning an Intent
        val success = createEventDirectly(context, params)
        
        // Return a dummy intent or null based on success
        // The actual work is done in createEventDirectly()
        return if (success) {
            // Return a dummy successful intent
            Intent().apply { 
                putExtra("calendar_event_created", true)
                putExtra("event_title", params["title"]?.toString())
            }
        } else {
            null
        }
    }

    /**
     * Creates a calendar event directly in the device's calendar
     * without opening any UI.
     */
    private fun createEventDirectly(context: Context, params: Map<String, Any?>): Boolean {
        // Check for calendar permissions
        if (!hasCalendarPermissions(context)) {
            Log.e(TAG, "Calendar permissions not granted")
            return false
        }

        try {
            val title = params["title"]?.toString()?.trim()
            if (title.isNullOrEmpty()) {
                Log.e(TAG, "Event title is required")
                return false
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
                return false
            }

            if (endTime <= startTime) {
                Log.e(TAG, "End time must be after start time")
                return false
            }

            // Get the default calendar ID
            val calendarId = getDefaultCalendarId(context)
            if (calendarId == null) {
                Log.e(TAG, "No default calendar found")
                return false
            }

            // Create the event using ContentValues for direct database insertion
            val eventValues = ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, calendarId)
                put(CalendarContract.Events.TITLE, title)
                put(CalendarContract.Events.DTSTART, startTime)
                put(CalendarContract.Events.DTEND, endTime)
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
                
                // Optional fields
                params["description"]?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let {
                    put(CalendarContract.Events.DESCRIPTION, it)
                }
                
                params["location"]?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let {
                    put(CalendarContract.Events.EVENT_LOCATION, it)
                }

                val allDay = when (params["all_day"]) {
                    is Boolean -> if (params["all_day"] as Boolean) 1 else 0
                    is String -> if ((params["all_day"] as String).toBoolean()) 1 else 0
                    else -> 0
                }
                put(CalendarContract.Events.ALL_DAY, allDay)
                
                // Set access level to default (not private)
                put(CalendarContract.Events.ACCESS_LEVEL, CalendarContract.Events.ACCESS_DEFAULT)
                
                // Set availability to busy
                put(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_BUSY)
                
                // Set event status to confirmed
                put(CalendarContract.Events.STATUS, CalendarContract.Events.STATUS_CONFIRMED)
                
                // Set has alarm to 0 (no default alarm)
                put(CalendarContract.Events.HAS_ALARM, 0)
                
                // Note: VISIBLE field cannot be set by third-party apps, only by the calendar provider
            }

            // Insert the event
            val eventUri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, eventValues)
            
            if (eventUri != null) {
                Log.i(TAG, "Calendar event created successfully: $title")
                Log.d(TAG, "Event URI: $eventUri")
                
                // Additional debugging - verify the event was actually inserted
                val eventId = eventUri.lastPathSegment
                Log.d(TAG, "Event ID: $eventId")
                Log.d(TAG, "Event details - Start: $startTime, End: $endTime, Calendar: $calendarId")
                
                // Try to read back the event to verify it exists
                try {
                    val verifyProjection = arrayOf(
                        CalendarContract.Events._ID,
                        CalendarContract.Events.TITLE,
                        CalendarContract.Events.DTSTART,
                        CalendarContract.Events.DTEND,
                        CalendarContract.Events.CALENDAR_ID
                    )
                    
                    val verifyCursor = context.contentResolver.query(
                        eventUri,
                        verifyProjection,
                        null,
                        null,
                        null
                    )
                    
                    verifyCursor?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val verifyTitle = cursor.getString(cursor.getColumnIndexOrThrow(CalendarContract.Events.TITLE))
                            val verifyStart = cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Events.DTSTART))
                            val verifyEnd = cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Events.DTEND))
                            val verifyCalId = cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Events.CALENDAR_ID))
                            
                            Log.d(TAG, "Event verification - Title: '$verifyTitle', Start: $verifyStart, End: $verifyEnd, CalendarID: $verifyCalId")
                        } else {
                            Log.w(TAG, "Could not verify event existence after creation")
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Could not verify event creation", e)
                }
                
                // Try to trigger calendar sync to make the event visible in calendar apps
                try {
                    // Send a broadcast to notify calendar apps of the change
                    val broadcastIntent = Intent(Intent.ACTION_PROVIDER_CHANGED).apply {
                        data = CalendarContract.CONTENT_URI
                    }
                    context.sendBroadcast(broadcastIntent)
                    Log.d(TAG, "Sent calendar change broadcast")
                } catch (e: Exception) {
                    Log.w(TAG, "Could not send calendar change broadcast", e)
                }
                
                return true
            } else {
                Log.e(TAG, "Failed to create calendar event")
                return false
            }

        } catch (e: Exception) {
            Log.e(TAG, "Exception while creating calendar event", e)
            return false
        }
    }

    /**
     * Gets the ID of the default calendar to use for creating events.
     */
    private fun getDefaultCalendarId(context: Context): Long? {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.OWNER_ACCOUNT,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
            CalendarContract.Calendars.VISIBLE,
            CalendarContract.Calendars.SYNC_EVENTS
        )

        val cursor = context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ${CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR}",
            null,
            null
        )

        var selectedCalendarId: Long? = null
        var bestCalendarId: Long? = null

        cursor?.use {
            Log.d(TAG, "Available calendars:")
            while (it.moveToNext()) {
                val calendarId = it.getLong(it.getColumnIndexOrThrow(CalendarContract.Calendars._ID))
                val displayName = it.getString(it.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME))
                val ownerAccount = it.getString(it.getColumnIndexOrThrow(CalendarContract.Calendars.OWNER_ACCOUNT))
                val accessLevel = it.getInt(it.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL))
                val visible = it.getInt(it.getColumnIndexOrThrow(CalendarContract.Calendars.VISIBLE))
                val syncEvents = it.getInt(it.getColumnIndexOrThrow(CalendarContract.Calendars.SYNC_EVENTS))
                
                Log.d(TAG, "Calendar: '$displayName' (ID: $calendarId, Account: $ownerAccount, Access: $accessLevel, Visible: $visible, Sync: $syncEvents)")
                
                // Prefer visible calendars that sync events
                if (visible == 1 && syncEvents == 1) {
                    if (bestCalendarId == null) {
                        bestCalendarId = calendarId
                    }
                }
                
                // Fall back to any writable calendar
                if (selectedCalendarId == null) {
                    selectedCalendarId = calendarId
                }
            }
        }

        val finalCalendarId = bestCalendarId ?: selectedCalendarId
        if (finalCalendarId != null) {
            Log.d(TAG, "Selected calendar ID: $finalCalendarId")
            return finalCalendarId
        } else {
            Log.w(TAG, "No writable calendar found")
            return null
        }
    }

    /**
     * Checks if the app has the necessary calendar permissions.
     */
    private fun hasCalendarPermissions(context: Context): Boolean {
        val writePermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED

        val readPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED

        return writePermission && readPermission
    }
}