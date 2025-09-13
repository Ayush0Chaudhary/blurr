package com.blurr.voice.intents

import android.content.Context
import android.util.Log
import com.blurr.voice.intents.impl.DialIntent
import com.blurr.voice.intents.impl.EmailComposeIntent
import com.blurr.voice.intents.impl.ShareTextIntent
import com.blurr.voice.intents.impl.ViewUrlIntent
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.os.Build
import androidx.annotation.RequiresApi
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager

/**
 * Discovers and manages AppIntent implementations.
 * Convention: Put intent implementations under package com.blurr.voice.intents.impl
 */
object IntentRegistry {
    private const val TAG = "IntentRegistry"

    private val discovered: MutableMap<String, AppIntent> = linkedMapOf()
    @Volatile private var initialized = false

    @Synchronized
    @Suppress("UNUSED_PARAMETER")
    fun init(context: Context) {
        register(DialIntent())
        register(ViewUrlIntent())
        register(ShareTextIntent())
        register(EmailComposeIntent())
        initialized = true
    }
    fun register(intent: AppIntent) {
        val key = intent.name.trim()
        if (discovered.containsKey(key)) {
            Log.w(TAG, "Duplicate intent registration for name: ${intent.name}; overriding")
        }
        discovered[key] = intent
    }

    fun listIntents(context: Context): List<AppIntent> {
        if (!initialized) init(context)
        return discovered.values.toList()
    }

    fun findByName(context: Context, name: String): AppIntent? {
        if (!initialized) init(context)
        // exact match first, then case-insensitive
        discovered[name]?.let { return it }
        return discovered.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
    }

    /**
     * Generates a formatted string describing the app's internal intents.
     */
    fun getFormattedIntentsContext(context: Context): String {
        val intents = listIntents(context)
        if (intents.isEmpty()) return ""

        return buildString {
            append("Available App Intents:\n")
            intents.forEach { intent ->
                append("- Intent: ${intent.name}\n")
                append("  Description: ${intent.description()}\n")
                val params = intent.parametersSpec()
                if (params.isNotEmpty()) {
                    append("  Parameters:\n")
                    params.forEach { p ->
                        append("    - ${p.name} (${p.type}): ${p.description} (Required: ${p.required})\n")
                    }
                }
            }
        }
    }

    /**
     * Aggregates output from internal intents, shortcut scanner, and share target scanner.
     */
    fun getComprehensiveFormattedIntents(context: Context): String {
        val internalIntents = getFormattedIntentsContext(context)
        val shortcuts = ShortcutScanner.getFormattedShortcuts(context)
        val shareTargets = ShareIntentScanner.getFormattedShareTargets(context)

        return buildString {
            if (internalIntents.isNotBlank()) {
                append(internalIntents)
                append("\n")
            }
            if (shortcuts.isNotBlank()) {
                append(shortcuts)
                append("\n")
            }
            if (shareTargets.isNotBlank()) {
                append(shareTargets)
            }
        }.trim()
    }
}

@RequiresApi(Build.VERSION_CODES.N_MR1)
private object ShortcutScanner {

    fun getShortcuts(context: Context): List<ShortcutInfo> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) {
            return emptyList()
        }

        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val query = LauncherApps.ShortcutQuery().apply {
            setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST)
        }

        return try {
            launcherApps.getShortcuts(query, android.os.Process.myUserHandle()) ?: emptyList()
        } catch (e: SecurityException) {
            Log.e("ShortcutScanner", "Permission denied when querying shortcuts.", e)
            emptyList()
        }
    }

    fun getFormattedShortcuts(context: Context): String {
        val shortcuts = getShortcuts(context)
        if (shortcuts.isEmpty()) {
            return ""
        }

        val builder = StringBuilder("Available App Shortcuts:\n")
        shortcuts.forEach { shortcut ->
            builder.append("- App: ${shortcut.getPackage()}\n")
            builder.append("  Action: ${shortcut.shortLabel}\n")
        }
        return builder.toString()
    }

    fun triggerShortcut(context: Context, shortcut: ShortcutInfo): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) {
            return false
        }

        try {
            val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            launcherApps.startShortcut(shortcut, null, null)
            Log.i("ShortcutScanner", "Successfully launched shortcut: '${shortcut.shortLabel}' from package ${shortcut.getPackage()}")
            return true
        } catch (e: SecurityException) {
            Log.e("ShortcutScanner", "PERMISSION_DENIED to start shortcut: ${shortcut.shortLabel}. Is this app the default assistant?", e)
        } catch (e: ActivityNotFoundException) {
            Log.e("ShortcutScanner", "Activity not found for shortcut: ${shortcut.shortLabel}. The app may have been updated or the shortcut is stale.", e)
        } catch (e: IllegalStateException) {
            Log.e("ShortcutScanner", "Could not launch shortcut due to invalid device state (e.g., locked): ${shortcut.shortLabel}", e)
        } catch (e: Exception) {
            Log.e("ShortcutScanner", "An unexpected error occurred while launching shortcut: ${shortcut.shortLabel}", e)
        }
        return false
    }
}

@RequiresApi(Build.VERSION_CODES.N_MR1)
private object ShareIntentScanner {

    fun getFormattedShareTargets(context: Context): String {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
        }

        val resolveInfoList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }

        if (resolveInfoList.isEmpty()) {
            return ""
        }

        val builder = StringBuilder("Apps that can share text:\n")
        resolveInfoList.forEach { resolveInfo ->
            val appName = resolveInfo.loadLabel(pm)
            val packageName = resolveInfo.activityInfo.packageName
            builder.append("- App: $appName ($packageName)\n")
        }
        return builder.toString()
    }
}
