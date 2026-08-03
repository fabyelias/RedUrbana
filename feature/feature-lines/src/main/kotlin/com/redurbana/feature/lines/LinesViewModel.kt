package com.redurbana.feature.lines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mapbox.geojson.Point
import com.mapbox.search.result.SearchSuggestion
import com.redurbana.domain.transport.model.GeoPoint
import com.redurbana.domain.transport.model.RouteRecommendation
import com.redurbana.domain.transport.usecase.GetRouteRecommendationsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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

/**
 * TODO (roadmap, mismo TODO que StopsViewModel): reemplazar por la
 * ubicación real del usuario cuando core-location tenga
 * FusedDeviceLocationSource implementado.
 */
private val FALLBACK_ORIGIN = GeoPoint(latitude = -34.6095, longitude = -58.3924) // Congreso, CABA
private const val SEARCH_DEBOUNCE_MS = 350L

@HiltViewModel
class LinesViewModel @Inject constructor(
    private val destinationSearchClient: DestinationSearchClient,
    private val getRouteRecommendations: GetRouteRecommendationsUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow<LinesUiState>(LinesUiState.SearchingDestination())
    val uiState: StateFlow<LinesUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

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
            val proximity = Point.fromLngLat(FALLBACK_ORIGIN.longitude, FALLBACK_ORIGIN.latitude)
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
                    getRouteRecommendations(FALLBACK_ORIGIN, destination).getOrThrow()
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
