package com.blurr.voice.ui

import android.util.Log
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blurr.voice.R
import com.blurr.voice.ui.theme.AssistantTheme
import com.blurr.voice.utilities.Logger
import com.blurr.voice.utilities.PandaState
import com.blurr.voice.utilities.PandaStateInfo

@Composable
fun AssistantOverlay(
    stateInfo: PandaStateInfo,
    onOutsideClick: () -> Unit,
    focusRequester: FocusRequester,
    onSendMessage: (String) -> Unit,
    onResetConversation: () -> Unit
) {
    val bottomPadding = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    val stablePadding = remember(bottomPadding) { bottomPadding }
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        visible = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onOutsideClick() }
            )
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(animationSpec = tween(300)),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(animationSpec = tween(250)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = stablePadding)
                .padding(12.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { /* Consume click */ }
                )
        ) {
            OverlayContainer(
                stateInfo = stateInfo,
                focusRequester = focusRequester,
                onSendMessage = onSendMessage,
                onResetConversation = onResetConversation
            )
        }
    }
}

@Composable
private fun OverlayContainer(
    stateInfo: PandaStateInfo,
    focusRequester: FocusRequester,
    onSendMessage: (String) -> Unit,
    onResetConversation: () -> Unit
) {
    var text by remember { mutableStateOf("") }
    var isFocused by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current
    var isSendingLocal by remember { mutableStateOf(false) }

    val submitAction = {
        if (text.isNotBlank() && !isSendingLocal) {
            isSendingLocal = true
            onSendMessage(text)
            // DO NOT clear text here. This prevents the stutter.
            keyboardController?.hide()
        }
    }

    LaunchedEffect(stateInfo.state) {
        when (stateInfo.state) {
            PandaState.PROCESSING -> {
                // Clear text immediately when processing starts
                text = ""
            }
            PandaState.SPEAKING -> {
                // Keep text empty while speaking
                text = ""
            }
            PandaState.AWAITING_INPUT -> {
                // Clear text and reset sending flag
                text = ""
                isSendingLocal = false
            }
            else -> {
                // For any other state, ensure text is clear
                isSendingLocal = false
            }
        }
    }

    val isExpanded = stateInfo.state == PandaState.SPEAKING || stateInfo.state == PandaState.AWAITING_INPUT
    val containerModifier = if (isExpanded) {
        Modifier
            .clip(RoundedCornerShape(AssistantTheme.dimens.largeCornerRadius))
            .border(
                width = AssistantTheme.dimens.borderWidth,
                color = AssistantTheme.colors.borderColor,
                shape = RoundedCornerShape(AssistantTheme.dimens.largeCornerRadius)
            )
            .background(AssistantTheme.colors.containerBackground)
            .padding(16.dp)
    } else {
        Modifier
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(containerModifier),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            ResponseContent(message = stateInfo.message)
        }

        InputPill(
            stateInfo = stateInfo,
            text = text,
            onTextChange = { text = it },
            isFocused = isFocused,
            onFocusChanged = { isFocused = it },
            focusRequester = focusRequester,
            onSubmit = submitAction,
            isSending = isSendingLocal || stateInfo.state == PandaState.PROCESSING || stateInfo.state == PandaState.SPEAKING,
            isContained = isExpanded,
            onResetConversation = onResetConversation
        )
    }
}

@Composable
private fun ResponseContent(message: String?) {
    // This component remains unchanged
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 24.dp, top = 8.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.panda_icon),
                contentDescription = "Assistant Icon",
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(50))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = message ?: "...",
                color = AssistantTheme.colors.inputText,
                fontSize = 20.sp,
                style = AssistantTheme.typography.body
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ActionButton(icon = Icons.Outlined.FavoriteBorder, description = "Paw")
            ActionButton(icon = Icons.Outlined.Star, description = "Lightning")
            ActionButton(icon = Icons.Outlined.ThumbUp, description = "Heart")
            ActionButton(icon = Icons.Outlined.Settings, description = "Settings")
        }
    }
}

