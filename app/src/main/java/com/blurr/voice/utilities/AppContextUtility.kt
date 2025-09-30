/**
 * @file AppContextUtility.kt
 * @brief Provides utility functions for retrieving information about installed applications.
 *
 * This file contains the `AppContextUtility` class, which can be used to get lists of
 * all installed applications or just user-installed applications. It also includes a data class
 * `AppInfo` to structure the application data.
 *
 * TODO: This file is not currently used anywhere in the project but is kept for its potential
 * future use in multi-app tasks.
 */
package com.blurr.voice.utilities

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log


/**
 * A data class to hold key information about an installed application.
 *
 * @property appName The user-facing name of the application.
 * @property packageName The unique package name of the application.
 * @property isSystemApp A flag indicating if the application is a system app.
 * @property isEnabled A flag indicating if the application is currently enabled.
 * @property versionName The user-facing version name of the application.
 * @property versionCode The internal version code of the application.
 */
data class AppInfo(
    val appName: String,
    val packageName: String,
    val isSystemApp: Boolean,
    val isEnabled: Boolean,
    val versionName: String? = null,
    val versionCode: Long = 0
)

/**
 * A utility class for querying information about applications installed on the device.
 * @param context The application context.
 */
class AppContextUtility(private val context: Context) {

    companion object {
        private const val TAG = "AppListUtility"
    }

    /**
     * Retrieves a list of all installed applications, including system apps.
     *
     * @return A list of [AppInfo] objects for every installed application. Returns an
     *         empty list if an error occurs.
     */
    @SuppressLint("QueryPermissionsNeeded")
    fun getAllApps(): List<AppInfo> {
        return try {
            val pm = context.packageManager
            val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)

            apps.map { app ->
                AppInfo(
                    appName = app.loadLabel(pm).toString(),
                    packageName = app.packageName,
                    isSystemApp = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                    isEnabled = app.enabled
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting all apps", e)
            emptyList()
        }
    }

    /**
     * Retrieves a list of user-installed applications only (non-system apps).
     *
     * @return A list of [AppInfo] objects for every non-system application. Returns an
     *         empty list if an error occurs.
     */
    @SuppressLint("QueryPermissionsNeeded")
    fun getUserApps(): List<AppInfo> {
        return try {
            val pm = context.packageManager
            val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)

            apps.filter { app ->
                (app.flags and ApplicationInfo.FLAG_SYSTEM) == 0
            }.map { app ->
                AppInfo(
                    appName = app.loadLabel(pm).toString(),
                    packageName = app.packageName,
                    isSystemApp = false,
                    isEnabled = app.enabled
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting user apps", e)
            emptyList()
        }
    }

    /**
     * **(Disabled)** Uses an LLM to intelligently filter the app list based on a user's instruction.
     *
     * This function is intended to reduce the context size sent to the main agent by having a
     * specialized LLM call first determine which apps are relevant to the user's command.
     *
     * **Note:** This function is currently commented out and is not used in the application.
     *
     * @param userInstruction The user's command.
     * @param includeSystemApps Whether to include system apps in the list sent to the LLM.
     * @return A string containing the filtered list of apps, or an error message.
     */
//    suspend fun getAppsForInstruction(userInstruction: String, includeSystemApps: Boolean = false): String {
//        return withContext(Dispatchers.IO) {
//            try {
//                val allApps = if (includeSystemApps) getAllApps() else getUserApps()
//                println("All apps:")
//                for(i in allApps){
//                    println(i)
//                }
//                if (allApps.isEmpty()) {
//                    return@withContext "No apps found on this device."
//                }
//
//                val appsData = allApps.joinToString("\n") { app ->
//                    "Application Name: \"${app.appName}\", Package Name: \"${app.packageName}\""
//                }
//
//                val prompt = """
//                    Based on the user's instruction, analyze the following list of applications.
//                    If the user is asking to open or interact with a specific app, return only the line for that single application.
//                    If the user's instruction is general and doesn't mention a specific app, return the entire list unchanged.
//
//                    USER INSTRUCTION:
//                    "$userInstruction"
//
//                    FULL APP LIST:
//                    $appsData
//                """.trimIndent()
//
//                Log.d(TAG, "Sending request to Gemini API for instruction: $userInstruction")
//
////                val response = GeminiApi.generateContent(
////                    prompt = prompt,
////                    context = context
////                )
//
////                if (response.isNullOrEmpty()) {
////                    Log.e(TAG, "Gemini API returned null or empty response")
////                    return@withContext "Error: Could not process app data. Please try again."
////                }
////
////                Log.d(TAG, "Gemini API response received: ${response.length} characters")
////
////                val cleanedResponse = response.trim()
////                    .removePrefix("```")
////                    .removeSuffix("```")
////                    .trim()
//
//                return@withContext cleanedResponse
//
//            } catch (e: Exception) {
//                Log.e(TAG, "Error getting apps for instruction: $userInstruction", e)
//                return@withContext "Error: Failed to process app data. ${e.message}"
//            }
//        }
//    }
}

