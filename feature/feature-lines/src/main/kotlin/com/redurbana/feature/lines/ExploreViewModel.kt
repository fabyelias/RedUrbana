package com.redurbana.feature.lines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mapbox.geojson.Point
import com.mapbox.search.result.SearchSuggestion
import com.redurbana.core.location.DeviceLocationSource
import com.redurbana.domain.transport.model.DrivingRoute
import com.redurbana.domain.transport.model.GeoPoint
import com.redurbana.domain.transport.model.TripItinerary
import com.redurbana.domain.transport.usecase.GetDrivingRouteUseCase
import com.redurbana.domain.transport.usecase.GetTripItinerariesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A qué modo de viaje aplica la búsqueda al confirmar destino — A pie/Bici quedan para más adelante. */
enum class TravelMode { TRANSIT, CAR }

/**
 * Estado de la pantalla mapa-primero: el mapa siempre está visible, lo que
 * cambia es el contenido del panel deslizable de abajo. Reemplaza al viejo
 * flujo de búsqueda por texto (LinesUiState.SearchingDestination) — ahora la
 * única forma de elegir destino es tocando el mapa.
 */
sealed interface ExploreUiState {
    data object Idle : ExploreUiState

    data class ConfirmingDestination(val point: GeoPoint, val placeName: String) : ExploreUiState

    data class Recommending(
        val destination: GeoPoint,
        val destinationName: String,
        val itineraries: List<TripItinerary> = emptyList(),
        val isLoading: Boolean = false,
        val error: String? = null,
    ) : ExploreUiState

    data class ItineraryDetail(val itinerary: TripItinerary, val previous: Recommending) : ExploreUiState

    /** Modo Auto: sin lista de alternativas ni transbordos, una sola ruta real manejando. */
    data class DrivingResult(
        val destination: GeoPoint,
        val destinationName: String,
        val route: DrivingRoute? = null,
        val isLoading: Boolean = false,
        val error: String? = null,
    ) : ExploreUiState
}

