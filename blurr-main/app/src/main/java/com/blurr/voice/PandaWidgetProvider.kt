package com.blurr.voice

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews

class PandaWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // Perform this loop procedure for each widget that belongs to this provider
        for (appWidgetId in appWidgetIds) {
            // Create an Intent to launch MainActivity with our custom action.
            // THIS IS THE SAME ACTION WE USED FOR THE APP SHORTCUT!
            val intent = Intent(context, MainActivity::class.java)
            intent.action = "com.blurr.voice.WAKE_UP_PANDA"

            // Create a PendingIntent that will be triggered when the widget is clicked.
            val pendingIntent = PendingIntent.getActivity(
                context,
                0, // A request code for this pending intent
                intent,
                // Use FLAG_IMMUTABLE for security on modern Android versions
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }
            )

            // Get the layout for the App Widget and attach an on-click listener
            val views = RemoteViews(context.packageName, R.layout.panda_widget_layout)
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

            // Tell the AppWidgetManager to perform an update on the current app widget
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}