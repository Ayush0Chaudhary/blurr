package com.example.blurr.ui.features.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.blurr.agent.VisionMode

// Define colors from your theme to be used in Composables
val DarkBackground = Color(0xFF121212) // Example dark background
val LightTextColor = Color(0xFFCECECE)
val HintTextColor = Color(0xFF888888)
val BlueAccent = Color(0xFF5880F7)
val PurpleAccent = Color(0xFFBE63F3)
val RedError = Color(0xFFCF6679)
val GreenSuccess = Color(0xFF66BB6A)
val ComponentBackground = Color(0x33FFFFFF) // Semi-transparent white
val DividerColor = Color(0xFF3B3B3B)

@Composable
fun MainScreen(
    state: MainContract.State,
    onEvent: (MainContract.Event) -> Unit,
    onStartAccessibilitySettings: () -> Unit,
//    onStartClarificationDialogue: (String, List<String>) -> Unit, // This would be handled by a side effect from VM
//    onToggleWakeWordService: (Boolean) -> Unit,
    onOpenGithub: () -> Unit
) {
    Scaffold(
        containerColor = DarkBackground,
        bottomBar = {
            PermissionFooter(
                isGranted = state.isPermissionGranted,
                onGrantClick = onStartAccessibilitySettings,
                onGithubClick = onOpenGithub
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues) // Apply padding from Scaffold
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.Start
        ) {
            Spacer(Modifier.height(16.dp))

            // Header Text
            GradientText(
                text = "Hello Sir,",
                fontSize = 40.sp,
                brush = Brush.linearGradient(colors = listOf(PurpleAccent, BlueAccent))
            )
            Text(
                text = "Assistant at\nyour command",
                color = Color.White,
                fontSize = 40.sp,
                lineHeight = 44.sp
            )
            Spacer(Modifier.height(24.dp))

            // Content Moderation
            StyledTextField(
                value = state.contentModerationText,
                onValueChange = { onEvent(MainContract.Event.OnContentModerationChanged(it)) },
                hint = "Write things you want to avoid"
            )
            Spacer(Modifier.height(12.dp))
            StyledButton(
                text = "Content Filtering",
                onClick = { onEvent(MainContract.Event.ExecuteContentModeration) }
            )
            Spacer(Modifier.height(16.dp))

            // Main Task
            StyledTextField(
                value = state.instruction,
                onValueChange = { onEvent(MainContract.Event.OnInstructionChanged(it)) },
                hint = "Input the task"
            )
            Spacer(Modifier.height(16.dp))

            // Voice Input
            VoiceInputSection(
                statusText = state.voiceStatusText,
                onPress = { onEvent(MainContract.Event.OnVoiceInputPressed) },
                onRelease = { onEvent(MainContract.Event.OnVoiceInputReleased) }
            )
            Spacer(Modifier.height(16.dp))

            // Perform Task Button
            StyledButton(
                text = "Perform Task",
                onClick = { onEvent(MainContract.Event.ExecuteTask()) },
                enabled = state.isTaskButtonEnabled
            )
            Spacer(Modifier.height(16.dp))

            // Status Text
            Text(
                text = state.statusText,
                color = Color.White,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))

            // Wake Word Engine
            SettingsGroup(title = "Wake Word Engine") {
                RadioSelector(
                    options = listOf("STT Engine", "Porcupine Engine"),
                    selectedOption = if (state.usePorcupineEngine) "Porcupine Engine" else "STT Engine",
                    onOptionSelected = { onEvent(MainContract.Event.OnWakeWordEngineChanged(it == "Porcupine Engine")) }
                )
                Spacer(Modifier.height(12.dp))
                StyledButton(
                    text = if (state.isWakeWordServiceRunning) "Disable Wake Word" else "Enable Wake Word",
                    onClick = { onEvent(MainContract.Event.ToggleWakeWordService) },
                    isBordered = true
                )
            }
            Spacer(Modifier.height(16.dp))

            // Vision Mode
            SettingsGroup(title = "Vision Mode") {
                RadioSelector(
                    options = listOf(VisionMode.XML.name, VisionMode.SCREENSHOT.name),
                    selectedOption = state.visionMode.name,
                    onOptionSelected = {
                        val mode = if (it == VisionMode.XML.name) VisionMode.XML else VisionMode.SCREENSHOT
                        onEvent(MainContract.Event.OnVisionModeChanged(mode))
                    },
                    isHorizontal = true
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = state.visionMode.description,
                    color = HintTextColor,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(16.dp)) // Space at the bottom of the scroll view
        }
    }
}

