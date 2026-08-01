package com.redurbana.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val crowdSourcingEnabled by viewModel.crowdSourcingEnabled.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = "Ajustes",
            style = MaterialTheme.typography.headlineMedium,
            color = RedUrbanaColors.TextPrimary,
        )

        GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Colaborar con la comunidad",
                        style = MaterialTheme.typography.titleMedium,
                        color = RedUrbanaColors.TextPrimary,
                    )
                    Text(
                        text = "Cuando detectemos que probablemente vas en un colectivo, " +
                            "compartimos tu posición de forma anónima para ayudar a calcular " +
                            "ubicaciones más precisas para el resto de los usuarios. " +
                            "Nunca se comparte tu identidad ni se guarda un historial de viajes " +
                            "asociado a vos.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = RedUrbanaColors.TextSecondary,
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                Switch(
                    checked = crowdSourcingEnabled,
                    onCheckedChange = viewModel::onCrowdSourcingToggled,
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = RedUrbanaColors.AccentGreenPrimary,
                    ),
                )
            }
        }

        if (crowdSourcingEnabled) {
            GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
                Text(
                    text = "✓ Activo — solo se reporta tu ubicación mientras el patrón de " +
                        "movimiento sea compatible con ir en colectivo (nunca caminando o en auto).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = RedUrbanaColors.AccentGreenPrimary,
                )
            }
        }

        // Resto de Ajustes (idioma, tema, notificaciones) — pendiente, mismo
        // criterio de placeholder honesto que el resto del roadmap.
        Text(
            text = "Idioma, tema y notificaciones se agregan en un próximo paso.",
            style = MaterialTheme.typography.bodyMedium,
            color = RedUrbanaColors.TextTertiary,
        )
    }
}
