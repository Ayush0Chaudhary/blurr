/**
 * @file CreateTriggerActivity.kt
 * @brief Defines the activity for creating and editing triggers.
 *
 * This file contains the implementation for `CreateTriggerActivity`, which provides the user
 * interface for configuring all types of triggers. It handles both the creation of new triggers
 * and the editing of existing ones.
 */
package com.blurr.voice.triggers.ui

import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TimePicker
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.blurr.voice.R
import com.blurr.voice.triggers.Trigger
import com.blurr.voice.triggers.TriggerManager
import com.blurr.voice.triggers.TriggerType
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * An [AppCompatActivity] for creating or editing a [Trigger].
 *
 * This activity displays a dynamic UI based on the [TriggerType] passed to it. It allows
 * the user to set the trigger's instruction and configure its specific parameters, such as
 * time, application, or charging state. It handles both "create" and "edit" modes.
 */
class CreateTriggerActivity : AppCompatActivity() {

    /** Manages all trigger data and scheduling. */
    private lateinit var triggerManager: TriggerManager
    /** Input field for the trigger's instruction. */
    private lateinit var instructionEditText: EditText
    /** Input field for searching the app list. */
    private lateinit var searchEditText: EditText
    /** Layout container for time-based trigger options. */
    private lateinit var scheduledTimeOptions: LinearLayout
    /** Layout container for notification-based trigger options. */
    private lateinit var notificationOptions: LinearLayout
    /** Layout container for charging-state-based trigger options. */
    private lateinit var chargingStateOptions: LinearLayout
    /** The UI widget for selecting a time. */
    private lateinit var timePicker: TimePicker
    /** The RecyclerView for displaying the list of installed applications. */
    private lateinit var appsRecyclerView: RecyclerView
    /** The ChipGroup for selecting days of the week. */
    private lateinit var dayOfWeekChipGroup: com.google.android.material.chip.ChipGroup
    /** The adapter for the apps RecyclerView. */
    private lateinit var appAdapter: AppAdapter
    /** The CheckBox for selecting all applications for a notification trigger. */
    private lateinit var selectAllAppsCheckbox: CheckBox

    /** The type of trigger being created or edited. */
    private var selectedTriggerType = TriggerType.SCHEDULED_TIME
    /** The list of apps selected for a notification trigger. */
    private var selectedApps = listOf<AppInfo>()
    /** The trigger being edited, or null if creating a new one. */
    private var existingTrigger: Trigger? = null

