package com.redurbana.app.more

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.redurbana.core.ui.theme.RedUrbanaColors

/**
 * Placeholder visual consistente (mismo tema, mismo tono de "invitación a
 * volver") para las secciones que todavía no tienen su propio módulo
 * implementado. Reemplazar por la pantalla real de cada feature a medida
 * que se van completando (roadmap: Paradas → Favoritos → Historial → Ajustes).
 */
@Composable
fun ComingSoonScreen(title: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = title, style = MaterialTheme.typography.headlineMedium, color = RedUrbanaColors.TextPrimary)
        Text(
            text = "Esta sección se completa en un próximo paso del roadmap.",
            style = MaterialTheme.typography.bodyMedium,
            color = RedUrbanaColors.TextSecondary,
        )
    }
}
