package com.redurbana.feature.lines.navigation

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

/**
 * Wrapper chico de `android.speech.tts.TextToSpeech` (API de plataforma, sin
 * dependencia nueva) para las instrucciones habladas de la navegación. Vive
 * en la Composable, no en el ViewModel: TextToSpeech necesita un Context de
 * Android y tiene su propio init asincrónico — no es responsabilidad de un
 * ViewModel manejar eso.
 */
class NavTts(context: Context) {
    var muted: Boolean by mutableStateOf(false)

    private val engine: TextToSpeech = TextToSpeech(context) { }.apply {
        language = Locale("es", "AR")
    }

    fun speak(text: String) {
        if (muted) return
        engine.speak(text, TextToSpeech.QUEUE_ADD, null, text.hashCode().toString())
    }

    fun shutdown() {
        engine.stop()
        engine.shutdown()
    }
}

@Composable
fun rememberNavTts(): NavTts {
    val context = LocalContext.current
    val tts = remember { NavTts(context) }
    DisposableEffect(Unit) {
        onDispose { tts.shutdown() }
    }
    return tts
}
