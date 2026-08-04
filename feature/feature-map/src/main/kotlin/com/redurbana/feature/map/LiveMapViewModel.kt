package com.redurbana.feature.map

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.redurbana.domain.crowdsourcing.usecase.SetActiveCrowdSourcingTripUseCase
import com.redurbana.domain.transport.FollowedVehicleController
import com.redurbana.domain.transport.GeoBounds
import com.redurbana.domain.transport.model.GeoPoint
import com.redurbana.domain.transport.model.RouteId
import com.redurbana.domain.transport.model.VehiclePosition
import com.redurbana.domain.transport.usecase.ObserveVehiclesInBoundsUseCase
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
 * Cuando el usuario mueve el mapa, se debe llamar updateVisibleBounds()
 * para que el stream de vehículos se recorte al viewport actual (evita
 * pedir/renderizar toda la ciudad si el usuario está haciendo zoom en un barrio).
 *
 * El seguimiento de cámara (cameraTarget) se deriva del mismo Flow de vehículos
 * combinado con FollowedVehicleController: no agrega ningún tick ni polling
 * adicional, solo recalcula un valor derivado cuando de cualquier forma ya
 * llegó una posición nueva del vehículo seguido.
 */
@HiltViewModel
class LiveMapViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val observeVehiclesInBounds: ObserveVehiclesInBoundsUseCase,
    private val followedVehicleController: FollowedVehicleController,
    private val setActiveCrowdSourcingTrip: SetActiveCrowdSourcingTripUseCase,
) : ViewModel() {

    private val selectedRouteId: String? = savedStateHandle.get<String>("routeId")

    private val _uiState = MutableStateFlow<MapUiState>(MapUiState.Loading)
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    private var currentBounds = GeoBounds(
        northEast = GeoPoint(-34.58, -58.36),
        southWest = GeoPoint(-34.64, -58.42),
    )

    init {
        // Sin línea elegida (se entró directo desde el bottom nav, sin pasar
        // por el buscador de destino): NO nos suscribimos a
        // observeVehiclesInBounds. Al no haber ningún collector, la
        // simulación compartida (shareIn WhileSubscribed) ni siquiera corre
        // — evita procesar/dibujar la flota completa sin necesidad.
        if (selectedRouteId != null) {
            observeVisibleVehicles()
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

    /** Se llama desde MapScreen en cada onCameraIdle del GoogleMap real. */
    fun updateVisibleBounds(bounds: GeoBounds) {
        currentBounds = bounds
        if (selectedRouteId != null) observeVisibleVehicles()
    }

    fun onVehicleSelected(vehicleId: String?) {
        val state = _uiState.value
        if (state is MapUiState.Success) {
            _uiState.value = state.copy(selectedVehicleId = vehicleId)
        }
    }

    private fun observeVisibleVehicles() {
        viewModelScope.launch {
            observeVehiclesInBounds(currentBounds)
                .combine(followedVehicleController.followed) { vehicles, followed ->
                    val vehiclesOnRoute = vehicles.filter { it.routeId == RouteId(selectedRouteId!!) }
                    val followedPosition = followed?.let { f ->
                        vehiclesOnRoute.firstOrNull { it.vehicleId == f.vehicleId }
                    }
                    val previous = _uiState.value as? MapUiState.Success
                    MapUiState.Success(
                        vehicles = vehiclesOnRoute,
                        selectedRouteId = selectedRouteId,
                        selectedVehicleId = previous?.selectedVehicleId,
                        isFollowing = followed != null,
                        cameraTarget = followedPosition?.position,
                    )
                }
                .collect { newState -> _uiState.value = newState }
        }
    }
}
