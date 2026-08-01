package com.redurbana.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.redurbana.core.ui.components.LiveBadge
import com.redurbana.core.ui.theme.RedUrbanaColors

/**
 * Punto de entrada de la pantalla Inicio. Reproduce el header de la referencia:
 * saludo + estado de confiabilidad + "Tránsito en vivo". El mapa embebido y
 * los paneles inferiores (Próximos colectivos / Paradas cercanas / Favoritos)
 * se completan en el siguiente paso del roadmap sobre este mismo esqueleto.
 */
@Composable
fun DashboardScreen(
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "¡Hola, Fabián!",
                style = MaterialTheme.typography.headlineMedium,
                color = RedUrbanaColors.TextPrimary,
            )
            LiveBadge()
        }

        GlassCard(contentPadding = PaddingValues(16.dp)) {
            when (val state = uiState) {
                is DashboardUiState.Loading -> Text(
                    text = "Cargando tránsito en vivo…",
                    color = RedUrbanaColors.TextSecondary,
                )
                is DashboardUiState.Error -> Text(
                    text = state.message,
                    color = RedUrbanaColors.AlertRed,
                )
                is DashboardUiState.Success -> Text(
                    text = "${state.nearbyVehicles.size} colectivos en tu zona · " +
                        "Índice de confiabilidad ${state.reliabilityPercent}% Excelente",
                    style = MaterialTheme.typography.bodyMedium,
                    color = RedUrbanaColors.AccentGreenPrimary,
                )
            }
        }
    }
}
