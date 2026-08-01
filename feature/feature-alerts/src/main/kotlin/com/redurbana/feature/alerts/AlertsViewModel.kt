package com.redurbana.feature.alerts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.redurbana.domain.transport.model.ServiceAlert
import com.redurbana.domain.transport.usecase.GetServiceAlertsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface AlertsUiState {
    data object Loading : AlertsUiState
    data class Success(val alerts: List<ServiceAlert>) : AlertsUiState
    data class Error(val message: String) : AlertsUiState
}

@HiltViewModel
class AlertsViewModel @Inject constructor(
    private val getServiceAlerts: GetServiceAlertsUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow<AlertsUiState>(AlertsUiState.Loading)
    val uiState: StateFlow<AlertsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = AlertsUiState.Loading
            getServiceAlerts()
                .onSuccess { alerts -> _uiState.value = AlertsUiState.Success(alerts) }
                .onFailure { _uiState.value = AlertsUiState.Error("No se pudieron cargar las alertas") }
        }
    }
}
