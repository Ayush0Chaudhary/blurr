package com.blurr.voice.triggers.ui

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.provider.Settings
import android.widget.CheckBox
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.blurr.voice.R
import com.blurr.voice.triggers.TriggerManager
import com.blurr.voice.triggers.TriggerMonitoringService
import com.blurr.voice.triggers.TriggerType
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton

class TriggersActivity : AppCompatActivity() {

    private lateinit var triggerManager: TriggerManager
    private lateinit var triggerAdapter: TriggerAdapter
    private lateinit var enableTriggersCheckbox: CheckBox
    private lateinit var triggersNotWorkingText: TextView
    private lateinit var addTriggerFab: ExtendedFloatingActionButton
    private lateinit var triggersRecyclerView: RecyclerView

    private lateinit var sharedPreferences: SharedPreferences

    companion object {
        const val PREFS_NAME = "TriggerPrefs"
        const val KEY_TRIGGERS_ENABLED = "triggers_enabled"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_triggers)

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        triggerManager = TriggerManager.getInstance(this)
        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        enableTriggersCheckbox = findViewById(R.id.enable_triggers_checkbox)
        triggersNotWorkingText = findViewById(R.id.triggers_not_working_text)
        addTriggerFab = findViewById(R.id.addTriggerFab)
        triggersRecyclerView = findViewById(R.id.triggersRecyclerView)

        setupRecyclerView()
        setupFab()
        setupTriggerControls()
    }

    private fun setupTriggerControls() {
        val triggersEnabled = sharedPreferences.getBoolean(KEY_TRIGGERS_ENABLED, false)
        enableTriggersCheckbox.isChecked = triggersEnabled
        updateUiState(triggersEnabled)

        enableTriggersCheckbox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                showBatteryOptimizationWarning {
                    startTriggerService()
                    sharedPreferences.edit().putBoolean(KEY_TRIGGERS_ENABLED, true).apply()
                    updateUiState(true)
                }
            } else {
                stopTriggerService()
                sharedPreferences.edit().putBoolean(KEY_TRIGGERS_ENABLED, false).apply()
                updateUiState(false)
            }
        }

        triggersNotWorkingText.setOnClickListener {
            showBatteryOptimizationWarning {}
        }
    }

    private fun updateUiState(enabled: Boolean) {
        triggersRecyclerView.alpha = if (enabled) 1.0f else 0.5f
        addTriggerFab.isEnabled = enabled
        triggerAdapter.setInteractionsEnabled(enabled)
    }


    private fun showBatteryOptimizationWarning(onAcknowledge: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle("Vendor Battery Optimization")
            .setMessage("Your vendor might not support Panda background trigger monitoring. To ensure Panda works properly, please disable any kind of battery optimization for the app.")
            .setPositiveButton("OK") { dialog, _ ->
                onAcknowledge()
                dialog.dismiss()
            }
            .show()
    }

    private fun startTriggerService() {
        val serviceIntent = Intent(this, TriggerMonitoringService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    private fun stopTriggerService() {
        val serviceIntent = Intent(this, TriggerMonitoringService::class.java)
        stopService(serviceIntent)
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
        if (hasNotificationTriggers && !com.blurr.voice.triggers.PermissionUtils.isNotificationListenerEnabled(this)) {
            showPermissionDialog()
        }
    }

    private fun showPermissionDialog() {
        val dialog = AlertDialog.Builder(this)
            .setTitle("Permission Required")
            .setMessage("To use notification-based triggers, you need to grant Panda the Notification Listener permission in your system settings.")
            .setPositiveButton("Grant Permission") { _, _ ->
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
            .setNegativeButton("Cancel", null)
            .show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(getColor(R.color.white))
    }

    private fun setupRecyclerView() {
        triggersRecyclerView.layoutManager = LinearLayoutManager(this)
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
        triggersRecyclerView.adapter = triggerAdapter
    }

    private fun showDeleteConfirmationDialog(trigger: com.blurr.voice.triggers.Trigger) {
        val dialog = AlertDialog.Builder(this)
            .setTitle("Delete Trigger")
            .setMessage("Are you sure you want to delete this trigger?")
            .setPositiveButton("Delete") { _, _ ->
                triggerManager.removeTrigger(trigger)
                loadTriggers() // Refresh the list
            }
            .setNegativeButton("Cancel", null)
            .show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(getColor(R.color.white))
    }

    private fun setupFab() {
        addTriggerFab.setOnClickListener {
            startActivity(Intent(this, ChooseTriggerTypeActivity::class.java))
        }
    }

    private fun loadTriggers() {
        val triggers = triggerManager.getTriggers()
        triggerAdapter.updateTriggers(triggers)
    }
}
