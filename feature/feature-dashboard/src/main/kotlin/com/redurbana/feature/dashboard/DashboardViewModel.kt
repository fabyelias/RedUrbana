package com.redurbana.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.redurbana.domain.transport.GeoBounds
import com.redurbana.domain.transport.model.GeoPoint
import com.redurbana.domain.transport.model.VehiclePosition
import com.redurbana.domain.transport.usecase.GetArrivalEstimatesForStopUseCase
import com.redurbana.domain.transport.usecase.GetStopsNearbyUseCase
import com.redurbana.domain.transport.usecase.ObserveVehiclesInBoundsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Estado de UI del Dashboard, sin booleanos sueltos — un solo sealed state por pantalla. */
sealed interface DashboardUiState {
    data object Loading : DashboardUiState
    data class Success(
        val nearbyVehicles: List<VehiclePosition>,
        val reliabilityPercent: Int,
    ) : DashboardUiState
    data class Error(val message: String) : DashboardUiState
}

/**
 * El ViewModel NUNCA conoce TransportDataProvider: solo los UseCases de
 * :domain:domain-transport. Cambiar de mock a una fuente real no requiere
 * tocar esta clase.
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val observeVehiclesInBounds: ObserveVehiclesInBoundsUseCase,
    private val getStopsNearby: GetStopsNearbyUseCase,
    private val getArrivalEstimates: GetArrivalEstimatesForStopUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow<DashboardUiState>(DashboardUiState.Loading)
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    // Bounds de ejemplo alrededor del Congreso, CABA (se reemplaza por el
    // viewport real del mapa una vez integrado feature-map).
    private val defaultBounds = GeoBounds(
        northEast = GeoPoint(-34.60, -58.37),
        southWest = GeoPoint(-34.63, -58.41),
    )

    init {
        observeNearbyVehicles()
    }

    private fun observeNearbyVehicles() {
        viewModelScope.launch {
            observeVehiclesInBounds(defaultBounds).collect { vehicles ->
                _uiState.value = DashboardUiState.Success(
                    nearbyVehicles = vehicles,
                    reliabilityPercent = 92, // TODO: agregar desde reliability por línea (paso 3+)
                )
            }
        }
    }
}
