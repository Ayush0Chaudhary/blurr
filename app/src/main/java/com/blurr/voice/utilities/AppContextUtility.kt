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
    }


