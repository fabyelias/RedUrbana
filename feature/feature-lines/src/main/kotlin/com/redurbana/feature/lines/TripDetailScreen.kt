package com.redurbana.feature.lines

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.redurbana.core.ui.components.GlassCard
import com.redurbana.core.ui.components.RouteBadge
import com.redurbana.core.ui.theme.LineColorProvider
import com.redurbana.core.ui.theme.RedUrbanaColors
import com.redurbana.domain.transport.model.Stop
import com.redurbana.domain.transport.model.TripItinerary
import com.redurbana.domain.transport.model.TripLeg
import kotlin.math.roundToInt

/**
 * Detalle paso a paso de UN itinerario ya calculado. No recibe su propio
 * ViewModel: `viewModel` es el MISMO `LinesViewModel` de la pantalla de
 * alternativas (compartido a nivel de grafo de navegación, ver
 * `RedUrbanaNavHost`) — esta pantalla solo lee `itineraries[index]`, nunca
 * vuelve a calcular nada.
 */
@Composable
fun TripDetailScreen(
    viewModel: LinesViewModel,
    itineraryIndex: Int,
    modifier: Modifier = Modifier,
    onStartTrip: (routeId: String, alightingStop: Stop) -> Unit = { _, _ -> },
) {
    val uiState by viewModel.uiState.collectAsState()
    val itinerary = (uiState as? LinesUiState.Recommending)?.itineraries?.getOrNull(itineraryIndex)

    if (itinerary == null) {
        Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
            Text(text = "Este itinerario ya no está disponible.", color = RedUrbanaColors.TextSecondary)
        }
        return
    }

    val lastTransit = itinerary.legs.filterIsInstance<TripLeg.Transit>().lastOrNull()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = "${itinerary.totalMinutes} min · ${if (itinerary.transferCount == 0) "Directo" else "${itinerary.transferCount} transbordo"}",
            style = MaterialTheme.typography.headlineSmall,
            color = RedUrbanaColors.TextPrimary,
        )

        TripPreviewMap(legs = itinerary.legs, modifier = Modifier.fillMaxWidth())

        LazyColumn(modifier = Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(itinerary.legs.withIndex().toList()) { (index, leg) -> TripStepCard(leg = leg, nextLeg = itinerary.legs.getOrNull(index + 1)) }
        }

        Button(
            onClick = { lastTransit?.let { onStartTrip(it.routeId.value, it.alightingStop) } },
            enabled = lastTransit != null,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Iniciar viaje")
        }
    }
}

@Composable
private fun TripStepCard(leg: TripLeg, nextLeg: TripLeg?) {
    GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(12.dp)) {
        when (leg) {
            is TripLeg.Walk -> {
                val destinationName = (nextLeg as? TripLeg.Transit)?.boardingStop?.name
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    WalkChip(distanceMeters = leg.distanceMeters)
                    Text(
                        text = "Caminá ${leg.durationMinutes} min" + (destinationName?.let { " hasta $it" } ?: " hasta destino"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = RedUrbanaColors.TextPrimary,
                    )
                }
            }
            is TripLeg.Transit -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    RouteBadge(routeShortName = leg.shortName, color = LineColorProvider.colorFor(colorSeed = leg.colorSeed))
                    Column {
                        Text(
                            text = "Tomá la línea ${leg.shortName} — ${leg.stopsCount} paradas",
                            style = MaterialTheme.typography.bodyMedium,
                            color = RedUrbanaColors.TextPrimary,
                        )
                        Text(
                            text = "Bajate en ${leg.alightingStop.name} · ${leg.estimatedMinutes} min",
                            style = MaterialTheme.typography.bodySmall,
                            color = RedUrbanaColors.TextSecondary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WalkChip(distanceMeters: Double) {
    Text(
        text = formatDistance(distanceMeters),
        style = MaterialTheme.typography.labelSmall,
        color = RedUrbanaColors.TextSecondary,
    )
}

private fun formatDistance(meters: Double): String =
    if (meters >= 1000) "${(meters / 100).roundToInt() / 10.0} km" else "${meters.roundToInt()} m"
