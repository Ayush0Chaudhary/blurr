package com.blurr.voice.utilities

import android.util.Log
import java.text.SimpleDateFormat
import java.util.*

/**
 * Utility class for calendar-related operations.
 * Provides helper functions for creating calendar events and parsing time.
 */
object CalendarUtils {
    private const val TAG = "CalendarUtils"

    /**
     * Parses various date/time formats and returns milliseconds since epoch.
     * Supports formats like:
     * - "2024-12-25 14:30" (YYYY-MM-dd HH:mm)
     * - "December 25, 2024 2:30 PM" 
     * - "25/12/2024 14:30"
     * - "today at 3pm", "tomorrow at 9am" (basic natural language)
     */
    fun parseDateTime(dateTimeString: String): Long? {
        val input = dateTimeString.trim().lowercase()
        val calendar = Calendar.getInstance()

        try {
            // Handle natural language patterns first
            when {
                input.contains("today") -> {
                    val timeStr = extractTime(input)
                    return if (timeStr != null) {
                        setTimeOnDate(calendar, timeStr)
                    } else {
                        // Default to current time if no specific time mentioned
                        calendar.timeInMillis
                    }
                }
                
                input.contains("tomorrow") -> {
                    calendar.add(Calendar.DAY_OF_MONTH, 1)
                    val timeStr = extractTime(input)
                    return if (timeStr != null) {
                        setTimeOnDate(calendar, timeStr)
                    } else {
                        // Default to same time tomorrow if no specific time mentioned
                        calendar.timeInMillis
                    }
                }
                
                input.contains("next week") -> {
                    calendar.add(Calendar.WEEK_OF_YEAR, 1)
                    val timeStr = extractTime(input)
                    return if (timeStr != null) {
                        setTimeOnDate(calendar, timeStr)
                    } else {
                        calendar.timeInMillis
                    }
                }
            }

            // Try standard date formats
            val formats = listOf(
                "yyyy-MM-dd HH:mm",
                "yyyy-MM-dd hh:mm a",
                "MM/dd/yyyy HH:mm",
                "MM/dd/yyyy hh:mm a",
                "dd/MM/yyyy HH:mm",
                "dd/MM/yyyy hh:mm a",
                "MMMM dd, yyyy hh:mm a",
                "MMM dd, yyyy HH:mm",
                "yyyy-MM-dd",
                "MM/dd/yyyy",
                "dd/MM/yyyy"
            )

            for (format in formats) {
                try {
                    val sdf = SimpleDateFormat(format, Locale.getDefault())
                    sdf.isLenient = false
                    val date = sdf.parse(dateTimeString)
                    return date?.time
                } catch (e: Exception) {
                    // Continue to next format
                }
            }

        } catch (e: Exception) {
            Log.w(TAG, "Error parsing date time: $dateTimeString", e)
        }

        return null
    }

    /**
     * Creates a calendar event with the specified parameters.
     * Returns a map suitable for use with the CalendarEventIntent.
     */
    fun createEventParams(
        title: String,
        startTime: Long,
        endTime: Long,
        location: String? = null,
        description: String? = null,
        allDay: Boolean = false
    ): Map<String, Any> {
        val params = mutableMapOf<String, Any>(
            "title" to title,
            "start_time" to startTime,
            "end_time" to endTime,
            "all_day" to allDay
        )

        location?.takeIf { it.isNotBlank() }?.let {
            params["location"] = it
        }

        description?.takeIf { it.isNotBlank() }?.let {
            params["description"] = it
        }

        return params
    }

    /**
     * Calculates end time for an event given start time and duration.
     */
    fun calculateEndTime(startTime: Long, durationMinutes: Int): Long {
        return startTime + (durationMinutes * 60 * 1000L)
    }

    /**
     * Gets the start of today in milliseconds.
     */
    fun getStartOfToday(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    /**
     * Gets the start of tomorrow in milliseconds.
     */
    fun getStartOfTomorrow(): Long {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    private fun extractTime(input: String): String? {
        // Extract time patterns like "3pm", "14:30", "2:30 PM"
        val timeRegex = Regex("""(\d{1,2}):?(\d{2})?\s*(am|pm)?""")
        val match = timeRegex.find(input)
        return match?.value
    }

    /**
     * Quick conversion helpers for common natural language dates.
     * These provide reasonable approximations for AI agents to use.
     */
    fun getTodayAt(hour: Int, minute: Int = 0): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, hour)
        calendar.set(Calendar.MINUTE, minute)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    fun getTomorrowAt(hour: Int, minute: Int = 0): Long {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, hour)
        calendar.set(Calendar.MINUTE, minute)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    fun getNextWeekAt(hour: Int, minute: Int = 0): Long {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.WEEK_OF_YEAR, 1)
        calendar.set(Calendar.HOUR_OF_DAY, hour)
        calendar.set(Calendar.MINUTE, minute)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    /**
     * Converts common time phrases to 24-hour format.
     * e.g., "3pm" -> 15, "9am" -> 9, "noon" -> 12
     */
    fun parseTimeToHour(timeString: String): Int? {
        val time = timeString.trim().lowercase()
        return when {
            time.contains("noon") || time.contains("12pm") -> 12
            time.contains("midnight") || time.contains("12am") -> 0
            time.contains("pm") -> {
                val hourStr = time.replace("pm", "").trim()
                val hour = hourStr.toIntOrNull() ?: return null
                if (hour == 12) 12 else hour + 12
            }
            time.contains("am") -> {
                val hourStr = time.replace("am", "").trim()
                val hour = hourStr.toIntOrNull() ?: return null
                if (hour == 12) 0 else hour
            }
            else -> time.toIntOrNull() // Assume 24-hour format
        }
    }

    private fun setTimeOnDate(calendar: Calendar, timeStr: String): Long? {
        try {
            val timeFormats = listOf("HH:mm", "hh:mm a", "H a", "ha")
            
            for (format in timeFormats) {
                try {
                    val sdf = SimpleDateFormat(format, Locale.getDefault())
                    val timeOnly = sdf.parse(timeStr)
                    if (timeOnly != null) {
                        val timeCalendar = Calendar.getInstance()
                        timeCalendar.time = timeOnly
                        
                        calendar.set(Calendar.HOUR_OF_DAY, timeCalendar.get(Calendar.HOUR_OF_DAY))
                        calendar.set(Calendar.MINUTE, timeCalendar.get(Calendar.MINUTE))
                        calendar.set(Calendar.SECOND, 0)
                        calendar.set(Calendar.MILLISECOND, 0)
                        
                        return calendar.timeInMillis
                    }
                } catch (e: Exception) {
                    // Continue to next format
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error setting time: $timeStr", e)
        }
        return null
    }
}