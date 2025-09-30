/**
 * @file TriggersActivity.kt
 * @brief Defines the main activity for viewing and managing triggers.
 *
 * This file contains the implementation for `TriggersActivity`, which serves as the main screen
 * for the triggers feature. It displays a list of all created triggers, allows users to
 * add new ones, and handles the necessary permission checks.
 */
package com.blurr.voice.triggers.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import android.provider.Settings
import androidx.recyclerview.widget.RecyclerView
import com.blurr.voice.R
import com.blurr.voice.triggers.TriggerManager
import com.blurr.voice.triggers.TriggerType
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton

/**
 * The main [AppCompatActivity] for managing triggers.
 *
 * This screen displays a list of all user-created triggers using a [RecyclerView].
 * It provides functionality to add, edit, and delete triggers, and it also handles
 * runtime permission checks required for certain trigger types.
 */
class TriggersActivity : AppCompatActivity() {

    /** The manager for accessing and modifying trigger data. */
    private lateinit var triggerManager: TriggerManager
    /** The adapter for the RecyclerView that displays the list of triggers. */
    private lateinit var triggerAdapter: TriggerAdapter

    /**
     * Called when the activity is first created.
     * Initializes the toolbar, trigger manager, RecyclerView, and Floating Action Button.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_triggers)

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)


        triggerManager = TriggerManager.getInstance(this)

        setupRecyclerView()
        setupFab()
    }

    /**
     * Handles the "Up" button navigation in the toolbar.
     * @return `true` to indicate the event was handled.
     */
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    /**
     * Called when the activity will start interacting with the user.
     * Reloads the triggers to reflect any changes and checks for necessary permissions.
     */
    override fun onResume() {
        super.onResume()
        loadTriggers()
        checkNotificationPermission()
    }

    /**
     * Checks if any enabled notification triggers exist and if the required
     * Notification Listener permission has been granted. If not, it prompts the user.
     */
    private fun checkNotificationPermission() {
        val hasNotificationTriggers = triggerManager.getTriggers().any { it.type == TriggerType.NOTIFICATION && it.isEnabled }
        if (hasNotificationTriggers && !com.blurr.voice.triggers.PermissionUtils.isNotificationListenerEnabled(this)) {
            showPermissionDialog()
        }
    }

    /**
     * Displays a dialog to inform the user why the Notification Listener permission is needed
     * and provides a button to open the relevant system settings screen.
     */
    private fun showPermissionDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Permission Required")
            .setMessage("To use notification-based triggers, you need to grant Panda the Notification Listener permission in your system settings.")
            .setPositiveButton("Grant Permission") { _, _ ->
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Sets up the RecyclerView, its LayoutManager, and the [TriggerAdapter].
     * The adapter is configured with callbacks to handle editing, deleting, and toggling triggers.
     */
    private fun setupRecyclerView() {
        val recyclerView = findViewById<RecyclerView>(R.id.triggersRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        triggerAdapter = TriggerAdapter(
            mutableListOf(),
            onCheckedChange = { trigger, isEnabled ->
                trigger.isEnabled = isEnabled
                triggerManager.updateTrigger(trigger)
            },
            onDeleteClick = { trigger ->
                showDeleteConfirmationDialog(trigger)
            },
            onEditClick = { trigger ->
                val intent = Intent(this, CreateTriggerActivity::class.java).apply {
                    putExtra("EXTRA_TRIGGER_ID", trigger.id)
                }
                startActivity(intent)
            }
        )
        recyclerView.adapter = triggerAdapter
    }

    /**
     * Shows a confirmation dialog before deleting a trigger.
     * @param trigger The [com.blurr.voice.triggers.Trigger] to be deleted.
     */
    private fun showDeleteConfirmationDialog(trigger: com.blurr.voice.triggers.Trigger) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Delete Trigger")
            .setMessage("Are you sure you want to delete this trigger?")
            .setPositiveButton("Delete") { _, _ ->
                triggerManager.removeTrigger(trigger)
                loadTriggers() // Refresh the list
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Sets up the Floating Action Button and its click listener to navigate to the
     * [ChooseTriggerTypeActivity].
     */
    private fun setupFab() {
        val fab = findViewById<ExtendedFloatingActionButton>(R.id.addTriggerFab)
        fab.setOnClickListener {
            startActivity(Intent(this, ChooseTriggerTypeActivity::class.java))
        }
    }

    /**
     * Loads the list of triggers from the [TriggerManager] and updates the adapter.
     */
    private fun loadTriggers() {
        val triggers = triggerManager.getTriggers()
        triggerAdapter.updateTriggers(triggers)
    }
}
