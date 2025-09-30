/**
 * @file DialogueActivity.kt
 * @brief Defines an activity for conducting a clarification dialogue with the user.
 *
 * This file contains the `DialogueActivity`, which provides a user interface to ask a series
 * of follow-up questions when an initial command is ambiguous. It uses both TTS and STT to
 * create a voice-forward experience.
 */
package com.blurr.voice

import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.blurr.voice.utilities.STTManager
import com.blurr.voice.utilities.TTSManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * An activity that presents a question-and-answer dialogue to the user.
 *
 * This UI is launched when the agent determines a user's command is ambiguous and requires
 * clarification. It iterates through a list of questions, speaking each one and listening for
 * a spoken answer. Once all questions are answered, it constructs an "enhanced" instruction
 * containing the original command plus the new information and returns it to the calling service.
 */
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

    /**
     * Companion object holding constants for Intent extras.
     */
    companion object {
        const val EXTRA_ORIGINAL_INSTRUCTION = "original_instruction"
        const val EXTRA_QUESTIONS = "questions"
        const val EXTRA_ANSWERS = "answers"
        const val EXTRA_ENHANCED_INSTRUCTION = "enhanced_instruction"
    }

    /**
     * Called when the activity is first created. Initializes UI, managers, and starts the dialogue.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dialogue)

        questionText = findViewById(R.id.questionText)
        answerInput = findViewById(R.id.answerInput)
        submitButton = findViewById(R.id.submitButton)
        voiceInputButton = findViewById(R.id.voiceInputButton)
        voiceStatusText = findViewById(R.id.voiceStatusText)
        progressText = findViewById(R.id.progressText)
        cancelButton = findViewById(R.id.cancelButton)

        ttsManager = TTSManager.getInstance(this)
        sttManager = STTManager(this)

        originalInstruction = intent.getStringExtra(EXTRA_ORIGINAL_INSTRUCTION) ?: ""
        questions = intent.getStringArrayListExtra(EXTRA_QUESTIONS) ?: arrayListOf()

        setupUI()
        setupVoiceInput()
        setupClickListeners()

        if (questions.isNotEmpty()) {
            showQuestion(0)
        } else {
            finishWithResult()
        }
    }

    /**
     * Sets up initial UI elements like the progress text and cancel button listener.
     */
    private fun setupUI() {
        updateProgress()
        cancelButton.setOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
        }
    }

    /**
     * Configures the touch listener for the voice input button to start/stop STT.
     */
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

    /**
     * Configures click listeners for the submit button and the answer input field's "Enter" key.
     */
    private fun setupClickListeners() {
        submitButton.setOnClickListener {
            val answer = answerInput.text.toString().trim()
            if (answer.isNotEmpty()) {
                submitAnswer(answer)
            } else {
                Toast.makeText(this, "Please provide an answer", Toast.LENGTH_SHORT).show()
            }
        }

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

    /**
     * Starts the STT process and updates the UI to indicate it's listening.
     * Automatically submits the recognized answer.
     */
    private fun startVoiceInput() {
        voiceStatusText.text = getString(R.string.listening)
        voiceInputButton.isPressed = true
        
        Toast.makeText(this, "Listening for your answer...", Toast.LENGTH_SHORT).show()
        
        sttManager.startListening(
            onResult = { recognizedText ->
                runOnUiThread {
                    voiceStatusText.text = getString(R.string.hold_to_speak)
                    voiceInputButton.isPressed = false
                    answerInput.setText(recognizedText)
                    Toast.makeText(this, "Recognized: $recognizedText", Toast.LENGTH_SHORT).show()

                    lifecycleScope.launch {
                        delay(1000)
                        submitAnswer(recognizedText)
                    }
                }
            },
            onError = { errorMessage ->
                runOnUiThread {
                    voiceStatusText.text = getString(R.string.hold_to_speak)
                    voiceInputButton.isPressed = false
                    Toast.makeText(this, "Error: $errorMessage", Toast.LENGTH_SHORT).show()

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
            onPartialResult = { partialText ->
                runOnUiThread {
                    answerInput.setText(partialText)
                    answerInput.setSelection(partialText.length)
                }
            }
        )
    }

    /**
     * Stops the STT process and resets the UI.
     */
    private fun stopVoiceInput() {
        sttManager.stopListening()
        voiceStatusText.text = getString(R.string.hold_to_speak)
        voiceInputButton.isPressed = false
    }

    /**
     * Displays a question at a given index, speaks it aloud, and then automatically
     * starts listening for the user's answer.
     * @param index The index of the question to display from the `questions` list.
     */
    private fun showQuestion(index: Int) {
        if (index < questions.size) {
            currentQuestionIndex = index
            val question = questions[index]
            
            questionText.text = question
            answerInput.text.clear()
            answerInput.requestFocus()
            
            lifecycleScope.launch {
                ttsManager.speakText(question)
                
                delay(1000)
                runOnUiThread {
                    startVoiceInput()
                }
            }
            
            updateProgress()
        } else {
            finishWithResult()
        }
    }

    /**
     * Submits the user's answer and proceeds to the next question or finishes the dialogue.
     * @param answer The user's answer as a string.
     */
    private fun submitAnswer(answer: String) {
        answers.add(answer)
        
        val nextIndex = currentQuestionIndex + 1
        if (nextIndex < questions.size) {
            showQuestion(nextIndex)
        } else {
            finishWithResult()
        }
    }

    /**
     * Updates the progress text (e.g., "1 of 3").
     */
    private fun updateProgress() {
        val progress = "${currentQuestionIndex + 1} of ${questions.size}"
        progressText.text = progress
    }

    /**
     * Called when all questions have been answered. It constructs the final enhanced
     * instruction and returns the results to the calling activity/service.
     */
    private fun finishWithResult() {
        val enhancedInstruction = createEnhancedInstruction()
        
        val resultIntent = Intent().apply {
            putExtra(EXTRA_ORIGINAL_INSTRUCTION, originalInstruction)
            putExtra(EXTRA_ANSWERS, ArrayList(answers))
            putExtra(EXTRA_ENHANCED_INSTRUCTION, enhancedInstruction)
        }
        
        setResult(RESULT_OK, resultIntent)
        finish()
    }

    /**
     * Creates the final instruction string by appending the collected answers to the
     * original instruction.
     * @return The enhanced instruction string.
     */
    private fun createEnhancedInstruction(): String {
        var enhanced = originalInstruction
        
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

    /**
     * Shuts down the STT manager when the activity is destroyed to release resources.
     */
    override fun onDestroy() {
        super.onDestroy()
        sttManager.shutdown()
    }
} 