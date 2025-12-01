package com.blurr.voice

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.NumberPicker
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.blurr.voice.api.GoogleTts
import com.blurr.voice.api.PicovoiceKeyManager
import com.blurr.voice.api.TTSVoice
import com.blurr.voice.utilities.SpeechCoordinator
import com.blurr.voice.utilities.VoicePreferenceManager
import com.blurr.voice.utilities.UserProfileManager
import com.blurr.voice.utilities.WakeWordManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import com.blurr.voice.data.CactusEmbeddingManager

class SettingsActivity : BaseNavigationActivity() {

    private lateinit var ttsVoicePicker: NumberPicker
    private lateinit var permissionsInfoButton: TextView
    private lateinit var batteryOptimizationHelpButton: TextView
    private lateinit var appVersionText: TextView
    private lateinit var editUserName: android.widget.EditText
    private lateinit var editUserEmail: android.widget.EditText
    private lateinit var editWakeWordKey: android.widget.EditText
    private lateinit var textGetPicovoiceKeyLink: TextView
    private lateinit var wakeWordButton: TextView
    private lateinit var buttonSignOut: Button
    private lateinit var checkboxUseLocalModel: android.widget.CheckBox
    private lateinit var manageMemoriesButton: TextView
    private lateinit var wakeWordManager: WakeWordManager
    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String>


    private lateinit var sc: SpeechCoordinator
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var availableVoices: List<TTSVoice>
    private var voiceTestJob: Job? = null

