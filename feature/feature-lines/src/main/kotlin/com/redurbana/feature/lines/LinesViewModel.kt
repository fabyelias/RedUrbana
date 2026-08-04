package com.redurbana.feature.lines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mapbox.geojson.Point
import com.mapbox.search.result.SearchSuggestion
import com.redurbana.core.location.DeviceLocationSource
import com.redurbana.domain.transport.model.GeoPoint
import com.redurbana.domain.transport.model.RouteRecommendation
import com.redurbana.domain.transport.usecase.GetRouteRecommendationsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

sealed interface LinesUiState {
    data class SearchingDestination(
        val query: String = "",
        val suggestions: List<SearchSuggestion> = emptyList(),
        val isSearching: Boolean = false,
        val error: String? = null,
    ) : LinesUiState

    data class Recommending(
        val destinationName: String,
        val recommendations: List<RouteRecommendation>,
        val isLoading: Boolean = false,
        val error: String? = null,
    ) : LinesUiState
}

/** Se usa solo si el GPS real no está disponible (permiso no concedido, sin señal, timeout). */
private val FALLBACK_ORIGIN = GeoPoint(latitude = -34.6095, longitude = -58.3924) // Congreso, CABA
private const val SEARCH_DEBOUNCE_MS = 350L
private const val LOCATION_TIMEOUT_MS = 8_000L

@HiltViewModel
class LinesViewModel @Inject constructor(
    private val destinationSearchClient: DestinationSearchClient,
    private val getRouteRecommendations: GetRouteRecommendationsUseCase,
    private val locationSource: DeviceLocationSource,
) : ViewModel() {

    private val _uiState = MutableStateFlow<LinesUiState>(LinesUiState.SearchingDestination())
    val uiState: StateFlow<LinesUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    // Se resuelve una sola vez de forma perezosa (no hace falta un stream
    // continuo para "dónde estoy ahora"). Si no hay permiso o el GPS no
    // responde a tiempo, cae al fallback de Congreso en vez de trabar la
    // búsqueda — mismo criterio que StopsViewModel.
    private var resolvedOrigin: GeoPoint? = null

    private suspend fun currentOrigin(): GeoPoint {
        resolvedOrigin?.let { return it }
        val real = withTimeoutOrNull(LOCATION_TIMEOUT_MS) {
            runCatching { locationSource.observeLocation().first() }.getOrNull()
        }
        val origin = real?.let { GeoPoint(it.latitude, it.longitude) } ?: FALLBACK_ORIGIN
        resolvedOrigin = origin
        return origin
    }

    fun onQueryChanged(query: String) {
        val current = _uiState.value as? LinesUiState.SearchingDestination ?: LinesUiState.SearchingDestination()
        _uiState.value = current.copy(query = query, error = null)

        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.value = current.copy(query = query, suggestions = emptyList(), isSearching = false, error = null)
            return
        }
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            _uiState.value = (_uiState.value as? LinesUiState.SearchingDestination ?: current).copy(isSearching = true)
            val origin = currentOrigin()
            val proximity = Point.fromLngLat(origin.longitude, origin.latitude)
            destinationSearchClient.search(query, proximity)
                .onSuccess { suggestions ->
                    _uiState.value = LinesUiState.SearchingDestination(query = query, suggestions = suggestions, isSearching = false)
                }
                .onFailure {
                    _uiState.value = LinesUiState.SearchingDestination(
                        query = query,
                        isSearching = false,
                        error = "No pudimos buscar ese destino. Probá de nuevo.",
                    )
                }
        }
    }

    fun onSuggestionSelected(suggestion: SearchSuggestion) {
        viewModelScope.launch {
            _uiState.value = LinesUiState.Recommending(
                destinationName = suggestion.name,
                recommendations = emptyList(),
                isLoading = true,
            )
            destinationSearchClient.resolve(suggestion)
                .mapCatching { result ->
                    val destination = GeoPoint(result.coordinate.latitude(), result.coordinate.longitude())
                    getRouteRecommendations(currentOrigin(), destination).getOrThrow()
                }
                .onSuccess { recommendations ->
                    _uiState.value = LinesUiState.Recommending(
                        destinationName = suggestion.name,
                        recommendations = recommendations,
                        isLoading = false,
                    )
                }
                .onFailure {
                    _uiState.value = LinesUiState.Recommending(
                        destinationName = suggestion.name,
                        recommendations = emptyList(),
                        isLoading = false,
                        error = "No pudimos calcular líneas recomendadas para ese destino.",
                    )
                }
        }
    }

    fun onSearchAgain() {
        searchJob?.cancel()
        _uiState.value = LinesUiState.SearchingDestination()
    }
}
