package com.redurbana.feature.alerts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.redurbana.core.ui.components.GlassCard
import com.redurbana.core.ui.theme.RedUrbanaColors
import com.redurbana.domain.transport.model.AlertSeverity
import com.redurbana.domain.transport.model.AlertType
import com.redurbana.domain.transport.model.ServiceAlert

@Composable
fun AlertsScreen(
    modifier: Modifier = Modifier,
    viewModel: AlertsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Alertas",
            style = MaterialTheme.typography.headlineMedium,
            color = RedUrbanaColors.TextPrimary,
        )

        when (val state = uiState) {
            is AlertsUiState.Loading -> Text(
                text = "Cargando alertas…",
                color = RedUrbanaColors.TextSecondary,
            )
            is AlertsUiState.Error -> Text(
                text = state.message,
                color = RedUrbanaColors.AlertRed,
            )
            is AlertsUiState.Success -> {
                if (state.alerts.isEmpty()) {
                    EmptyAlertsState()
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(state.alerts) { alert -> AlertCard(alert) }
                    }
                }
            }
        }
    }
}

/**
 * Estado vacío tratado como una noticia positiva, no como una ausencia de
 * contenido — coherente con el resto del diseño (Índice de confiabilidad).
 */
@Composable
private fun EmptyAlertsState() {
    GlassCard(contentPadding = PaddingValues(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(text = "✅", style = MaterialTheme.typography.titleLarge)
            Column {
                Text(
                    text = "Sin alertas activas",
                    style = MaterialTheme.typography.titleMedium,
                    color = RedUrbanaColors.TextPrimary,
                )
                Text(
                    text = "No hay desvíos, cortes ni demoras reportadas en tus líneas.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = RedUrbanaColors.TextSecondary,
                )
            }
        }
    }
}

@Composable
private fun AlertCard(alert: ServiceAlert) {
    val (icon, color) = when (alert.severity) {
        AlertSeverity.SEVERE -> "🔴" to RedUrbanaColors.AlertRed
        AlertSeverity.WARNING -> "🟠" to RedUrbanaColors.WarningAmber
        AlertSeverity.INFO -> "🔵" to RedUrbanaColors.AccentBlue
    }
    GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
            Text(text = icon)
            Column {
                Text(
                    text = alertTypeLabel(alert.type),
                    style = MaterialTheme.typography.titleMedium,
                    color = color,
                )
                Text(
                    text = alert.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = RedUrbanaColors.TextSecondary,
                )
                Text(
                    text = "Líneas: " + alert.affectedRoutes.joinToString(", ") { it.value },
                    style = MaterialTheme.typography.labelSmall,
                    color = RedUrbanaColors.TextTertiary,
                )
            }
        }
    }
}

private fun alertTypeLabel(type: AlertType): String = when (type) {
    AlertType.DETOUR -> "Desvío"
    AlertType.CLOSURE -> "Corte"
    AlertType.PROTEST -> "Manifestación"
    AlertType.ACCIDENT -> "Accidente"
    AlertType.STRIKE -> "Paro"
}
