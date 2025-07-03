package com.example.sanivox.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.example.sanivox.DarkModeController

private val DarkColorScheme = darkColorScheme(
    primary = BlueLight,
    onPrimary = Color.Black,
    secondary = BlueSecondary,
    onSecondary = Color.Black,
    error = RedError,
    onError = Color.White,
    background = GreyDark,
    surface = GreyDark,
    onBackground = Color.White,
    onSurface = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = BluePrimary,
    onPrimary = Color.White,
    secondary = BlueSecondary,
    onSecondary = Color.White,
    error = RedError,
    onError = Color.White,
    background = GreyLight,
    surface = Color.White,
    onBackground = Color.Black,
    onSurface = Color.Black
)

@Composable
fun SanivoxTheme(
    darkTheme: Boolean = DarkModeController.darkMode.value,
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