@Composable
private fun ActionButton(icon: ImageVector, description: String) {
    // This component remains unchanged
    val context = LocalContext.current
    Icon(
        imageVector = icon,
        contentDescription = description,
        modifier = Modifier
            .size(30.dp)
            .clickable {
                Toast
                    .makeText(context, "$description clicked", Toast.LENGTH_SHORT)
                    .show()
            },
        tint = AssistantTheme.colors.borderColor
    )
}


// blurr-main/app/src/main/java/com/blurr/voice/ui/AssistantOverlayUI.kt


@Composable
private fun InputPill(
    stateInfo: PandaStateInfo,
    text: String,
    onTextChange: (String) -> Unit,
    isFocused: Boolean,
    onFocusChanged: (Boolean) -> Unit,
    focusRequester: FocusRequester,
    onSubmit: () -> Unit,
    isSending: Boolean,
    isContained: Boolean,
    onResetConversation: () -> Unit
) {
    val context = LocalContext.current

    val pillModifier = if (isContained) {
        Modifier
            .clip(RoundedCornerShape(AssistantTheme.dimens.cornerRadius))
            .background(AssistantTheme.colors.inputPillBackground)
    } else {
        Modifier
            .clip(RoundedCornerShape(AssistantTheme.dimens.cornerRadius))
            .border(
                width = AssistantTheme.dimens.borderWidth,
                color = AssistantTheme.colors.borderColor,
                shape = RoundedCornerShape(AssistantTheme.dimens.cornerRadius)
            )
            .background(AssistantTheme.colors.containerBackground)
    }

    Row(
        modifier = Modifier
            .height(AssistantTheme.dimens.containerHeight)
            .fillMaxWidth()
            .then(pillModifier)
            .clickable(
                enabled = !isContained,
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                if (!isFocused) {
                    focusRequester.requestFocus()
                }
            }
            // Increase left padding for more space
            .padding(start = 20.dp, end = AssistantTheme.dimens.paddingMedium),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            contentAlignment = Alignment.CenterStart
        ) {
            BasicTextField(
                value = text,
                onValueChange = onTextChange,
                enabled = !isSending,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onFocusChanged { focusState -> onFocusChanged(focusState.isFocused) },
                textStyle = TextStyle(
                    color = AssistantTheme.colors.inputText,
                    fontSize = 18.sp
                ),
                singleLine = true,
                cursorBrush = SolidColor(AssistantTheme.colors.cursor),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (!isSending) onSubmit() }),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        val showPlaceholder = text.isEmpty()
                        if (showPlaceholder) {
                            Text(
                                text = "Ask panda anything...",
                                color = AssistantTheme.colors.placeholderText,
                                fontSize = 18.sp,
                                style = AssistantTheme.typography.body
                            )
                        }

                        if (!isSending && text.isNotEmpty()) {
                            innerTextField()
                        }
                    }
                }
            )

            if (stateInfo.state == PandaState.AWAITING_INPUT && !isFocused) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            onResetConversation()
                            focusRequester.requestFocus()
                        }
                )
            }
        }
        Spacer(modifier = Modifier.width(AssistantTheme.dimens.paddingMedium))

        val showMic = text.isBlank() && !isSending
        if (showMic) {
            Image(
                painter = painterResource(id = R.drawable.mic_icon),
                contentDescription = "Activate voice input",
                modifier = Modifier
                    .size(AssistantTheme.dimens.iconSize)
                    .clickable(enabled = !isSending) {
                        Toast
                            .makeText(context, "Voice input placeholder", Toast.LENGTH_SHORT)
                            .show()
                    }
            )
        } else {
            Image(
                painter = painterResource(id = R.drawable.arrow_icon),
                contentDescription = "Send message",
                modifier = Modifier
                    .size(AssistantTheme.dimens.iconSize)
                    .clickable(enabled = !isSending) { onSubmit() }
            )
        }
    }
}