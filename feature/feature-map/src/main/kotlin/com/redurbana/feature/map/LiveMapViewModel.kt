package com.redurbana.feature.map

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.redurbana.domain.crowdsourcing.usecase.SetActiveCrowdSourcingTripUseCase
import com.redurbana.domain.transport.FollowedVehicleController
import com.redurbana.domain.transport.model.GeoPoint
import com.redurbana.domain.transport.model.RouteId
import com.redurbana.domain.transport.model.VehiclePosition
import com.redurbana.domain.transport.usecase.ObserveVehiclesOnRouteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

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
 * El seguimiento de cámara (cameraTarget) tiene dos disparadores: seguir
 * un vehículo explícitamente (FollowedVehicleController), o —la primera
 * vez que aparece data de la línea elegida— centrar ahí una sola vez, para
 * que el mapa no se quede mostrando el punto de arranque por defecto
 * (Congreso) si la línea está en otra zona. Después de ese primer centrado
 * el usuario puede panear libremente sin que lo interrumpamos.
 */
@HiltViewModel
class LiveMapViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val observeVehiclesOnRoute: ObserveVehiclesOnRouteUseCase,
    private val followedVehicleController: FollowedVehicleController,
    private val setActiveCrowdSourcingTrip: SetActiveCrowdSourcingTripUseCase,
) : ViewModel() {

    private val selectedRouteId: String? = savedStateHandle.get<String>("routeId")

    private val _uiState = MutableStateFlow<MapUiState>(MapUiState.Loading)
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    private var hasCenteredOnRoute = false

    init {
        // Sin línea elegida (se entró directo desde el bottom nav, sin pasar
        // por el buscador de destino): NO nos suscribimos a nada. Evita
        // procesar/dibujar la flota completa sin necesidad.
        if (selectedRouteId != null) {
            observeSelectedRoute(RouteId(selectedRouteId))
            // El reporte anónimo de ubicación (crowdsourcing) queda ligado a
            // esta pantalla: arranca acá, se corta en onCleared(). Si el
            // usuario no tiene el opt-in prendido en Ajustes, esto no hace
            // nada — LocationReporter chequea eso internamente.
            setActiveCrowdSourcingTrip(RouteId(selectedRouteId))
        } else {
            _uiState.value = MapUiState.Success(vehicles = emptyList(), selectedRouteId = null)
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
            observeVehiclesOnRoute(routeId)
                .combine(followedVehicleController.followed) { vehicles, followed ->
                    val followedPosition = followed?.let { f ->
                        vehicles.firstOrNull { it.vehicleId == f.vehicleId }
                    }
                    val previous = _uiState.value as? MapUiState.Success
                    val cameraTarget = when {
                        followedPosition != null -> followedPosition.position
                        !hasCenteredOnRoute && vehicles.isNotEmpty() -> {
                            hasCenteredOnRoute = true
                            vehicles.first().position
                        }
                        else -> previous?.cameraTarget
                    }
                    MapUiState.Success(
                        vehicles = vehicles,
                        selectedRouteId = routeId.value,
                        selectedVehicleId = previous?.selectedVehicleId,
                        isFollowing = followed != null,
                        cameraTarget = cameraTarget,
                    )
                }
                .collect { newState -> _uiState.value = newState }
        }
    }
}