    companion object {
        private const val PREFS_NAME = "BlurrSettings"
        private const val KEY_SELECTED_VOICE = "selected_voice"
        private const val TEST_TEXT = "Hello, I'm Panda, and this is a test of the selected voice."
        private val DEFAULT_VOICE = TTSVoice.CHIRP_PUCK
        const val KEY_USE_LOCAL_MODEL = "use_local_model"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // Initialize permission launcher first
        requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Toast.makeText(this, "Permission granted!", Toast.LENGTH_SHORT).show()
                // The manager will handle the service start after permission is granted.
                wakeWordManager.handleWakeWordButtonClick(wakeWordButton)
                updateWakeWordButtonState()
            } else {
                Toast.makeText(this, "Permission denied.", Toast.LENGTH_SHORT).show()
            }
        }

        initialize()
        setupUI()
        loadAllSettings()
        setupAutoSavingListeners()
        cacheVoiceSamples()
    }

    override fun onStop() {
        super.onStop()
        // Stop any lingering voice tests when the user leaves the screen
        sc.stop()
        voiceTestJob?.cancel()
    }

    private fun initialize() {
        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sc = SpeechCoordinator.getInstance(this)
        availableVoices = GoogleTts.getAvailableVoices()
        // Initialize wake word manager
        wakeWordManager = WakeWordManager(this, requestPermissionLauncher)
    }

    private fun setupUI() {
        ttsVoicePicker = findViewById(R.id.ttsVoicePicker)
        permissionsInfoButton = findViewById(R.id.permissionsInfoButton)
        appVersionText = findViewById(R.id.appVersionText)
        batteryOptimizationHelpButton = findViewById(R.id.batteryOptimizationHelpButton)
      
        editWakeWordKey = findViewById(R.id.editWakeWordKey)
        wakeWordButton = findViewById(R.id.wakeWordButton)
        editWakeWordKey = findViewById(R.id.editWakeWordKey)
        wakeWordButton = findViewById(R.id.wakeWordButton)
        buttonSignOut = findViewById(R.id.buttonSignOut)
        checkboxUseLocalModel = findViewById(R.id.checkboxUseLocalModel)
        manageMemoriesButton = findViewById(R.id.manageMemoriesButton)

        editUserName = findViewById(R.id.editUserName)
        editUserEmail = findViewById(R.id.editUserEmail)
        textGetPicovoiceKeyLink = findViewById(R.id.textGetPicovoiceKeyLink)


        setupClickListeners()
        setupVoicePicker()

        // Prefill profile fields from saved values
        kotlin.runCatching {
            val pm = UserProfileManager(this)
            editUserName.setText(pm.getName() ?: "")
            editUserEmail.setText(pm.getEmail() ?: "")
        }

        // Show app version
        val versionName = BuildConfig.VERSION_NAME
        appVersionText.text = "Version $versionName"
    }

    private fun setupVoicePicker() {
        val voiceDisplayNames = availableVoices.map { it.displayName }.toTypedArray()
        ttsVoicePicker.minValue = 0
        ttsVoicePicker.maxValue = voiceDisplayNames.size - 1
        ttsVoicePicker.displayedValues = voiceDisplayNames
        ttsVoicePicker.wrapSelectorWheel = false
    }

    private fun setupClickListeners() {
        permissionsInfoButton.setOnClickListener {
            val intent = Intent(this, PermissionsActivity::class.java)
            startActivity(intent)
        }
        batteryOptimizationHelpButton.setOnClickListener {
            showBatteryOptimizationDialog()
        }
        
        checkboxUseLocalModel.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // Check if already downloaded?
                lifecycleScope.launch {
                    val isDownloaded = CactusEmbeddingManager.isModelDownloaded()
                    if (!isDownloaded) {
                        Toast.makeText(this@SettingsActivity, "Downloading local model... This may take a while.", Toast.LENGTH_LONG).show()
                        checkboxUseLocalModel.isEnabled = false // Disable while downloading
                        val success = CactusEmbeddingManager.downloadModel()
                        checkboxUseLocalModel.isEnabled = true
                        if (success) {
                            Toast.makeText(this@SettingsActivity, "Model downloaded successfully!", Toast.LENGTH_SHORT).show()
                            sharedPreferences.edit { putBoolean(KEY_USE_LOCAL_MODEL, true) }
                        } else {
                            Toast.makeText(this@SettingsActivity, "Download failed. Please check your connection.", Toast.LENGTH_SHORT).show()
                            checkboxUseLocalModel.isChecked = false
                        }
                    } else {
                        sharedPreferences.edit { putBoolean(KEY_USE_LOCAL_MODEL, true) }
                    }
                }
            } else {
                sharedPreferences.edit { putBoolean(KEY_USE_LOCAL_MODEL, false) }

            }
        }

        manageMemoriesButton.setOnClickListener {
            val intent = Intent(this, MemoriesActivity::class.java)
            startActivity(intent)
        }

        wakeWordButton.setOnClickListener {
            val keyManager = PicovoiceKeyManager(this)
            
            // Step 1: Save key if provided in the EditText
            val userKey = editWakeWordKey.text.toString().trim()
            if (userKey.isNotEmpty()) {
                keyManager.saveUserProvidedKey(userKey)
                Toast.makeText(this, "Wake word key saved.", Toast.LENGTH_SHORT).show()
            }
            
            // Step 2: Check if we have a key (either just saved or previously saved)
            val hasKey = !keyManager.getUserProvidedKey().isNullOrBlank()
            
            if (!hasKey) {
                showPicovoiceKeyRequiredDialog()
                return@setOnClickListener
            }
            
            // Step 3: Enable the wake word
            wakeWordManager.handleWakeWordButtonClick(wakeWordButton)
            // Give the service a moment to update its state before refreshing the UI
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ updateWakeWordButtonState() }, 500)
        }
        textGetPicovoiceKeyLink.setOnClickListener {
            val url = "https://console.picovoice.ai/login"
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse(url)
            try {
                startActivity(intent)
            } catch (e: Exception) {
                // This might happen if the device has no web browser
                Toast.makeText(this, "Could not open link. No browser found.", Toast.LENGTH_SHORT).show()
                Log.e("SettingsActivity", "Failed to open Picovoice link", e)
            }
        }

        buttonSignOut.setOnClickListener {
            showSignOutConfirmationDialog()
        }
    }

    private fun setupAutoSavingListeners() {
        var isInitialLoad = true

        ttsVoicePicker.setOnValueChangedListener { _, _, newVal ->
            val selectedVoice = availableVoices[newVal]
            saveSelectedVoice(selectedVoice)

            if (!isInitialLoad) {
                voiceTestJob?.cancel()
                voiceTestJob = lifecycleScope.launch {
                    delay(400L)
                    // First, stop any currently playing voice
                    sc.stop()
                    // Then, play the new sample
                    playVoiceSample(selectedVoice)
                }
            }
        }

        ttsVoicePicker.post {
            isInitialLoad = false
        }
    }

    private fun playVoiceSample(voice: TTSVoice) {
        lifecycleScope.launch {
            val cacheDir = File(cacheDir, "voice_samples")
            val voiceFile = File(cacheDir, "${voice.name}.wav")

            try {
                if (voiceFile.exists()) {
                    val audioData = voiceFile.readBytes()
                    sc.playAudioData(audioData)
                    Log.d("SettingsActivity", "Playing cached sample for ${voice.displayName}")
                } else {
                    sc.testVoice(TEST_TEXT, voice)
                    Log.d("SettingsActivity", "Synthesizing test for ${voice.displayName}")
                }
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    Log.e("SettingsActivity", "Error playing voice sample", e)
                    Toast.makeText(this@SettingsActivity, "Error playing voice", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun cacheVoiceSamples() {
        lifecycleScope.launch(Dispatchers.IO) {
            val cacheDir = File(cacheDir, "voice_samples")
            if (!cacheDir.exists()) cacheDir.mkdirs()

            var downloadedCount = 0
            for (voice in availableVoices) {
                val voiceFile = File(cacheDir, "${voice.name}.wav")
                if (!voiceFile.exists()) {
                    try {
                        val audioData = GoogleTts.synthesize(TEST_TEXT, voice)
                        voiceFile.writeBytes(audioData)
                        downloadedCount++
                    } catch (e: Exception) {
                        Log.e("SettingsActivity", "Failed to cache voice ${voice.name}", e)
                    }
                }
            }
            if (downloadedCount > 0) {
                runOnUiThread {
                    Toast.makeText(this@SettingsActivity, "$downloadedCount voice samples prepared.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun loadAllSettings() {

        // Inside loadAllSettings()
        val keyManager = PicovoiceKeyManager(this)
        editWakeWordKey.setText(keyManager.getUserProvidedKey() ?: "") // You will create this method next
        val savedVoiceName = sharedPreferences.getString(KEY_SELECTED_VOICE, DEFAULT_VOICE.name)
        val savedVoice = availableVoices.find { it.name == savedVoiceName } ?: DEFAULT_VOICE
        ttsVoicePicker.value = availableVoices.indexOf(savedVoice)
        
        // Update wake word button state
        updateWakeWordButtonState()
        
        // Load local model preference
        checkboxUseLocalModel.isChecked = sharedPreferences.getBoolean(KEY_USE_LOCAL_MODEL, false)
    }

    private fun saveSelectedVoice(voice: TTSVoice) {
        VoicePreferenceManager.saveSelectedVoice(this, voice)
        Log.d("SettingsActivity", "Saved voice: ${voice.displayName}")
    }

    private fun showPicovoiceKeyRequiredDialog() {
        val dialog = AlertDialog.Builder(this)
            .setTitle("Picovoice Key Required")
            .setMessage("To enable wake word functionality, you need a Picovoice AccessKey. You can get a free key from the Picovoice Console. Note: The Picovoice dashboard might not be available on mobile browsers sometimes - you may need to use a desktop browser.")
            .setPositiveButton("Get Key") { _, _ ->
                // Try to open Picovoice console
                val url = "https://console.picovoice.ai/login"
                val intent = Intent(Intent.ACTION_VIEW)
                intent.data = Uri.parse(url)
                try {
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this, "Could not open link. No browser found or link unavailable on mobile. Please use a desktop browser.", Toast.LENGTH_LONG).show()
                    Log.e("SettingsActivity", "Failed to open Picovoice link", e)
                }
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
        
        // Set button text colors to white
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(
            androidx.core.content.ContextCompat.getColor(this, R.color.white)
        )
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(
            androidx.core.content.ContextCompat.getColor(this, R.color.white)
        )
    }

    private fun updateWakeWordButtonState() {
        wakeWordManager.updateButtonState(wakeWordButton)
    }

    private fun showBatteryOptimizationDialog() {
        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.battery_optimization_title))
            .setMessage(getString(R.string.battery_optimization_message))
            .setPositiveButton(getString(R.string.learn_how)) { _, _ ->
                // Open the Tasker FAQ URL
                val url = "https://tasker.joaoapps.com/userguide/en/faqs/faq-problem.html#00"
                val intent = Intent(Intent.ACTION_VIEW)
                intent.data = Uri.parse(url)
                try {
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this, "Could not open link. No browser found.", Toast.LENGTH_LONG).show()
                    Log.e("SettingsActivity", "Failed to open battery optimization link", e)
                }
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
        
        // Set button text colors to white
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(
            androidx.core.content.ContextCompat.getColor(this, R.color.white)
        )
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(
            androidx.core.content.ContextCompat.getColor(this, R.color.white)
        )
    }

    private fun showSignOutConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Sign Out")
            .setMessage("Are you sure you want to sign out? This will clear all your settings and data.")
            .setPositiveButton("Sign Out") { _, _ ->
                signOut()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun signOut() {
        // Clear User Profile
        val userProfileManager = UserProfileManager(this)
        userProfileManager.clearProfile()

        // Clear all shared preferences for this app
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()


        // Restart the app by navigating to the onboarding screen
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    
    override fun getContentLayoutId(): Int = R.layout.activity_settings
    
    override fun getCurrentNavItem(): BaseNavigationActivity.NavItem = BaseNavigationActivity.NavItem.SETTINGS
}