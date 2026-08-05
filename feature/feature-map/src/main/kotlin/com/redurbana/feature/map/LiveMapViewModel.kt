package com.redurbana.feature.map

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.redurbana.core.location.DeviceLocationSource
import com.redurbana.domain.crowdsourcing.usecase.SetActiveCrowdSourcingTripUseCase
import com.redurbana.domain.transport.FollowedVehicleController
import com.redurbana.domain.transport.model.GeoPoint
import com.redurbana.domain.transport.model.RouteId
import com.redurbana.domain.transport.model.VehiclePosition
import com.redurbana.domain.transport.model.distanceMeters
import com.redurbana.domain.transport.usecase.ObserveVehiclesOnRouteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

private const val LOCATION_TIMEOUT_MS = 8_000L
private const val ARRIVAL_THRESHOLD_METERS = 200.0

sealed interface MapUiState {
    data object Loading : MapUiState
    data class Success(
        val vehicles: List<VehiclePosition>,
        /** Línea elegida en el buscador de destino; null = no se dibuja ningún vehículo. */
        val selectedRouteId: String? = null,
        val selectedVehicleId: String? = null,
        val isFollowing: Boolean = false,
        /** Última posición conocida del vehículo seguido, o null si no se sigue a nadie. */
        val cameraTarget: GeoPoint? = null,
    ) : MapUiState
    data class Error(val message: String) : MapUiState
}

/**
 * Igual que DashboardViewModel: solo conoce el UseCase, no el provider.
 *
 * A diferencia de una versión anterior, esto NO filtra por el viewport del
 * mapa (observeVehiclesInBounds): la línea elegida puede estar en cualquier
 * lado, lejos de dónde arranca la cámara por defecto — filtrar por bounds
 * significaba que si el usuario no estaba ya mirando esa zona, nunca veía
 * su colectivo. observeVehiclesOnRoute trae la línea elegida sin importar
 * dónde esté la cámara.
 *
 * El centrado inicial de cámara usa la ubicación REAL del dispositivo (con
 * fallback a Congreso solo si no hay GPS a tiempo), no la posición de un
 * vehículo cualquiera de la línea: un colectivo real puede tener un
 * recorrido de decenas de km (ej. la 60 va de La Boca a Tigre), así que
 * "centrar en el primer vehículo" podía abrir el mapa en cualquier punto
 * del recorrido, lejos de donde está el usuario. El seguimiento de cámara
 * (cameraTarget) tiene entonces dos disparadores: seguir un vehículo
 * explícitamente (FollowedVehicleController), o la ubicación real del
 * usuario una sola vez al entrar. Después de ese primer centrado el
 * usuario puede panear/hacer zoom libremente sin que lo interrumpamos.
 */
@HiltViewModel
class LiveMapViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val observeVehiclesOnRoute: ObserveVehiclesOnRouteUseCase,
    private val followedVehicleController: FollowedVehicleController,
    private val setActiveCrowdSourcingTrip: SetActiveCrowdSourcingTripUseCase,
    private val locationSource: DeviceLocationSource,
    private val tripArrivalNotifier: TripArrivalNotifier,
) : ViewModel() {

    private val selectedRouteId: String? = savedStateHandle.get<String>("routeId")
    private val alightingLat: Double? = savedStateHandle.get<Double>("alightingLat")
    private val alightingLng: Double? = savedStateHandle.get<Double>("alightingLng")
    private val alightingStopName: String? = savedStateHandle.get<String>("alightingStopName")
    private val alightingPoint: GeoPoint? = if (alightingLat != null && alightingLng != null) {
        GeoPoint(alightingLat, alightingLng)
    } else {
        null
    }

    private val _uiState = MutableStateFlow<MapUiState>(MapUiState.Loading)
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    /** Null hasta que se resuelve el GPS real (o el timeout, con fallback a Congreso); una sola vez. */
    private val realLocation = MutableStateFlow<GeoPoint?>(null)

    private var arrivalNotified = false

    init {
        // Sin línea elegida (se entró directo desde el bottom nav, sin pasar
        // por el buscador de destino): NO nos suscribimos a nada. Evita
        // procesar/dibujar la flota completa sin necesidad.
        if (selectedRouteId != null) {
            observeSelectedRoute(RouteId(selectedRouteId))
            fetchRealLocationOnce()
            // El reporte anónimo de ubicación (crowdsourcing) queda ligado a
            // esta pantalla: arranca acá, se corta en onCleared(). Si el
            // usuario no tiene el opt-in prendido en Ajustes, esto no hace
            // nada — LocationReporter chequea eso internamente.
            setActiveCrowdSourcingTrip(RouteId(selectedRouteId))
            if (alightingPoint != null) watchArrival(alightingPoint)
        } else {
            _uiState.value = MapUiState.Success(vehicles = emptyList(), selectedRouteId = null)
        }
    }

    /**
     * Solo corre cuando se llega acá desde "Iniciar viaje" (hay parada de
     * bajada). Observa el GPS de forma CONTINUA (no una sola vez como
     * [fetchRealLocationOnce]) mientras esta pantalla siga viva — sin
     * Foreground Service todavía, no sobrevive si la app pasa a background
     * por mucho tiempo o el proceso muere (ver TripArrivalNotifier).
     */
    private fun watchArrival(target: GeoPoint) {
        viewModelScope.launch {
            locationSource.observeLocation().collect { sample ->
                if (arrivalNotified) return@collect
                val current = GeoPoint(sample.latitude, sample.longitude)
                if (current.distanceMeters(target) <= ARRIVAL_THRESHOLD_METERS) {
                    arrivalNotified = true
                    tripArrivalNotifier.notifyApproachingStop(alightingStopName ?: "tu parada")
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        if (selectedRouteId != null) setActiveCrowdSourcingTrip(null)
    }

    fun onVehicleSelected(vehicleId: String?) {
        val state = _uiState.value
        if (state is MapUiState.Success) {
            _uiState.value = state.copy(selectedVehicleId = vehicleId)
        }
    }

    private fun fetchRealLocationOnce() {
        viewModelScope.launch {
            val real = withTimeoutOrNull(LOCATION_TIMEOUT_MS) {
                runCatching { locationSource.observeLocation().first() }.getOrNull()
            }
            realLocation.value = real?.let { GeoPoint(it.latitude, it.longitude) } ?: FALLBACK_LOCATION
        }
    }

    private fun observeSelectedRoute(routeId: RouteId) {
        viewModelScope.launch {
            combine(
                observeVehiclesOnRoute(routeId),
                followedVehicleController.followed,
                realLocation,
            ) { vehicles, followed, userLocation ->
                val followedPosition = followed?.let { f ->
                    vehicles.firstOrNull { it.vehicleId == f.vehicleId }
                }
                val previous = _uiState.value as? MapUiState.Success
                val cameraTarget = when {
                    followedPosition != null -> followedPosition.position
                    previous?.cameraTarget == null -> userLocation
                    else -> previous.cameraTarget
                }
                MapUiState.Success(
                    vehicles = vehicles,
                    selectedRouteId = routeId.value,
                    selectedVehicleId = previous?.selectedVehicleId,
                    isFollowing = followed != null,
                    cameraTarget = cameraTarget,
                )
            }.collect { newState -> _uiState.value = newState }
        }
    }

    private companion object {
        val FALLBACK_LOCATION = GeoPoint(latitude = -34.6095, longitude = -58.3924) // Congreso, CABA
    }
}
