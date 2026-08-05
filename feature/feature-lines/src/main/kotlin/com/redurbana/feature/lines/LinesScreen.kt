package com.redurbana.feature.lines

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mapbox.search.result.SearchSuggestion
import com.redurbana.core.ui.components.GlassCard
import com.redurbana.core.ui.components.RouteBadge
import com.redurbana.core.ui.theme.LineColorProvider
import com.redurbana.core.ui.theme.RedUrbanaColors
import com.redurbana.domain.transport.model.TripItinerary
import com.redurbana.domain.transport.model.TripLeg
import kotlin.math.roundToInt

@Composable
fun LinesScreen(
    modifier: Modifier = Modifier,
    viewModel: LinesViewModel = hiltViewModel(),
    onItinerarySelected: (index: Int) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TravelModeTabs()

        when (val state = uiState) {
            is LinesUiState.SearchingDestination -> SearchingDestinationContent(
                state = state,
                onQueryChanged = viewModel::onQueryChanged,
                onSuggestionSelected = viewModel::onSuggestionSelected,
            )
            is LinesUiState.Recommending -> RecommendingContent(
                state = state,
                onSearchAgain = viewModel::onSearchAgain,
                onItinerarySelected = onItinerarySelected,
            )
        }
    }
}

/**
 * Igual que en Google Maps, la fila de modos de viaje va siempre visible
 * arriba de la búsqueda/resultados. Solo "Transporte público" tiene datos
 * reales detrás — Auto/A pie/Bici quedan visualmente presentes pero
 * deshabilitadas: mostrarlas como funcionales sin tener ni rutas viales ni
 * ciclovías reales sería engañoso.
 */
@Composable
private fun TravelModeTabs(modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ModeTab(label = "Transporte público", enabled = true)
        ModeTab(label = "Auto", enabled = false)
        ModeTab(label = "A pie", enabled = false)
        ModeTab(label = "Bici", enabled = false)
    }
}

@Composable
private fun ModeTab(label: String, enabled: Boolean) {
    val background = if (enabled) RedUrbanaColors.AccentGreenPrimary else RedUrbanaColors.SurfaceCard
    val textColor = if (enabled) androidx.compose.ui.graphics.Color.Black else RedUrbanaColors.TextTertiary
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(background)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = textColor)
        if (!enabled) {
            Text(text = "Próximamente", style = MaterialTheme.typography.labelSmall, color = textColor)
        }
    }
}

@Composable
private fun SearchingDestinationContent(
    state: LinesUiState.SearchingDestination,
    onQueryChanged: (String) -> Unit,
    onSuggestionSelected: (SearchSuggestion) -> Unit,
) {
    Text(
        text = "¿A dónde vas?",
        style = MaterialTheme.typography.headlineMedium,
        color = RedUrbanaColors.TextPrimary,
    )
    OutlinedTextField(
        value = state.query,
        onValueChange = onQueryChanged,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("Buscar destino…") },
        singleLine = true,
    )

    when {
        state.isSearching -> Text(text = "Buscando…", color = RedUrbanaColors.TextSecondary)
        state.error != null -> Text(text = state.error, color = RedUrbanaColors.AlertRed)
        state.suggestions.isNotEmpty() -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.suggestions) { suggestion ->
                SuggestionCard(suggestion = suggestion, onClick = { onSuggestionSelected(suggestion) })
            }
        }
        state.query.isNotBlank() -> Text(text = "Sin resultados todavía.", color = RedUrbanaColors.TextSecondary)
    }
}

@Composable
private fun SuggestionCard(suggestion: SearchSuggestion, onClick: () -> Unit) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        contentPadding = PaddingValues(14.dp),
    ) {
        Text(text = suggestion.name, style = MaterialTheme.typography.titleMedium, color = RedUrbanaColors.TextPrimary)
        val subtitle = suggestion.fullAddress ?: suggestion.descriptionText
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = RedUrbanaColors.TextSecondary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun RecommendingContent(
    state: LinesUiState.Recommending,
    onSearchAgain: () -> Unit,
    onItinerarySelected: (index: Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(text = "Alternativas para llegar a", style = MaterialTheme.typography.bodyMedium, color = RedUrbanaColors.TextSecondary)
            Text(text = state.destinationName, style = MaterialTheme.typography.headlineSmall, color = RedUrbanaColors.TextPrimary)
        }
        TextButton(onClick = onSearchAgain) { Text("Cambiar destino") }
    }

    when {
        state.isLoading -> CircularProgressIndicator(color = RedUrbanaColors.AccentGreenPrimary)
        state.error != null -> Text(text = state.error, color = RedUrbanaColors.AlertRed)
        state.itineraries.isEmpty() -> Text(
            text = "No encontramos alternativas cerca tuyo para ese destino.",
            color = RedUrbanaColors.TextSecondary,
        )
        else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(state.itineraries.withIndex().toList()) { (index, itinerary) ->
                TripItineraryCard(itinerary = itinerary, onClick = { onItinerarySelected(index) })
            }
        }
    }
}

@Composable
private fun TripItineraryCard(itinerary: TripItinerary, onClick: () -> Unit) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        contentPadding = PaddingValues(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "${itinerary.totalMinutes} min", style = MaterialTheme.typography.titleMedium, color = RedUrbanaColors.TextPrimary)
            Text(
                text = if (itinerary.transferCount == 0) "Directo" else "${itinerary.transferCount} transbordo",
                style = MaterialTheme.typography.labelSmall,
                color = RedUrbanaColors.TextSecondary,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            itinerary.legs.forEachIndexed { index, leg ->
                when (leg) {
                    is TripLeg.Walk -> WalkChip(distanceMeters = leg.distanceMeters)
                    is TripLeg.Transit -> RouteBadge(
                        routeShortName = leg.shortName,
                        color = LineColorProvider.colorFor(colorSeed = leg.colorSeed),
                    )
                }
                if (index != itinerary.legs.lastIndex) {
                    Text(text = "›", style = MaterialTheme.typography.bodyMedium, color = RedUrbanaColors.TextTertiary)
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        val nextDeparture = itinerary.legs.filterIsInstance<TripLeg.Transit>().firstOrNull()?.nextDepartureMinutes
        Text(
            text = when {
                nextDeparture == null -> "Sin dato de salida en vivo todavía"
                nextDeparture <= 0 -> "Está llegando"
                else -> "Sale en $nextDeparture min"
            },
            style = MaterialTheme.typography.bodySmall,
            color = RedUrbanaColors.TextSecondary,
        )
    }
}

@Composable
private fun WalkChip(distanceMeters: Double) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(RedUrbanaColors.SurfaceElevated)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = "A pie · ${formatDistance(distanceMeters)}",
            style = MaterialTheme.typography.labelSmall,
            color = RedUrbanaColors.TextSecondary,
        )
    }
}

private fun formatDistance(meters: Double): String =
    if (meters >= 1000) "${(meters / 100).roundToInt() / 10.0} km" else "${meters.roundToInt()} m"