@HiltViewModel
class ExploreViewModel @Inject constructor(
    private val destinationSearchClient: DestinationSearchClient,
    private val getTripItineraries: GetTripItinerariesUseCase,
    private val getDrivingRoute: GetDrivingRouteUseCase,
    private val locationSource: DeviceLocationSource,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ExploreUiState>(ExploreUiState.Idle)
    val uiState: StateFlow<ExploreUiState> = _uiState.asStateFlow()

    private val _travelMode = MutableStateFlow(TravelMode.TRANSIT)
    val travelMode: StateFlow<TravelMode> = _travelMode.asStateFlow()

    fun onTravelModeSelected(mode: TravelMode) {
        _travelMode.value = mode
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchSuggestions = MutableStateFlow<List<SearchSuggestion>>(emptyList())
    val searchSuggestions: StateFlow<List<SearchSuggestion>> = _searchSuggestions.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private var searchJob: Job? = null

    /**
     * Debounce manual (300ms): sin esto cada letra tipeada dispara un
     * request — cancela el anterior con searchJob así solo el último
     * query en pie termina pisando las sugerencias.
     */
    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()
        if (query.isBlank()) {
            _searchSuggestions.value = emptyList()
            _isSearching.value = false
            return
        }
        searchJob = viewModelScope.launch {
            delay(300)
            _isSearching.value = true
            val proximity = _liveLocation.value?.let { Point.fromLngLat(it.longitude, it.latitude) }
            destinationSearchClient.searchSuggestions(query, proximity)
                .onSuccess { suggestions -> _searchSuggestions.value = suggestions }
                .onFailure { _searchSuggestions.value = emptyList() }
            _isSearching.value = false
        }
    }

    fun onSearchSuggestionSelected(suggestion: SearchSuggestion) {
        searchJob?.cancel()
        _searchQuery.value = ""
        _searchSuggestions.value = emptyList()
        viewModelScope.launch {
            destinationSearchClient.selectSuggestion(suggestion)
                .onSuccess { result ->
                    val coordinate = result.coordinate
                    val point = GeoPoint(latitude = coordinate.latitude(), longitude = coordinate.longitude())
                    _uiState.value = ExploreUiState.ConfirmingDestination(
                        point = point,
                        placeName = result.name,
                    )
                }
        }
    }

    fun onSearchCleared() {
        searchJob?.cancel()
        _searchQuery.value = ""
        _searchSuggestions.value = emptyList()
        _isSearching.value = false
    }

    /**
     * Ubicación real, en vivo — se sigue actualizando todo el tiempo que
     * esta pantalla esté abierta (no una sola vez): así el punto "estás acá"
     * del mapa te sigue si te movés, Y las alternativas que se calculan más
     * tarde en la sesión usan tu posición ACTUAL, no la que tenías al abrir
     * la app. Antes esto se cacheaba una sola vez, y si el primer intento
     * caía al fallback (GPS lento/sin señal), quedaba pegado en ese fallback
     * para siempre, aunque el GPS real respondiera después — eso hacía que
     * las alternativas parecieran "no tener que ver con dónde estoy".
     */
    private val _liveLocation = MutableStateFlow<GeoPoint?>(null)
    val liveLocation: StateFlow<GeoPoint?> = _liveLocation.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching {
                locationSource.observeLocation().collect { sample ->
                    _liveLocation.value = GeoPoint(sample.latitude, sample.longitude)
                }
            }
        }
    }

    /**
     * Para que ExploreMapScreen centre la cámara inicial sin duplicar la
     * lógica de GPS. Espera sin límite de tiempo al primer fix real — antes
     * había un timeout de 8s que caía a un fallback fijo en Congreso, que se
     * mostraba como si fuera la ubicación real del usuario cuando el GPS
     * tardaba más de eso (y en dispositivos sin chip GPS, nunca corregía).
     */
    suspend fun initialCameraTarget(): GeoPoint = _liveLocation.filterNotNull().first()

    fun onMapTapped(point: GeoPoint) {
        viewModelScope.launch {
            _uiState.value = ExploreUiState.ConfirmingDestination(point = point, placeName = "Buscando lugar…")
            destinationSearchClient.reverseGeocode(Point.fromLngLat(point.longitude, point.latitude))
                .onSuccess { result ->
                    _uiState.value = ExploreUiState.ConfirmingDestination(point = point, placeName = result.name ?: "Este punto del mapa")
                }
                .onFailure {
                    _uiState.value = ExploreUiState.ConfirmingDestination(point = point, placeName = "Este punto del mapa")
                }
        }
    }

    fun onDestinationConfirmed() {
        val current = _uiState.value as? ExploreUiState.ConfirmingDestination ?: return
        val origin = _liveLocation.value
        if (origin == null) {
            // Sin fallback silencioso a Congreso: calcular alternativas
            // "desde Congreso" cuando el usuario está en otro lado daba
            // resultados que no tenían nada que ver con dónde está parado.
            showNoLocationError(current)
            return
        }
        when (_travelMode.value) {
            TravelMode.TRANSIT -> confirmTransit(origin, current)
            TravelMode.CAR -> confirmDriving(origin, current)
        }
    }

    private fun showNoLocationError(current: ExploreUiState.ConfirmingDestination) {
        val error = "Todavía no pudimos ubicarte — probá de nuevo en unos segundos."
        _uiState.value = when (_travelMode.value) {
            TravelMode.TRANSIT -> ExploreUiState.Recommending(
                destination = current.point,
                destinationName = current.placeName,
                isLoading = false,
                error = error,
            )
            TravelMode.CAR -> ExploreUiState.DrivingResult(
                destination = current.point,
                destinationName = current.placeName,
                isLoading = false,
                error = error,
            )
        }
    }

    private fun confirmTransit(origin: GeoPoint, current: ExploreUiState.ConfirmingDestination) {
        viewModelScope.launch {
            _uiState.value = ExploreUiState.Recommending(
                destination = current.point,
                destinationName = current.placeName,
                isLoading = true,
            )
            getTripItineraries(origin, current.point)
                .onSuccess { itineraries ->
                    _uiState.value = ExploreUiState.Recommending(
                        destination = current.point,
                        destinationName = current.placeName,
                        itineraries = itineraries,
                        isLoading = false,
                    )
                }
                .onFailure {
                    _uiState.value = ExploreUiState.Recommending(
                        destination = current.point,
                        destinationName = current.placeName,
                        isLoading = false,
                        error = "No pudimos calcular alternativas para ese destino.",
                    )
                }
        }
    }

    private fun confirmDriving(origin: GeoPoint, current: ExploreUiState.ConfirmingDestination) {
        viewModelScope.launch {
            _uiState.value = ExploreUiState.DrivingResult(
                destination = current.point,
                destinationName = current.placeName,
                isLoading = true,
            )
            getDrivingRoute(origin, current.point)
                .onSuccess { route ->
                    _uiState.value = ExploreUiState.DrivingResult(
                        destination = current.point,
                        destinationName = current.placeName,
                        route = route,
                        isLoading = false,
                    )
                }
                .onFailure {
                    _uiState.value = ExploreUiState.DrivingResult(
                        destination = current.point,
                        destinationName = current.placeName,
                        isLoading = false,
                        error = "No pudimos calcular una ruta en auto para ese destino.",
                    )
                }
        }
    }

    fun onCancelDestination() {
        _uiState.value = ExploreUiState.Idle
    }

    fun onItinerarySelected(itinerary: TripItinerary) {
        val recommending = _uiState.value as? ExploreUiState.Recommending ?: return
        _uiState.value = ExploreUiState.ItineraryDetail(itinerary = itinerary, previous = recommending)
    }

    fun onBackToAlternatives() {
        val detail = _uiState.value as? ExploreUiState.ItineraryDetail ?: return
        _uiState.value = detail.previous
    }
}
