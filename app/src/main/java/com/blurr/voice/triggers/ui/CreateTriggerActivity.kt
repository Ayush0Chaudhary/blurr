package com.blurr.voice.triggers.ui

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TimePicker
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
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

class CreateTriggerActivity : AppCompatActivity() {

    private lateinit var triggerManager: TriggerManager
    private lateinit var instructionEditText: EditText
    private lateinit var scheduledTimeOptions: LinearLayout
    private lateinit var notificationOptions: LinearLayout
    private lateinit var chargingStateOptions: LinearLayout
    private lateinit var timePicker: TimePicker
    private lateinit var appsRecyclerView: RecyclerView
    private lateinit var dayOfWeekChipGroup: com.google.android.material.chip.ChipGroup
    private lateinit var appAdapter: AppAdapter
    private lateinit var scrollView: ScrollView
    private lateinit var searchAppEditText: EditText

    private var allApps: List<AppInfo> = emptyList()
    private var selectedTriggerType = TriggerType.SCHEDULED_TIME
    private var selectedApp: AppInfo? = null
    private var existingTrigger: Trigger? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_trigger)

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        triggerManager = TriggerManager.getInstance(this)
        instructionEditText = findViewById(R.id.instructionEditText)
        scheduledTimeOptions = findViewById(R.id.scheduledTimeOptions)
        notificationOptions = findViewById(R.id.notificationOptions)
        chargingStateOptions = findViewById(R.id.chargingStateOptions)
        timePicker = findViewById(R.id.timePicker)
        appsRecyclerView = findViewById(R.id.appsRecyclerView)
        dayOfWeekChipGroup = findViewById(R.id.dayOfWeekChipGroup)
        searchAppEditText = findViewById(R.id.searchAppEditText)
//        scrollView = findViewById(R.id.scrollView)

//        instructionEditText.setOnFocusChangeListener { view, hasFocus ->
//            if (hasFocus) {
//                // Delay scrolling until the keyboard is likely to be visible
//                view.postDelayed({
//                    scrollView.smoothScrollTo(0, view.bottom)
//                }, 200)
//            }
//        }

        val saveButton = findViewById<Button>(R.id.saveTriggerButton)

        val triggerId = intent.getStringExtra("EXTRA_TRIGGER_ID")
        if (triggerId != null) {
            // Edit mode
            existingTrigger = triggerManager.getTriggers().find { it.id == triggerId }
            if (existingTrigger != null) {
                selectedTriggerType = existingTrigger!!.type
                populateUiWithTriggerData(existingTrigger!!)
                saveButton.text = "Update Trigger"
            } else {
                // Trigger not found, something is wrong.
                Toast.makeText(this, "Trigger not found.", Toast.LENGTH_SHORT).show()
                finish()
                return // return from onCreate
            }
        } else {
            // Create mode
            selectedTriggerType = intent.getSerializableExtra("EXTRA_TRIGGER_TYPE") as TriggerType
            // Set default checked state for all day chips
            for (i in 0 until dayOfWeekChipGroup.childCount) {
                (dayOfWeekChipGroup.getChildAt(i) as com.google.android.material.chip.Chip).isChecked = true
            }
        }


        setupInitialView()

        setupRecyclerView()
        loadApps()

        searchAppEditText.addTextChangedListener { text ->
            val query = text.toString()
            filterApps(query)
        }

        saveButton.setOnClickListener {
            saveTrigger()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    private fun populateUiWithTriggerData(trigger: Trigger) {
        instructionEditText.setText(trigger.instruction)

        when (trigger.type) {
            TriggerType.SCHEDULED_TIME -> {
                timePicker.hour = trigger.hour ?: 0
                timePicker.minute = trigger.minute ?: 0
                // Clear all chips first
                for (i in 0 until dayOfWeekChipGroup.childCount) {
                    (dayOfWeekChipGroup.getChildAt(i) as com.google.android.material.chip.Chip).isChecked = false
                }
                // Then check the ones from the trigger
                trigger.daysOfWeek.forEach { day ->
                    (dayOfWeekChipGroup.getChildAt(day - 1) as com.google.android.material.chip.Chip).isChecked = true
                }
            }
            TriggerType.NOTIFICATION -> {
                selectedApp = AppInfo(
                    appName = trigger.appName ?: "",
                    packageName = trigger.packageName ?: ""
                )
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

    private fun setupRecyclerView() {
        appsRecyclerView.layoutManager = LinearLayoutManager(this)
        appAdapter = AppAdapter(emptyList()) { app ->
            selectedApp = app
        }
        appsRecyclerView.adapter = appAdapter
    }

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
            allApps = apps

            withContext(Dispatchers.Main) {
                appAdapter.updateApps(apps)
                if (existingTrigger != null && existingTrigger!!.type == TriggerType.NOTIFICATION) {
                    val position = apps.indexOfFirst { it.packageName == existingTrigger!!.packageName }
                    if (position != -1) {
                        appAdapter.setSelectedPosition(position)
                        appsRecyclerView.scrollToPosition(position)
                        selectedApp = apps[position]
                    }
                }
            }
        }
    }

    private fun filterApps(query: String) {
        val filteredList = if (query.isEmpty()) {
            allApps
        } else {
            allApps.filter {
                it.appName.contains(query, ignoreCase = true)
            }
        }
        appAdapter.updateApps(filteredList)
    }

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
                if (selectedApp == null) {
                    Toast.makeText(this, "Please select an app", Toast.LENGTH_SHORT).show()
                    return
                }
                trigger = Trigger(
                    id = existingTrigger?.id ?: UUID.randomUUID().toString(),
                    type = TriggerType.NOTIFICATION,
                    packageName = selectedApp!!.packageName,
                    appName = selectedApp!!.appName,
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

    private fun getSelectedDays(): Set<Int> {
        val selectedDays = mutableSetOf<Int>()
        for (i in 0 until dayOfWeekChipGroup.childCount) {
            val chip = dayOfWeekChipGroup.getChildAt(i) as com.google.android.material.chip.Chip
            if (chip.isChecked) {
                // Mapping index to Calendar.DAY_OF_WEEK constants (Sunday=1, Monday=2, etc.)
                selectedDays.add(i + 1)
            }
        }
        return selectedDays
    }

    private fun showExactAlarmPermissionDialog() {
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Permission Required")
            .setMessage("To schedule tasks at a precise time, Panda needs the 'Alarms & Reminders' permission. Please grant this in the next screen.")
            .setPositiveButton("Grant Permission") { _, _ ->
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    startActivity(android.content.Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                }
            }
            .setNegativeButton("Cancel", null)
            .show()

        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setTextColor(getColor(R.color.white))
    }
}
