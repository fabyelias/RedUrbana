package com.redurbana.feature.stops

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.redurbana.core.ui.components.GlassCard
import com.redurbana.core.ui.components.RouteBadge
import com.redurbana.core.ui.theme.LineColorProvider
import com.redurbana.core.ui.theme.RedUrbanaColors
import com.redurbana.domain.transport.model.Stop

@Composable
fun StopsScreen(
    modifier: Modifier = Modifier,
    viewModel: StopsViewModel = hiltViewModel(),
    onStopClick: (stopId: String) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Paradas cercanas",
            style = MaterialTheme.typography.headlineMedium,
            color = RedUrbanaColors.TextPrimary,
        )

        when (val state = uiState) {
            is StopsUiState.Loading -> Text(
                text = "Buscando paradas cerca tuyo…",
                color = RedUrbanaColors.TextSecondary,
            )
            is StopsUiState.Error -> Text(
                text = state.message,
                color = RedUrbanaColors.AlertRed,
            )
            is StopsUiState.Success -> {
                if (state.stops.isEmpty()) {
                    Text(
                        text = "No encontramos paradas en esta zona.",
                        color = RedUrbanaColors.TextSecondary,
                    )
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(state.stops) { stop ->
                            StopCard(stop = stop, onClick = { onStopClick(stop.stopId.value) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StopCard(stop: Stop, onClick: () -> Unit) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(14.dp),
    ) {
        Text(
            text = stop.name,
            style = MaterialTheme.typography.titleMedium,
            color = RedUrbanaColors.TextPrimary,
        )
        Row(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            stop.routesServed.take(6).forEach { routeId ->
                RouteBadge(
                    routeShortName = routeId.value,
                    color = LineColorProvider.colorFor(colorSeed = routeId.value),
                )
            }
        }
    }
}
