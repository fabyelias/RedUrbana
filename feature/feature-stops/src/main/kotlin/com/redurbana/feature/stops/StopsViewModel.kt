package com.redurbana.feature.stops

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.redurbana.domain.transport.model.GeoPoint
import com.redurbana.domain.transport.model.Stop
import com.redurbana.domain.transport.usecase.GetStopsNearbyUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface StopsUiState {
    data object Loading : StopsUiState
    data class Success(val stops: List<Stop>) : StopsUiState
    data class Error(val message: String) : StopsUiState
}

/**
 * TODO (roadmap): reemplazar el GeoPoint fijo por la ubicación real del
 * usuario una vez que :core:core-location tenga su wrapper de
 * FusedLocationProvider implementado. La firma del UseCase ya está lista
 * para recibirla (getStopsNearby(location, radiusMeters)) — no hace falta
 * tocar esta clase más que la fuente del GeoPoint.
 */
@HiltViewModel
class StopsViewModel @Inject constructor(
    private val getStopsNearby: GetStopsNearbyUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow<StopsUiState>(StopsUiState.Loading)
    val uiState: StateFlow<StopsUiState> = _uiState.asStateFlow()

    private val fallbackLocation = GeoPoint(latitude = -34.6095, longitude = -58.3924) // Congreso, CABA

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = StopsUiState.Loading
            getStopsNearby(fallbackLocation, radiusMeters = 500)
                .onSuccess { stops -> _uiState.value = StopsUiState.Success(stops) }
                .onFailure { _uiState.value = StopsUiState.Error("No se pudieron cargar las paradas cercanas") }
        }
    }
}
