package com.blurr.voice.triggers.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.blurr.voice.R
import com.blurr.voice.triggers.PermissionUtils
import com.blurr.voice.triggers.TriggerManager
import com.blurr.voice.triggers.TriggerType
import com.google.android.material.floatingactionbutton.FloatingActionButton

class TriggersActivity : AppCompatActivity() {

    private lateinit var triggerManager: TriggerManager
    private lateinit var triggerAdapter: TriggerAdapter

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

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    override fun onResume() {
        super.onResume()
        loadTriggers()
        checkNotificationPermission()
    }

    private fun checkNotificationPermission() {
        val hasNotificationTriggers = triggerManager.getTriggers().any { it.type == TriggerType.NOTIFICATION && it.isEnabled }
        if (hasNotificationTriggers && !PermissionUtils.isNotificationListenerEnabled(this)) {
            showPermissionDialog()
        }
    }

    private fun showPermissionDialog() {
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Permission Required")
            .setMessage("To use notification-based triggers, you need to grant Panda the Notification Listener permission in your system settings.")
            .setPositiveButton("Grant Permission") { _, _ ->
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
            .setNegativeButton("Cancel", null)
            .show()

        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setTextColor(getColor(R.color.white))
    }

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
            }
        )
        recyclerView.adapter = triggerAdapter
    }

    private fun showDeleteConfirmationDialog(trigger: com.blurr.voice.triggers.Trigger) {
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Delete Trigger")
            .setMessage("Are you sure you want to delete this trigger?")
            .setPositiveButton("Delete") { _, _ ->
                triggerManager.removeTrigger(trigger)
                loadTriggers()
            }
            .setNegativeButton("Cancel", null)
            .show()

        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setTextColor(getColor(R.color.white))
    }

    private fun setupFab() {
        val fab = findViewById<FloatingActionButton>(R.id.addTriggerFab)
        fab.setOnClickListener {
            startActivity(Intent(this, ChooseTriggerTypeActivity::class.java))
        }
    }

    private fun loadTriggers() {
        val triggers = triggerManager.getTriggers()
        triggerAdapter.updateTriggers(triggers)
    }
}
