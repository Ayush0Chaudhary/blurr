package com.blurr.voice.utilities

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log


// TODO This fiel is not used anywhere but we still keep it because
//  this implement a agent that adds apps in the context of the agent which can be used for multiple app tasks
data class AppInfo(
    val appName: String,
    val packageName: String,
    val isSystemApp: Boolean,
    val isEnabled: Boolean,
    val versionName: String? = null,
    val versionCode: Long = 0
)

class AppContextUtility(private val context: Context) {

    companion object {
        private const val TAG = "AppListUtility"
    }

    /**
     * Get all installed applications with basic information
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
     * Get only user-installed applications (non-system apps)
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
     * MAIN FUNCTION: Uses Gemini API to intelligently filter and format app data based on user instruction
     * This avoids using large context windows by letting AI determine what's relevant
     */
//    suspend fun getAppsForInstruction(userInstruction: String, includeSystemApps: Boolean = false): String {
//        return withContext(Dispatchers.IO) {
//            try {
//                // Get all apps data
//                val allApps = if (includeSystemApps) getAllApps() else getUserApps()
//                println("All apps:")
//                for(i in allApps){
//                    println(i)
//                }
//                if (allApps.isEmpty()) {
//                    return@withContext "No apps found on this device."
//                }
//
//                // Create the apps data string
//                val appsData = allApps.joinToString("\n") { app ->
//                    "Application Name: \"${app.appName}\", Package Name: \"${app.packageName}\""
//                }
//
//                // Create the prompt for Gemini API
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
//                // Call Gemini API
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
////                // Clean up the response - remove any markdown formatting or extra text
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

