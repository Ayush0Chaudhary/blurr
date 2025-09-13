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
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.blurr.voice.R
import com.blurr.voice.triggers.Trigger
import com.blurr.voice.triggers.TriggerManager
import com.blurr.voice.triggers.TriggerType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CreateTriggerActivity : AppCompatActivity() {

    private lateinit var triggerManager: TriggerManager
    private lateinit var instructionEditText: EditText
    private lateinit var scheduledTimeOptions: LinearLayout
    private lateinit var notificationOptions: LinearLayout
    private lateinit var timePicker: TimePicker
    private lateinit var appsRecyclerView: RecyclerView
    private lateinit var dayOfWeekChipGroup: com.google.android.material.chip.ChipGroup
    private lateinit var appAdapter: AppAdapter
    private lateinit var scrollView: ScrollView

    private var selectedTriggerType = TriggerType.SCHEDULED_TIME
    private var selectedApp: AppInfo? = null

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
        timePicker = findViewById(R.id.timePicker)
        appsRecyclerView = findViewById(R.id.appsRecyclerView)
        dayOfWeekChipGroup = findViewById(R.id.dayOfWeekChipGroup)
        scrollView = findViewById(R.id.scrollView)

        instructionEditText.setOnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                // Delay scrolling until the keyboard is likely to be visible
                view.postDelayed({
                    scrollView.smoothScrollTo(0, view.bottom)
                }, 200)
            }
        }

        // Set default checked state for all day chips
        for (i in 0 until dayOfWeekChipGroup.childCount) {
            (dayOfWeekChipGroup.getChildAt(i) as com.google.android.material.chip.Chip).isChecked = true
        }

        selectedTriggerType = intent.getSerializableExtra("EXTRA_TRIGGER_TYPE") as TriggerType
        setupInitialView()

        setupRecyclerView()
        loadApps()

        val saveButton = findViewById<Button>(R.id.saveTriggerButton)
        saveButton.setOnClickListener {
            saveTrigger()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    private fun setupInitialView() {
        when (selectedTriggerType) {
            TriggerType.SCHEDULED_TIME -> {
                scheduledTimeOptions.visibility = View.VISIBLE
                notificationOptions.visibility = View.GONE
            }
            TriggerType.NOTIFICATION -> {
                scheduledTimeOptions.visibility = View.GONE
                notificationOptions.visibility = View.VISIBLE
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

            withContext(Dispatchers.Main) {
                appAdapter = AppAdapter(apps) { app ->
                    selectedApp = app
                }
                appsRecyclerView.adapter = appAdapter
            }
        }
    }

    private fun saveTrigger() {
        val instruction = instructionEditText.text.toString()
        if (instruction.isBlank()) {
            Toast.makeText(this, "Instruction cannot be empty", Toast.LENGTH_SHORT).show()
            return
        }

        val trigger = when (selectedTriggerType) {
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
                Trigger(
                    type = TriggerType.SCHEDULED_TIME,
                    hour = timePicker.hour,
                    minute = timePicker.minute,
                    instruction = instruction,
                    daysOfWeek = selectedDays
                )
            }
            TriggerType.NOTIFICATION -> {
                if (selectedApp == null) {
                    Toast.makeText(this, "Please select an app", Toast.LENGTH_SHORT).show()
                    return
                }
                Trigger(
                    type = TriggerType.NOTIFICATION,
                    packageName = selectedApp!!.packageName,
                    appName = selectedApp!!.appName,
                    instruction = instruction
                )
            }
        }

        triggerManager.addTrigger(trigger)
        Toast.makeText(this, "Trigger saved!", Toast.LENGTH_SHORT).show()
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
