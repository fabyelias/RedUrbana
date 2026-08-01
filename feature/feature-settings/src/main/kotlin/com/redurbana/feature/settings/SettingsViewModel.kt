package com.redurbana.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.redurbana.domain.crowdsourcing.usecase.ObserveCrowdSourcingOptInUseCase
import com.redurbana.domain.crowdsourcing.usecase.SetCrowdSourcingOptInUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    observeCrowdSourcingOptIn: ObserveCrowdSourcingOptInUseCase,
    private val setCrowdSourcingOptIn: SetCrowdSourcingOptInUseCase,
) : ViewModel() {

    val crowdSourcingEnabled: StateFlow<Boolean> = observeCrowdSourcingOptIn()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initialValue = false)

    fun onCrowdSourcingToggled(enabled: Boolean) {
        viewModelScope.launch { setCrowdSourcingOptIn(enabled) }
    }
}
