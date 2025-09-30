/**
 * @file ChooseTriggerTypeActivity.kt
 * @brief Defines the activity for selecting the type of a new trigger.
 *
 * This file contains the implementation for `ChooseTriggerTypeActivity`, which presents the user
 * with a choice of different trigger types (e.g., time-based, notification-based).
 * Selecting a type navigates the user to the `CreateTriggerActivity`.
 */
package com.blurr.voice.triggers.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.blurr.voice.R
import com.blurr.voice.triggers.TriggerType

/**
 * An [AppCompatActivity] that allows the user to select a type for a new trigger.
 *
 * This screen displays several options corresponding to the [TriggerType] enum.
 * Tapping an option launches the [CreateTriggerActivity], passing the selected
 * trigger type as an intent extra.
 */
class ChooseTriggerTypeActivity : AppCompatActivity() {

    /**
     * Called when the activity is first created.
     * Sets up the content view, toolbar, and click listeners for the trigger type cards.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_choose_trigger_type)

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        findViewById<CardView>(R.id.scheduledTimeCard).setOnClickListener {
            launchCreateTriggerActivity(TriggerType.SCHEDULED_TIME)
        }

        findViewById<CardView>(R.id.notificationCard).setOnClickListener {
            launchCreateTriggerActivity(TriggerType.NOTIFICATION)
        }

        findViewById<CardView>(R.id.chargingStateCard).setOnClickListener {
            launchCreateTriggerActivity(TriggerType.CHARGING_STATE)
        }
    }

    /**
     * Launches the [CreateTriggerActivity] with the selected trigger type.
     * @param triggerType The [TriggerType] chosen by the user.
     */
    private fun launchCreateTriggerActivity(triggerType: TriggerType) {
        val intent = Intent(this, CreateTriggerActivity::class.java).apply {
            putExtra("EXTRA_TRIGGER_TYPE", triggerType)
        }
        startActivity(intent)
    }

    /**
     * Handles the "Up" button navigation in the toolbar.
     * @return `true` to indicate the event was handled.
     */
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}
