package com.example.sanivox

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import com.example.sanivox.ui.theme.SanivoxTheme


class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        DarkModeController.darkMode.value = obtenerPreferencia(this, "modo_oscuro", false)

        setContent {
            val darkMode by DarkModeController.darkMode
            LaunchedEffect(darkMode) { /* Forzar recomposición */ }

            SanivoxTheme(darkTheme = darkMode) {
                SettingsScreen(
                    context = this,
                    onBack = { finish() }
                )
            }
        }
    }

    override fun onPause() {
        super.onPause()
        finish()
    }

}