    /**
     * Called when the activity is first created.
     *
     * Initializes the UI, determines if the activity is in "create" or "edit" mode,
     * sets up the appropriate views, and wires up event listeners.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_trigger)

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        triggerManager = TriggerManager.getInstance(this)
        instructionEditText = findViewById(R.id.instructionEditText)
        searchEditText = findViewById(R.id.searchEditText)
        scheduledTimeOptions = findViewById(R.id.scheduledTimeOptions)
        notificationOptions = findViewById(R.id.notificationOptions)
        chargingStateOptions = findViewById(R.id.chargingStateOptions)
        timePicker = findViewById(R.id.timePicker)
        appsRecyclerView = findViewById(R.id.appsRecyclerView)
        dayOfWeekChipGroup = findViewById(R.id.dayOfWeekChipGroup)

        val saveButton = findViewById<Button>(R.id.saveTriggerButton)

        val triggerId = intent.getStringExtra("EXTRA_TRIGGER_ID")
        if (triggerId != null) {
            // Edit mode: Load the existing trigger data.
            lifecycleScope.launch {
                existingTrigger = withContext(Dispatchers.IO) {
                    triggerManager.getTriggers().find { it.id == triggerId }
                }
                if (existingTrigger != null) {
                    selectedTriggerType = existingTrigger!!.type
                    populateUiWithTriggerData(existingTrigger!!)
                    saveButton.text = "Update Trigger"
                } else {
                    Toast.makeText(this@CreateTriggerActivity, "Trigger not found.", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        } else {
            // Create mode: Get the type from the intent.
            selectedTriggerType = intent.getSerializableExtra("EXTRA_TRIGGER_TYPE") as TriggerType
            for (i in 0 until dayOfWeekChipGroup.childCount) {
                (dayOfWeekChipGroup.getChildAt(i) as com.google.android.material.chip.Chip).isChecked = true
            }
        }

        setupInitialView()
        setupRecyclerView()
        loadApps()

        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                appAdapter.filter.filter(s)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        selectAllAppsCheckbox = findViewById(R.id.selectAllAppsCheckbox)
        selectAllAppsCheckbox.setOnCheckedChangeListener { _, isChecked ->
            appsRecyclerView.isEnabled = !isChecked
            appsRecyclerView.alpha = if (isChecked) 0.5f else 1.0f
            if (isChecked) {
                appAdapter.setSelectedApps(emptyList())
                selectedApps = emptyList()
            }
        }

        saveButton.setOnClickListener {
            saveTrigger()
        }

        val testButton = findViewById<Button>(R.id.testTriggerButton)
        testButton.setOnClickListener {
            testTrigger()
        }
    }

    /**
     * Executes the current instruction immediately for testing purposes.
     */
    private fun testTrigger() {
        val instruction = instructionEditText.text.toString()
        if (instruction.isBlank()) {
            Toast.makeText(this, "Instruction cannot be empty", Toast.LENGTH_SHORT).show()
            return
        }

        triggerManager.executeInstruction(instruction)
        Toast.makeText(this, "Test trigger fired!", Toast.LENGTH_SHORT).show()
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
     * Fills the UI fields with the data from an existing trigger when in edit mode.
     * @param trigger The [Trigger] to populate the UI with.
     */
    private fun populateUiWithTriggerData(trigger: Trigger) {
        instructionEditText.setText(trigger.instruction)

        when (trigger.type) {
            TriggerType.SCHEDULED_TIME -> {
                timePicker.hour = trigger.hour ?: 0
                timePicker.minute = trigger.minute ?: 0
                for (i in 0 until dayOfWeekChipGroup.childCount) {
                    (dayOfWeekChipGroup.getChildAt(i) as com.google.android.material.chip.Chip).isChecked = false
                }
                trigger.daysOfWeek.forEach { day ->
                    (dayOfWeekChipGroup.getChildAt(day - 1) as com.google.android.material.chip.Chip).isChecked = true
                }
            }
            TriggerType.NOTIFICATION -> {
                if (trigger.packageName == "*") {
                    selectAllAppsCheckbox.isChecked = true
                }
            }
            TriggerType.CHARGING_STATE -> {
                val radioGroup = findViewById<RadioGroup>(R.id.chargingStatusRadioGroup)
                if (trigger.chargingStatus == "Connected") {
                    radioGroup.check(R.id.radioConnected)
                } else {
                    radioGroup.check(R.id.radioDisconnected)
                }
            }
        }
    }

    /**
     * Shows or hides the specific option layouts based on the selected trigger type.
     */
    private fun setupInitialView() {
        when (selectedTriggerType) {
            TriggerType.SCHEDULED_TIME -> {
                scheduledTimeOptions.visibility = View.VISIBLE
                notificationOptions.visibility = View.GONE
                chargingStateOptions.visibility = View.GONE
            }
            TriggerType.NOTIFICATION -> {
                scheduledTimeOptions.visibility = View.GONE
                notificationOptions.visibility = View.VISIBLE
                chargingStateOptions.visibility = View.GONE
            }
            TriggerType.CHARGING_STATE -> {
                scheduledTimeOptions.visibility = View.GONE
                notificationOptions.visibility = View.GONE
                chargingStateOptions.visibility = View.VISIBLE
            }
        }
    }

    /**
     * Sets up the RecyclerView and its adapter for the app list.
     */
    private fun setupRecyclerView() {
        appsRecyclerView.layoutManager = LinearLayoutManager(this)
        appAdapter = AppAdapter(emptyList()) { apps ->
            selectedApps = apps
        }
        appsRecyclerView.adapter = appAdapter
    }

    /**
     * Asynchronously loads the list of installed applications and populates the adapter.
     */
    private fun loadApps() {
        lifecycleScope.launch(Dispatchers.IO) {
            val pm = packageManager
            val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
                .map {
                    AppInfo(
                        appName = it.loadLabel(pm).toString(),
                        packageName = it.packageName,
                        icon = it.loadIcon(pm)
                    )
                }
                .sortedBy { it.appName }

            withContext(Dispatchers.Main) {
                appAdapter.updateApps(apps)
                if (existingTrigger != null && existingTrigger!!.type == TriggerType.NOTIFICATION) {
                    val selectedPackageNames = existingTrigger!!.packageName?.split(",") ?: emptyList()
                    val preSelectedApps = apps.filter { it.packageName in selectedPackageNames }
                    appAdapter.setSelectedApps(preSelectedApps)
                    selectedApps = preSelectedApps
                }
            }
        }
    }

    /**
     * Gathers data from the UI, creates a [Trigger] object, and saves or updates it.
     */
    private fun saveTrigger() {
        val instruction = instructionEditText.text.toString()
        if (instruction.isBlank()) {
            Toast.makeText(this, "Instruction cannot be empty", Toast.LENGTH_SHORT).show()
            return
        }

        val trigger: Trigger
        when (selectedTriggerType) {
            TriggerType.SCHEDULED_TIME -> {
                if (!com.blurr.voice.triggers.PermissionUtils.canScheduleExactAlarms(this)) {
                    showExactAlarmPermissionDialog()
                    return
                }
                val selectedDays = getSelectedDays()
                if (selectedDays.isEmpty()) {
                    Toast.makeText(this, "Please select at least one day", Toast.LENGTH_SHORT).show()
                    return
                }
                trigger = Trigger(
                    id = existingTrigger?.id ?: UUID.randomUUID().toString(),
                    type = TriggerType.SCHEDULED_TIME,
                    hour = timePicker.hour,
                    minute = timePicker.minute,
                    instruction = instruction,
                    daysOfWeek = selectedDays,
                    isEnabled = existingTrigger?.isEnabled ?: true
                )
            }
            TriggerType.NOTIFICATION -> {
                val packageName: String
                val appName: String
                if (selectAllAppsCheckbox.isChecked) {
                    packageName = "*"
                    appName = "All Applications"
                } else {
                    if (selectedApps.isEmpty()) {
                        Toast.makeText(this, "Please select at least one app", Toast.LENGTH_SHORT).show()
                        return
                    }
                    packageName = selectedApps.joinToString(",") { it.packageName }
                    appName = selectedApps.joinToString(",") { it.appName }
                }

                trigger = Trigger(
                    id = existingTrigger?.id ?: UUID.randomUUID().toString(),
                    type = TriggerType.NOTIFICATION,
                    packageName = packageName,
                    appName = appName,
                    instruction = instruction,
                    isEnabled = existingTrigger?.isEnabled ?: true
                )
            }
            TriggerType.CHARGING_STATE -> {
                val radioGroup = findViewById<RadioGroup>(R.id.chargingStatusRadioGroup)
                val selectedStatus = if (radioGroup.checkedRadioButtonId == R.id.radioConnected) {
                    "Connected"
                } else {
                    "Disconnected"
                }
                trigger = Trigger(
                    id = existingTrigger?.id ?: UUID.randomUUID().toString(),
                    type = TriggerType.CHARGING_STATE,
                    chargingStatus = selectedStatus,
                    instruction = instruction,
                    isEnabled = existingTrigger?.isEnabled ?: true
                )
            }
        }

        if (existingTrigger != null) {
            triggerManager.updateTrigger(trigger)
            Toast.makeText(this, "Trigger updated!", Toast.LENGTH_SHORT).show()
        } else {
            triggerManager.addTrigger(trigger)
            Toast.makeText(this, "Trigger saved!", Toast.LENGTH_SHORT).show()
        }
        finish()
    }

    /**
     * Gets the set of selected days from the day-of-the-week ChipGroup.
     * @return A set of integers representing the selected days (1 for Sunday, etc.).
     */
    private fun getSelectedDays(): Set<Int> {
        val selectedDays = mutableSetOf<Int>()
        for (i in 0 until dayOfWeekChipGroup.childCount) {
            val chip = dayOfWeekChipGroup.getChildAt(i) as com.google.android.material.chip.Chip
            if (chip.isChecked) {
                selectedDays.add(i + 1)
            }
        }
        return selectedDays
    }

    /**
     * Shows a dialog to inform the user that the "Alarms & Reminders" permission is required.
     */
    private fun showExactAlarmPermissionDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Permission Required")
            .setMessage("To schedule tasks at a precise time, Panda needs the 'Alarms & Reminders' permission. Please grant this in the next screen.")
            .setPositiveButton("Grant Permission") { _, _ ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    startActivity(Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
