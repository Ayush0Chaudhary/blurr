package com.blurr.voice

import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.blurr.voice.utilities.TTSManager
import com.blurr.voice.utilities.STTManager
import kotlinx.coroutines.*
import kotlinx.coroutines.delay

class DialogueActivity : AppCompatActivity() {

    private lateinit var questionText: TextView
    private lateinit var answerInput: EditText
    private lateinit var submitButton: Button
    private lateinit var voiceInputButton: ImageButton
    private lateinit var voiceStatusText: TextView
    private lateinit var progressText: TextView
    private lateinit var cancelButton: Button

    private lateinit var ttsManager: TTSManager
    private lateinit var sttManager: STTManager

    private var questions: List<String> = emptyList()
    private var answers: MutableList<String> = mutableListOf()
    private var currentQuestionIndex = 0
    private var originalInstruction: String = ""

    companion object {
        const val EXTRA_ORIGINAL_INSTRUCTION = "original_instruction"
        const val EXTRA_QUESTIONS = "questions"
        const val EXTRA_ANSWERS = "answers"
        const val EXTRA_ENHANCED_INSTRUCTION = "enhanced_instruction"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dialogue)

        // Initialize UI components
        questionText = findViewById(R.id.questionText)
        answerInput = findViewById(R.id.answerInput)
        submitButton = findViewById(R.id.submitButton)
        voiceInputButton = findViewById(R.id.voiceInputButton)
        voiceStatusText = findViewById(R.id.voiceStatusText)
        progressText = findViewById(R.id.progressText)
        cancelButton = findViewById(R.id.cancelButton)

        // Initialize managers
        ttsManager = TTSManager.getInstance(this)
        sttManager = STTManager(this)

        // Get data from intent
        originalInstruction = intent.getStringExtra(EXTRA_ORIGINAL_INSTRUCTION) ?: ""
        questions = intent.getStringArrayListExtra(EXTRA_QUESTIONS) ?: arrayListOf()

        setupUI()
        setupVoiceInput()
        setupClickListeners()

        // Start with first question
        if (questions.isNotEmpty()) {
            showQuestion(0)
        } else {
            finishWithResult()
        }
    }

    private fun setupUI() {
        // Set up progress indicator
        updateProgress()
        
        // Set up cancel button
        cancelButton.setOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
        }
    }

    private fun setupVoiceInput() {
        voiceInputButton.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startVoiceInput()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    stopVoiceInput()
                    true
                }
                else -> false
            }
        }
    }

    private fun setupClickListeners() {
        submitButton.setOnClickListener {
            val answer = answerInput.text.toString().trim()
            if (answer.isNotEmpty()) {
                submitAnswer(answer)
            } else {
                Toast.makeText(this, "Please provide an answer", Toast.LENGTH_SHORT).show()
            }
        }

        // Handle Enter key in input field
        answerInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                val answer = answerInput.text.toString().trim()
                if (answer.isNotEmpty()) {
                    submitAnswer(answer)
                }
                true
            } else {
                false
            }
        }
    }

    private fun startVoiceInput() {
        voiceStatusText.text = getString(R.string.listening)
        voiceInputButton.isPressed = true
        
        // Show a toast to indicate automatic voice activation
        Toast.makeText(this, "Listening for your answer...", Toast.LENGTH_SHORT).show()
        
        sttManager.startListening(
            onResult = { recognizedText ->
                runOnUiThread {
                    voiceStatusText.text = getString(R.string.hold_to_speak)
                    voiceInputButton.isPressed = false
                    answerInput.setText(recognizedText)
                    Toast.makeText(this, "Recognized: $recognizedText", Toast.LENGTH_SHORT).show()

                    // Automatically submit the answer after a short delay
                    lifecycleScope.launch {
                        delay(1000) // Wait 1 second for user to see the recognized text
                        submitAnswer(recognizedText)
                    }
                }
            },
            onError = { errorMessage ->
                runOnUiThread {
                    voiceStatusText.text = getString(R.string.hold_to_speak)
                    voiceInputButton.isPressed = false
                    Toast.makeText(this, "Error: $errorMessage", Toast.LENGTH_SHORT).show()

                    // If there was an error, restart voice input after a delay
                    lifecycleScope.launch {
                        delay(2000)
                        startVoiceInput()
                    }
                }
            },
            onListeningStateChange = { isListening ->
                runOnUiThread {
                    voiceInputButton.isPressed = isListening
                    voiceStatusText.text = if (isListening) getString(R.string.listening) else getString(R.string.hold_to_speak)
                }
            },
            // ADD THIS CALLBACK
            onPartialResult = { partialText ->
                runOnUiThread {
                    answerInput.setText(partialText)
                    answerInput.setSelection(partialText.length) // Keep cursor at the end
                }
            }
        )
    }

    private fun stopVoiceInput() {
        sttManager.stopListening()
        voiceStatusText.text = getString(R.string.hold_to_speak)
        voiceInputButton.isPressed = false
    }

    private fun showQuestion(index: Int) {
        if (index < questions.size) {
            currentQuestionIndex = index
            val question = questions[index]
            
            questionText.text = question
            answerInput.text.clear()
            answerInput.requestFocus()
            
            // Speak the question
            lifecycleScope.launch {
                ttsManager.speakText(question)
                
                // Wait for TTS to complete, then start voice input automatically
                delay(1000) // Wait 1 second after question is displayed
                runOnUiThread {
                    startVoiceInput()
                }
            }
            
            updateProgress()
        } else {
            finishWithResult()
        }
    }

    private fun submitAnswer(answer: String) {
        answers.add(answer)
        
        // Move to next question
        val nextIndex = currentQuestionIndex + 1
        if (nextIndex < questions.size) {
            showQuestion(nextIndex)
        } else {
            finishWithResult()
        }
    }

    private fun updateProgress() {
        val progress = "${currentQuestionIndex + 1} of ${questions.size}"
        progressText.text = progress
    }

    private fun finishWithResult() {
        // Create enhanced instruction with answers
        val enhancedInstruction = createEnhancedInstruction()
        
        val resultIntent = Intent().apply {
            putExtra(EXTRA_ORIGINAL_INSTRUCTION, originalInstruction)
            putExtra(EXTRA_ANSWERS, ArrayList(answers))
            putExtra(EXTRA_ENHANCED_INSTRUCTION, enhancedInstruction)
        }
        
        setResult(RESULT_OK, resultIntent)
        finish()
    }

    private fun createEnhancedInstruction(): String {
        var enhanced = originalInstruction
        
        // Add answers to the instruction
        if (answers.isNotEmpty()) {
            enhanced += "\n\nAdditional information:"
            questions.forEachIndexed { index, question ->
                if (index < answers.size) {
                    enhanced += "\n- $question: ${answers[index]}"
                }
            }
        }
        
        return enhanced
    }

    override fun onDestroy() {
        super.onDestroy()
        sttManager.shutdown()
    }
} 