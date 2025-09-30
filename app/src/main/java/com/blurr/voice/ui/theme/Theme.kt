/**
 * @file Theme.kt
 * @brief Defines the Jetpack Compose theme for the application.
 *
 * This file contains the main theme composable, `BlurrTheme`, which sets up the
 * color schemes and typography for the entire application. It supports dark and light
 * themes, as well as dynamic coloring on Android 12+.
 */
package com.blurr.voice.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * The color scheme for the dark theme of the application.
 */
private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

/**
 * The color scheme for the light theme of the application.
 */
private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40

    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
)

/**
 * The main theme composable for the Blurr application.
 *
 * This function applies the appropriate color scheme and typography to its content.
 * It automatically detects the system's theme (dark or light) and supports Material You
 * dynamic coloring on compatible devices (Android 12+).
 *
 * @param darkTheme Whether the theme should be dark. Defaults to the system setting.
 * @param dynamicColor Whether to use dynamic (wallpaper-based) coloring on Android 12+.
 *                     Defaults to true.
 * @param content The composable content to which the theme will be applied.
 */
@Composable
fun BlurrTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}