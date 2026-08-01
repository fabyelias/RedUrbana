package com.redurbana.feature.vehicledetail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.redurbana.core.ui.components.GlassCard
import com.redurbana.core.ui.components.RouteBadge
import com.redurbana.core.ui.theme.LineColorProvider
import com.redurbana.core.ui.theme.RedUrbanaColors
import com.redurbana.domain.transport.model.VehicleStatus

@Composable
fun VehicleDetailScreen(
    modifier: Modifier = Modifier,
    viewModel: VehicleDetailViewModel = hiltViewModel(),
    onFollowClick: () -> Unit = {},
    onViewRouteClick: () -> Unit = {},
    onShareClick: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()

    GlassCard(modifier = modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
        when (val state = uiState) {
            is VehicleDetailUiState.Loading -> Text(
                text = "Cargando…",
                color = RedUrbanaColors.TextSecondary,
            )
            is VehicleDetailUiState.Error -> Text(
                text = state.message,
                color = RedUrbanaColors.AlertRed,
            )
            is VehicleDetailUiState.Success -> VehicleDetailContent(
                state = state,
                onFollowClick = { viewModel.onFollowToggled(); onFollowClick() },
                onViewRouteClick = onViewRouteClick,
                onShareClick = onShareClick,
            )
        }
    }
}

@Composable
private fun VehicleDetailContent(
    state: VehicleDetailUiState.Success,
    onFollowClick: () -> Unit,
    onViewRouteClick: () -> Unit,
    onShareClick: () -> Unit,
) {
    val color = LineColorProvider.colorFor(colorSeed = state.routeDetails.colorSeed)

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RouteBadge(routeShortName = state.routeDetails.shortName, color = color)
            Column {
                Text(
                    text = "Línea ${state.routeDetails.shortName} · Interno ${state.vehicle.internalNumber ?: "—"}",
                    style = MaterialTheme.typography.titleMedium,
                    color = RedUrbanaColors.TextPrimary,
                )
                Text(
                    text = state.routeDetails.company,
                    style = MaterialTheme.typography.bodyMedium,
                    color = RedUrbanaColors.TextSecondary,
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            DetailField(label = "VELOCIDAD", value = "${state.vehicle.speedKmh.toInt()} km/h")
            DetailField(
                label = "ESTADO",
                value = statusLabel(state.vehicle.status),
                valueColor = statusColor(state.vehicle.status),
            )
        }

        state.vehicle.positionConfidence?.let { confidence ->
            val style = com.redurbana.core.ui.theme.VehicleConfidenceStyle.from(confidence.percent)
            val reports = state.vehicle.contributingReports
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                DetailField(
                    label = "CONFIANZA DEL DATO",
                    value = buildString {
                        append("${confidence.percent}%")
                        style.label?.let { append(" · $it") }
                        if (reports != null) append(" · $reports reportes")
                    },
                    valueColor = if (style == com.redurbana.core.ui.theme.VehicleConfidenceStyle.ESTIMATED) {
                        RedUrbanaColors.WarningAmber
                    } else {
                        RedUrbanaColors.TextPrimary
                    },
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ActionButton(
                label = if (state.isFollowing) "Siguiendo" else "Seguir",
                isPrimary = true,
                onClick = onFollowClick,
                modifier = Modifier,
            )
            ActionButton(label = "Ver recorrido", isPrimary = false, onClick = onViewRouteClick)
            ActionButton(label = "Compartir", isPrimary = false, onClick = onShareClick)
        }
    }
}

@Composable
private fun DetailField(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color = RedUrbanaColors.TextPrimary) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = RedUrbanaColors.TextSecondary)
        Text(text = value, style = MaterialTheme.typography.bodyLarge, color = valueColor)
    }
}

@Composable
private fun ActionButton(label: String, isPrimary: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val backgroundColor = if (isPrimary) RedUrbanaColors.AccentGreenPrimary else RedUrbanaColors.SurfaceCard
    val textColor = if (isPrimary) androidx.compose.ui.graphics.Color.Black else RedUrbanaColors.TextPrimary
    Row(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = textColor)
    }
}

private fun statusLabel(status: VehicleStatus): String = when (status) {
    VehicleStatus.ON_TIME -> "● En horario"
    VehicleStatus.DELAYED -> "● Demorado"
    VehicleStatus.OUT_OF_SERVICE -> "● Fuera de servicio"
    VehicleStatus.UNKNOWN -> "● Desconocido"
}

private fun statusColor(status: VehicleStatus) = when (status) {
    VehicleStatus.ON_TIME -> RedUrbanaColors.AccentGreenPrimary
    VehicleStatus.DELAYED -> RedUrbanaColors.WarningAmber
    VehicleStatus.OUT_OF_SERVICE -> RedUrbanaColors.AlertRed
    VehicleStatus.UNKNOWN -> RedUrbanaColors.TextSecondary
}
