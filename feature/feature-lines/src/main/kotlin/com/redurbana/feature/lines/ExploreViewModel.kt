package com.redurbana.feature.lines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mapbox.geojson.Point
import com.redurbana.core.location.DeviceLocationSource
import com.redurbana.domain.transport.model.GeoPoint
import com.redurbana.domain.transport.model.TripItinerary
import com.redurbana.domain.transport.usecase.GetTripItinerariesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

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
}

@HiltViewModel
class ExploreViewModel @Inject constructor(
    private val destinationSearchClient: DestinationSearchClient,
    private val getTripItineraries: GetTripItinerariesUseCase,
    private val locationSource: DeviceLocationSource,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ExploreUiState>(ExploreUiState.Idle)
    val uiState: StateFlow<ExploreUiState> = _uiState.asStateFlow()

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
            _uiState.value = ExploreUiState.Recommending(
                destination = current.point,
                destinationName = current.placeName,
                isLoading = false,
                error = "Todavía no pudimos ubicarte — probá de nuevo en unos segundos.",
            )
            return
        }
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
