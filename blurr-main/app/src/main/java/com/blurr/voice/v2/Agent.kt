package com.blurr.voice.v2

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.blurr.voice.v2.actions.ActionExecutor
import com.blurr.voice.v2.fs.FileSystem
import com.blurr.voice.v2.llm.GeminiApi
import com.blurr.voice.v2.llm.GeminiMessage
import com.blurr.voice.v2.message_manager.MemoryManager
import com.blurr.voice.v2.perception.Perception
import kotlinx.coroutines.delay

/**
 * The main conductor of the agent.
 * This class owns all the necessary components and runs the primary SENSE -> THINK -> ACT loop.
 *
 * @param settings The agent's configuration.
 * @param memoryManager The agent's short-term memory and prompt builder.
 * @param perception The agent's "eyes," responsible for analyzing the screen.
 * @param llmApi The client for communicating with the Gemini LLM.
 * @param actionExecutor The agent's "hands," responsible for executing actions on the device.
 * @param fileSystem The agent's long-term file storage.
 * @param context The Android application context.
 */
@RequiresApi(Build.VERSION_CODES.R)
class Agent(
    private val settings: AgentSettings,
    private val memoryManager: MemoryManager,
    private val perception: Perception,
    private val llmApi: GeminiApi,
    private val actionExecutor: ActionExecutor,
    private val fileSystem: FileSystem,
    private val context: Context
) {
    // The agent's internal state, which is updated at each step.
    val state: AgentState = AgentState()
    private val TAG = "AgentV2"

    // A complete, long-term record of the entire session.
    // We use <Unit> because we haven't defined a custom structured output for the 'done' action yet.
    val history: AgentHistoryList<Unit> = AgentHistoryList()

    /**
     * The main entry point to start the agent's execution loop.
     *
     * @param initialTask The high-level task requested by the user.
     * @param maxSteps The maximum number of steps the agent can take before stopping.
     */
    suspend fun run(initialTask: String, maxSteps: Int = 150) {
        memoryManager.addNewTask(initialTask)
        state.stopped = false
        Log.d(TAG, "--- Agent starting task: '$initialTask' ---")

        while (!state.stopped && state.nSteps <= maxSteps) {
            Log.d(TAG,"\n--- Step ${state.nSteps}/$maxSteps ---")

            // 1. SENSE: Observe the current state of the screen.
            Log.d(TAG,"👀 Sensing screen state...")
            val screenState = perception.analyze()

            // 2. THINK (Prepare Prompt): Update memory with the results of the LAST step
            // and create the new prompt using the CURRENT screen state.
            Log.d(TAG,"🧠 Preparing prompt...")
            memoryManager.createStateMessage(
                modelOutput = state.lastModelOutput,
                result = state.lastResult,
                stepInfo = AgentStepInfo(state.nSteps, maxSteps),
                screenState = screenState
            )

            // 3. THINK (Get Decision): Send the prepared messages to the LLM.
            Log.d(TAG,"🤔 Asking LLM for next action...")
            val messages = memoryManager.getMessages()
            val agentOutput = llmApi.generateAgentOutput(messages)

            // --- Handle LLM Failure ---
            if (agentOutput == null) {
                Log.d(TAG,"❌ LLM failed to return a valid action. Retrying...")
                state.consecutiveFailures++
                // Add a corrective message for the next attempt.
                memoryManager.addContextMessage(GeminiMessage(text = "System Note: Your previous output was not valid JSON. Please ensure your response is correctly formatted."))
                if (state.consecutiveFailures >= settings.maxFailures) {
                    Log.d(TAG,"❌ Agent failed too many times consecutively. Stopping.")
                    break
                }
                delay(1000) // Wait a moment before retrying
                continue // Skip to the next loop iteration
            }
            state.consecutiveFailures = 0
            state.lastModelOutput = agentOutput
            Log.d(TAG, agentOutput.toString())
            Log.d(TAG,"🤖 LLM decided: ${agentOutput.nextGoal}")

            // 4. ACT: Execute the LLM's planned actions.
            Log.d(TAG,"💪 Executing actions...")
            val actionResults = mutableListOf<ActionResult>()
            for (action in agentOutput.action) {
                val result = actionExecutor.execute(action, screenState, context, fileSystem)
                actionResults.add(result)
                Log.d(TAG,"  - Action '${action::class.simpleName}' executed. Result: ${result.longTermMemory ?: result.error ?: "OK"}")

                // If an action fails, stop executing further actions in this step.
                if (result.error != null) {
                    Log.d(TAG,"  - 🛑 Action failed. Stopping current step's execution.")
                    break
                }
            }
            state.lastResult = actionResults

            // 5. RECORD: Save the complete step to the long-term history.
            history.addItem(
                AgentHistory(
                    modelOutput = agentOutput,
                    result = actionResults,
                    state = screenState,
                    metadata = null // You can add timing/token metadata here later
                )
            )

            // --- Check for Task Completion ---
            if (actionResults.any { it.isDone == true }) {
                Log.d(TAG,"✅ Agent finished the task.")
                state.stopped = true
            }

            state.nSteps++
            delay(1000) // A small, polite delay between steps.
        }

        // --- Loop Finished ---
        if (state.nSteps > maxSteps) {

            Log.d(TAG,"--- 🏁 Agent reached max steps. Stopping. ---")
        } else {
            Log.d(TAG,"--- 🏁 Agent run finished. ---")
        }
    }
}