@Composable
fun GradientText(text: String, fontSize: androidx.compose.ui.unit.TextUnit, brush: Brush) {
    Text(
        text = text,
        style = TextStyle(
            brush = brush,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold
        )
    )
}

@Composable
fun StyledTextField(value: String, onValueChange: (String) -> Unit, hint: String) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(hint, color = HintTextColor) },
        modifier = Modifier
            .fillMaxWidth()
            .background(ComponentBackground, RoundedCornerShape(8.dp)),
        shape = RoundedCornerShape(8.dp),
        colors = TextFieldDefaults.colors(
            focusedTextColor = LightTextColor,
            unfocusedTextColor = LightTextColor,
            focusedContainerColor = ComponentBackground,
            unfocusedContainerColor = ComponentBackground,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent
        )
    )
}

@Composable
fun StyledButton(text: String, onClick: () -> Unit, enabled: Boolean = true, isBordered: Boolean = false) {
    val shape = RoundedCornerShape(8.dp)
    val buttonModifier = if (isBordered) {
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, BlueAccent, shape)
            .clickable(enabled = enabled, onClick = onClick)
    } else {
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(colors = listOf(PurpleAccent, BlueAccent)))
            .clickable(enabled = enabled, onClick = onClick)
    }

    Box(
        modifier = buttonModifier.padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White, fontSize = 16.sp)
    }
}

@Composable
fun VoiceInputSection(statusText: String, onPress: () -> Unit, onRelease: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Voice Input", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text("(Auto-execute)", color = HintTextColor, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(Brush.radialGradient(colors = listOf(BlueAccent, PurpleAccent)))
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            onPress()
                            tryAwaitRelease()
                            onRelease()
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Phone,
                contentDescription = "Voice Input",
                tint = Color.White,
                modifier = Modifier.size(40.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(statusText, color = HintTextColor, fontSize = 12.sp)
    }
}

@Composable
fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(ComponentBackground, RoundedCornerShape(8.dp))
                .padding(12.dp)
        ) {
            content()
        }
    }
}

@Composable
fun RadioSelector(
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit,
    isHorizontal: Boolean = false
) {
    val layout: @Composable (content: @Composable () -> Unit) -> Unit = if (isHorizontal) {
        { content -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) { content() } }
    } else {
        { content -> Column { content() } }
    }

    layout {
        options.forEach { text ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onOptionSelected(text) }
                    .padding(vertical = 4.dp)
            ) {
                RadioButton(
                    selected = (text == selectedOption),
                    onClick = { onOptionSelected(text) },
                    colors = RadioButtonDefaults.colors(
                        selectedColor = BlueAccent,
                        unselectedColor = HintTextColor
                    )
                )
                Text(text = text, color = LightTextColor)
            }
        }
    }
}

@Composable
fun PermissionFooter(isGranted: Boolean, onGrantClick: () -> Unit, onGithubClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkBackground)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = if (isGranted) "Permission: Granted" else "Permission: Not Granted",
                color = if (isGranted) GreenSuccess else RedError,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            StyledButton(
                text = "Grant Accessibility Permission",
                onClick = onGrantClick,
                isBordered = true
            )
        }
        HorizontalDivider(thickness = 1.dp, color = DividerColor)
        Text(
            text = "View source code on GitHub",
            color = BlueAccent,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onGithubClick)
                .padding(16.dp),
            textAlign = TextAlign.Center
        )
    }
}
