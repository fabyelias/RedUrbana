package com.redurbana.feature.lines

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mapbox.search.result.SearchSuggestion
import com.redurbana.core.ui.components.GlassCard
import com.redurbana.core.ui.components.RouteBadge
import com.redurbana.core.ui.theme.LineColorProvider
import com.redurbana.core.ui.theme.RedUrbanaColors
import com.redurbana.domain.transport.model.RouteRecommendation
import kotlin.math.roundToInt

@Composable
fun LinesScreen(
    modifier: Modifier = Modifier,
    viewModel: LinesViewModel = hiltViewModel(),
    onRouteSelected: (routeId: String) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (val state = uiState) {
            is LinesUiState.SearchingDestination -> SearchingDestinationContent(
                state = state,
                onQueryChanged = viewModel::onQueryChanged,
                onSuggestionSelected = viewModel::onSuggestionSelected,
            )
            is LinesUiState.Recommending -> RecommendingContent(
                state = state,
                onSearchAgain = viewModel::onSearchAgain,
                onRouteSelected = onRouteSelected,
            )
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
    onRouteSelected: (routeId: String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(text = "Líneas para llegar a", style = MaterialTheme.typography.bodyMedium, color = RedUrbanaColors.TextSecondary)
            Text(text = state.destinationName, style = MaterialTheme.typography.headlineSmall, color = RedUrbanaColors.TextPrimary)
        }
        TextButton(onClick = onSearchAgain) { Text("Cambiar destino") }
    }

    when {
        state.isLoading -> CircularProgressIndicator(color = RedUrbanaColors.AccentGreenPrimary)
        state.error != null -> Text(text = state.error, color = RedUrbanaColors.AlertRed)
        state.recommendations.isEmpty() -> Text(
            text = "No encontramos líneas cerca tuyo para ese destino.",
            color = RedUrbanaColors.TextSecondary,
        )
        else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(state.recommendations) { recommendation ->
                RecommendationCard(recommendation = recommendation, onClick = { onRouteSelected(recommendation.routeId.value) })
            }
        }
    }
}

@Composable
private fun RecommendationCard(recommendation: RouteRecommendation, onClick: () -> Unit) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        contentPadding = PaddingValues(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RouteBadge(
                routeShortName = recommendation.shortName,
                color = LineColorProvider.colorFor(colorSeed = recommendation.colorSeed),
            )
            Column {
                Text(
                    text = "A ${formatDistance(recommendation.distanceToOriginMeters)} de vos",
                    style = MaterialTheme.typography.bodyMedium,
                    color = RedUrbanaColors.TextPrimary,
                )
                val stopsText = recommendation.stopsToDestination?.let { "$it paradas hasta destino" }
                    ?: "Paradas hasta destino: no disponible"
                Text(text = stopsText, style = MaterialTheme.typography.bodySmall, color = RedUrbanaColors.TextSecondary)
            }
        }
    }
}

private fun formatDistance(meters: Double): String =
    if (meters >= 1000) "${(meters / 100).roundToInt() / 10.0} km" else "${meters.roundToInt()} m"
