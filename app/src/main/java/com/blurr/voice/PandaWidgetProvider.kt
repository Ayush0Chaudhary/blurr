/**
 * @file PandaWidgetProvider.kt
 * @brief Defines the AppWidgetProvider for the Panda home screen widget.
 *
 * This file contains the logic for the app's home screen widget, which allows the user
 * to activate the assistant with a single tap from their home screen.
 */
package com.blurr.voice

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews

/**
 * The `AppWidgetProvider` for the Panda home screen widget.
 *
 * This class handles the widget's lifecycle events. Its primary responsibility is to
 * configure the widget's layout and set up a click listener that launches the main
 * conversational service.
 */
class PandaWidgetProvider : AppWidgetProvider() {

    /**
     * Called by the system to update the widget's view.
     *
     * This method is called when the widget is first placed on the home screen and at
     * regular update intervals. It configures a `PendingIntent` that sends the
     * `WAKE_UP_PANDA` action to the `MainActivity`. This intent is then attached as a
     * click listener to the widget's root layout.
     *
     * @param context The application context.
     * @param appWidgetManager The widget manager instance.
     * @param appWidgetIds The IDs of the widget instances to update.
     */
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // Perform this setup for each widget instance.
        for (appWidgetId in appWidgetIds) {
            // Create an Intent to launch MainActivity with the custom "wake up" action.
            val intent = Intent(context, MainActivity::class.java).apply {
                action = "com.blurr.voice.WAKE_UP_PANDA"
            }

            // Create a PendingIntent that will be triggered when the widget is clicked.
            val pendingIntent = PendingIntent.getActivity(
                context,
                0, // A request code for this pending intent.
                intent,
                // Use FLAG_IMMUTABLE for security on modern Android versions.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }
            )

            // Get the layout for the App Widget and attach the on-click listener.
            val views = RemoteViews(context.packageName, R.layout.panda_widget_layout)
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

            // Instruct the AppWidgetManager to update the widget.
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}