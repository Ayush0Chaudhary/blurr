package com.blurr.voice.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class AssistantColors(
    val containerBackground: Color,
    val inputPillBackground: Color, // ADDED: For the inner pill
    val placeholderText: Color,
    val inputText: Color,
    val cursor: Color,
    val iconTint: Color,
    val borderColor: Color
)

private val DarkColors = AssistantColors(
    containerBackground = Color(0xFF1A1A1A),      // Main card background (deep black)
    inputPillBackground = Color(0xFF2C2C2E),      // Inner input pill background (dark gray)
    placeholderText = Color(0xFF9E9E9E),
    inputText = Color.White,
    cursor = Color(0xFF8AB4F8),
    iconTint = Color.White,
    borderColor = Color(0xFFffa456)
)

data class AssistantDimens(
    val containerHeight: Dp,
    val cornerRadius: Dp,
    val largeCornerRadius: Dp, // ADDED: For the outer card
    val iconSize: Dp,
    val paddingMedium: Dp,
    val borderWidth: Dp
)

private val DefaultDimens = AssistantDimens(
    containerHeight = 64.dp,
    cornerRadius = 32.dp,       // For the inner pill
    largeCornerRadius = 28.dp,  // For the outer card
    iconSize = 38.dp,
    paddingMedium = 12.dp,
    borderWidth = 1.dp
)

data class AssistantTypography(
    val body: TextStyle
)

private val DefaultTypography = AssistantTypography(
    body = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 18.sp
    )
)

private val LocalAssistantColors = staticCompositionLocalOf { DarkColors }
private val LocalAssistantDimens = staticCompositionLocalOf { DefaultDimens }
private val LocalAssistantTypography = staticCompositionLocalOf { DefaultTypography }

object AssistantTheme {
    val colors: AssistantColors @Composable get() = LocalAssistantColors.current
    val dimens: AssistantDimens @Composable get() = LocalAssistantDimens.current
    val typography: AssistantTypography @Composable get() = LocalAssistantTypography.current
}