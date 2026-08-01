package com.redurbana.feature.vehicledetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.redurbana.domain.transport.FollowedVehicleController
import com.redurbana.domain.transport.model.RouteDetails
import com.redurbana.domain.transport.model.RouteId
import com.redurbana.domain.transport.model.VehicleId
import com.redurbana.domain.transport.model.VehiclePosition
import com.redurbana.domain.transport.usecase.FollowVehicleUseCase
import com.redurbana.domain.transport.usecase.GetRouteDetailsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface VehicleDetailUiState {
    data object Loading : VehicleDetailUiState
    data class Success(
        val vehicle: VehiclePosition,
        val routeDetails: RouteDetails,
        val isFollowing: Boolean,
    ) : VehicleDetailUiState
    data class Error(val message: String) : VehicleDetailUiState
}

/**
 * Igual que el resto de los ViewModels de la app: depende solo de UseCases
 * de :domain:domain-transport, nunca de TransportDataProvider directamente.
 * El estado "isFollowing" ahora viene de FollowedVehicleController —el mismo
 * que lee LiveMapViewModel para animar la cámara— así que tocar "Seguir" acá
 * mueve la cámara del mapa sin que esta feature conozca a feature-map.
 */
@HiltViewModel
class VehicleDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val followVehicle: FollowVehicleUseCase,
    private val getRouteDetails: GetRouteDetailsUseCase,
    private val followedVehicleController: FollowedVehicleController,
) : ViewModel() {

    private val routeId = RouteId(checkNotNull(savedStateHandle.get<String>("routeId")))
    private val vehicleId = VehicleId(checkNotNull(savedStateHandle.get<String>("vehicleId")))

    private val _uiState = MutableStateFlow<VehicleDetailUiState>(VehicleDetailUiState.Loading)
    val uiState: StateFlow<VehicleDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            getRouteDetails(routeId)
                .onSuccess { details ->
                    followVehicle(routeId, vehicleId)
                        .combine(followedVehicleController.followed) { position, followed ->
                            position to (followed?.vehicleId == vehicleId)
                        }
                        .collect { (position, isFollowing) ->
                            _uiState.value = if (position != null) {
                                VehicleDetailUiState.Success(
                                    vehicle = position,
                                    routeDetails = details,
                                    isFollowing = isFollowing,
                                )
                            } else {
                                VehicleDetailUiState.Error("Ya no hay reportes suficientes para este colectivo")
                            }
                        }
                }
                .onFailure {
                    _uiState.value = VehicleDetailUiState.Error("No se pudo cargar la línea ${routeId.value}")
                }
        }
    }

    fun onFollowToggled() {
        followedVehicleController.toggleFollowing(routeId, vehicleId)
    }

    override fun onCleared() {
        super.onCleared()
        // Si el usuario sale del detalle sin dejar de seguir explícitamente,
        // el seguimiento se mantiene a propósito (igual que Google Maps
        // navegación: podés volver al mapa y la cámara lo sigue mostrando).
    }
}
