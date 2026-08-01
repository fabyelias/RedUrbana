package com.redurbana.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.redurbana.core.ui.theme.RedUrbanaColors
import com.redurbana.core.ui.theme.RedUrbanaTheme
import com.redurbana.app.navigation.RedUrbanaNavHost
import dagger.hilt.android.AndroidEntryPoint

/**
 * Único punto de entrada de Compose. Toda la navegación vive en
 * RedUrbanaNavHost — MainActivity no conoce ninguna feature directamente.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RedUrbanaTheme(darkTheme = true) {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(RedUrbanaColors.BackgroundPrimary),
                    color = RedUrbanaColors.BackgroundPrimary,
                ) {
                    RedUrbanaNavHost()
                }
            }
        }
    }
}
