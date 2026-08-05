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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
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
        /** Ubicación real del usuario, en vivo — para dibujar el punto "estás acá" en el mapa. */
        val userLocation: GeoPoint? = null,
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
 * usuario la primera vez que se resuelve. Después de ese primer centrado
 * el usuario puede panear/hacer zoom libremente sin que lo interrumpamos —
 * pero [MapUiState.Success.userLocation] se sigue actualizando todo el
 * tiempo (no una sola vez), para poder dibujar un punto "estás acá" que te
 * sigue si te movés, aunque la cámara ya no te siga a vos.
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
    // String, no Double: Navigation Compose no tiene NavType propio para
    // Double (ver AppRoute.LiveMap) — se parsea acá del lado de lectura.
    private val alightingLat: Double? = savedStateHandle.get<String>("alightingLat")?.toDoubleOrNull()
    private val alightingLng: Double? = savedStateHandle.get<String>("alightingLng")?.toDoubleOrNull()
    private val alightingStopName: String? = savedStateHandle.get<String>("alightingStopName")
    private val alightingPoint: GeoPoint? = if (alightingLat != null && alightingLng != null) {
        GeoPoint(alightingLat, alightingLng)
    } else {
        null
    }

    private val _uiState = MutableStateFlow<MapUiState>(MapUiState.Loading)
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    /**
     * Ubicación real, en vivo — se sigue actualizando todo el tiempo que
     * esta pantalla esté abierta (no una sola vez). El fallback a Congreso
     * solo se aplica si el GPS no respondió dentro de [LOCATION_TIMEOUT_MS]
     * Y no lo pisa si el GPS real llega más tarde (watchLocation sigue
     * corrigiendo el valor en cuanto haya una posición real).
     */
    private val realLocation = MutableStateFlow<GeoPoint?>(null)

    private var arrivalNotified = false

    init {
        // Sin línea elegida (se entró directo desde el bottom nav, sin pasar
        // por el buscador de destino): NO nos suscribimos a nada. Evita
        // procesar/dibujar la flota completa sin necesidad.
        if (selectedRouteId != null) {
            observeSelectedRoute(RouteId(selectedRouteId))
            watchLocation()
            // El reporte anónimo de ubicación (crowdsourcing) queda ligado a
            // esta pantalla: arranca acá, se corta en onCleared(). Si el
            // usuario no tiene el opt-in prendido en Ajustes, esto no hace
            // nada — LocationReporter chequea eso internamente.
            setActiveCrowdSourcingTrip(RouteId(selectedRouteId))
        } else {
            _uiState.value = MapUiState.Success(vehicles = emptyList(), selectedRouteId = null)
        }
    }

    /**
     * Único observador de GPS de esta pantalla: alimenta [realLocation] (el
     * punto "estás acá" + el centrado inicial de cámara) Y, si hay parada de
     * bajada, dispara el aviso de llegada — antes eran dos colectores
     * separados que hacían básicamente lo mismo. Sin Foreground Service
     * todavía (ver TripArrivalNotifier): no sobrevive si la app pasa a
     * background por mucho tiempo o el proceso muere.
     */
    private fun watchLocation() {
        viewModelScope.launch {
            delay(LOCATION_TIMEOUT_MS)
            if (realLocation.value == null) realLocation.value = FALLBACK_LOCATION
        }
        viewModelScope.launch {
            locationSource.observeLocation().collect { sample ->
                val current = GeoPoint(sample.latitude, sample.longitude)
                realLocation.value = current
                if (alightingPoint != null && !arrivalNotified && current.distanceMeters(alightingPoint) <= ARRIVAL_THRESHOLD_METERS) {
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
                    userLocation = userLocation,
                )
            }.collect { newState -> _uiState.value = newState }
        }
    }

    private companion object {
        val FALLBACK_LOCATION = GeoPoint(latitude = -34.6095, longitude = -58.3924) // Congreso, CABA
    }
}
