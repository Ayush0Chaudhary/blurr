package com.blurr.voice

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.core.view.WindowCompat
import com.blurr.voice.ui.AssistantOverlay
import com.blurr.voice.utilities.Logger
import com.blurr.voice.utilities.PandaState
import com.blurr.voice.utilities.PandaStateManager

class AssistantActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Logger.ui("AssistantActivity: onCreate")

        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            val focusRequester = remember { FocusRequester() }
            val pandaStateManager = PandaStateManager.getInstance(this)
            val stateInfo by pandaStateManager.stateFlow.collectAsState()

            AssistantOverlay(
                stateInfo = stateInfo,
                onOutsideClick = {
                    Logger.ui("AssistantActivity: onOutsideClick detected, finishing activity.")
                    finish()
                },
                focusRequester = focusRequester,
                onSendMessage = { message ->
                    Logger.ui("AssistantActivity: onSendMessage, sending text to service: '$message'")
                    val serviceIntent = Intent(this, ConversationalAgentService::class.java).apply {
                        action = ConversationalAgentService.ACTION_PROCESS_TEXT
                        putExtra(ConversationalAgentService.EXTRA_TEXT_QUERY, message)
                    }
                    startService(serviceIntent)
                },
                onResetConversation = {
                    Logger.ui("AssistantActivity: onResetConversation triggered. Setting state to IDLE.")
                    pandaStateManager.updateStateWithMessage(PandaState.IDLE)
                }
            )
        }

        setFinishOnTouchOutside(true)
    }

    override fun onStart() {
        super.onStart()
        Logger.ui("AssistantActivity: onStart")
    }

    override fun onResume() {
        super.onResume()
        Logger.ui("AssistantActivity: onResume")
    }

    override fun onPause() {
        super.onPause()
        Logger.ui("AssistantActivity: onPause")
    }

    override fun onStop() {
        super.onStop()
        Logger.ui("AssistantActivity: onStop")
    }

    override fun onDestroy() {
        super.onDestroy()
        Logger.ui("AssistantActivity: onDestroy. Stopping conversational agent service.")
        // Ensure the service is stopped when the UI is destroyed
        stopService(Intent(this, ConversationalAgentService::class.java))
    }
}