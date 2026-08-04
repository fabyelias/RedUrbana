package com.redurbana.feature.stops

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.redurbana.core.location.DeviceLocationSource
import com.redurbana.domain.transport.model.GeoPoint
import com.redurbana.domain.transport.model.Stop
import com.redurbana.domain.transport.usecase.GetStopsNearbyUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

sealed interface StopsUiState {
    data object Loading : StopsUiState
    data class Success(val stops: List<Stop>) : StopsUiState
    data class Error(val message: String) : StopsUiState
}

private const val LOCATION_TIMEOUT_MS = 8_000L

@HiltViewModel
class StopsViewModel @Inject constructor(
    private val getStopsNearby: GetStopsNearbyUseCase,
    private val locationSource: DeviceLocationSource,
) : ViewModel() {

    private val _uiState = MutableStateFlow<StopsUiState>(StopsUiState.Loading)
    val uiState: StateFlow<StopsUiState> = _uiState.asStateFlow()

    /** Se usa solo si el GPS real no está disponible (permiso no concedido, sin señal, timeout). */
    private val fallbackLocation = GeoPoint(latitude = -34.6095, longitude = -58.3924) // Congreso, CABA

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = StopsUiState.Loading
            val real = withTimeoutOrNull(LOCATION_TIMEOUT_MS) {
                runCatching { locationSource.observeLocation().first() }.getOrNull()
            }
            val location = real?.let { GeoPoint(it.latitude, it.longitude) } ?: fallbackLocation
            getStopsNearby(location, radiusMeters = 500)
                .onSuccess { stops -> _uiState.value = StopsUiState.Success(stops) }
                .onFailure { _uiState.value = StopsUiState.Error("No se pudieron cargar las paradas cercanas") }
        }
    }
}
