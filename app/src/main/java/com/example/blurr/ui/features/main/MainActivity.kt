package com.example.blurr.ui.features.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.blurr.BuildConfig
import com.example.blurr.DialogueActivity
import com.example.blurr.R
import com.example.blurr.services.EnhancedWakeWordService
import com.example.blurr.ui.theme.BlurrTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Toast.makeText(this, "Permission granted!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Permission denied. Some features may not work.", Toast.LENGTH_LONG).show()
            }
        }

    private val dialogueLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val enhancedInstruction = result.data?.getStringExtra("EXTRA_ENHANCED_INSTRUCTION")
            if (!enhancedInstruction.isNullOrEmpty()) {
                viewModel.onEvent(MainContract.Event.ExecuteTask(enhancedInstruction))
            }
        } else if (result.resultCode == RESULT_CANCELED) {
            Toast.makeText(this, "Dialogue cancelled", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        askForPermissions()
        checkAndRequestOverlayPermission()

        setContent {
            BlurrTheme {
                val state by viewModel.uiState.collectAsState()

                // This LaunchedEffect will listen for side effects from the ViewModel
                LaunchedEffect(Unit) {
                    viewModel.sideEffects.collectLatest { effect ->
                        when (effect) {
                            is MainContract.SideEffect.StartClarificationDialogue -> {
                                startClarificationDialogue(effect.instruction, effect.questions)
                            }
                            is MainContract.SideEffect.ToggleWakeWordService -> {
                                toggleWakeWordService(effect.usePorcupine)
                            }
                            is MainContract.SideEffect.ShowToast -> {
                                Toast.makeText(this@MainActivity, effect.message, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }

                MainScreen(
                    state = state,
                    onEvent = viewModel::onEvent,
                    onStartAccessibilitySettings = {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                    onOpenGithub = {
                        val url = "https://github.com/Ayush0Chaudhary/blurr"
                        val intent = Intent(Intent.ACTION_VIEW)
                        intent.data = Uri.parse(url)
                        startActivity(intent)
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onEvent(MainContract.Event.CheckServiceStatus)
    }

    private fun startClarificationDialogue(originalInstruction: String, questions: List<String>) {
        val intent = Intent(this, DialogueActivity::class.java).apply {
            putExtra(DialogueActivity.EXTRA_ORIGINAL_INSTRUCTION, originalInstruction)
            putExtra(DialogueActivity.EXTRA_QUESTIONS, ArrayList(questions))
        }
        dialogueLauncher.launch(intent)
    }

    private fun toggleWakeWordService(usePorcupine: Boolean) {
        if (EnhancedWakeWordService.isRunning) {
            stopService(Intent(this, EnhancedWakeWordService::class.java))
            Toast.makeText(this, getString(R.string.wake_word_disabled), Toast.LENGTH_SHORT).show()
        } else {
            if (usePorcupine && !isPorcupineAccessKeyConfigured()) {
                Toast.makeText(this, getString(R.string.porcupine_access_key_required), Toast.LENGTH_LONG).show()
                return
            }

            val serviceIntent = Intent(this, EnhancedWakeWordService::class.java).apply {
                putExtra(EnhancedWakeWordService.EXTRA_USE_PORCUPINE, usePorcupine)
            }

            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                ContextCompat.startForegroundService(this, serviceIntent)
                val engineName = if (usePorcupine) "Porcupine" else "STT"
                Toast.makeText(this, getString(R.string.wake_word_enabled, engineName), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Microphone permission is required for wake word.", Toast.LENGTH_LONG).show()
                askForPermissions()
            }
        }
        // Update UI after a short delay to allow service state to change
        lifecycleScope.launch {
            delay(500)
            viewModel.onEvent(MainContract.Event.CheckServiceStatus)
        }
    }

    private fun isPorcupineAccessKeyConfigured(): Boolean {
        return try {
            val accessKey = BuildConfig.PICOVOICE_ACCESS_KEY
            accessKey.isNotEmpty() && accessKey != "YOUR_PICOVOICE_ACCESS_KEY_HERE"
        } catch (e: Exception) {
            false
        }
    }

    private fun askForPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun checkAndRequestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }
}
