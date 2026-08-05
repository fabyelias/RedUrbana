package com.redurbana.feature.lines.navigation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.redurbana.core.location.DeviceLocationSource
import com.redurbana.domain.transport.model.DrivingRoute
import com.redurbana.domain.transport.model.GeoPoint
import com.redurbana.domain.transport.model.RouteStep
import com.redurbana.domain.transport.model.projectOntoPolyline
import com.redurbana.domain.transport.usecase.GetDrivingRouteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

/** Posición en vivo durante la navegación — separado de GeoPoint a propósito: acá SÍ hace falta velocidad/rumbo, en el resto de la app no. */
data class NavPosition(
    val point: GeoPoint,
    val speedKmh: Int,
    val bearingDegrees: Float,
)

sealed interface CarNavigationUiState {
    data object Loading : CarNavigationUiState
    data class Active(
        val route: DrivingRoute,
        val currentStepIndex: Int,
        val distanceToManeuverMeters: Double,
        val distanceRemainingMeters: Double,
        val minutesRemaining: Int,
        val isRecalculating: Boolean = false,
    ) : CarNavigationUiState
    data class Error(val message: String) : CarNavigationUiState
}

private const val NAV_POLL_INTERVAL_MS = 1_000L
private const val OFF_ROUTE_THRESHOLD_METERS = 30.0
private const val OFF_ROUTE_CONSECUTIVE_FIXES = 3
private const val VOICE_FAR_THRESHOLD_METERS = 300.0
private const val VOICE_NEAR_THRESHOLD_METERS = 50.0

/**
 * Sondea el GPS cada 1s (no los 5s por defecto del resto de la app — acá
 * hace falta esa frecuencia para que la cámara/velocidad/detección de
 * desvío se sientan en vivo) y deriva de eso: en qué tramo va, cuánto falta
 * para el próximo giro, si se salió de la ruta trazada, y cuándo avisar por
 * voz. `GetDrivingRouteUseCase` es el mismo que ya usa
 * ExploreViewModel.confirmDriving — acá se reinvoca cada vez que hace falta
 * recalcular, con el origen actualizado a la posición real.
 */
@HiltViewModel
class CarNavigationViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val locationSource: DeviceLocationSource,
    private val getDrivingRoute: GetDrivingRouteUseCase,
) : ViewModel() {

    private val destination = GeoPoint(
        latitude = savedStateHandle.get<String>("destinationLat")!!.toDouble(),
        longitude = savedStateHandle.get<String>("destinationLng")!!.toDouble(),
    )
    val destinationName: String = savedStateHandle.get<String>("destinationName") ?: "destino"

    private val _uiState = MutableStateFlow<CarNavigationUiState>(CarNavigationUiState.Loading)
    val uiState: StateFlow<CarNavigationUiState> = _uiState.asStateFlow()

    private val _position = MutableStateFlow<NavPosition?>(null)
    val position: StateFlow<NavPosition?> = _position.asStateFlow()

    /** Frases para hablar — la Composable las consume con un TextToSpeech propio (ver NavTts.kt). */
    private val _speak = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val speak: SharedFlow<String> = _speak.asSharedFlow()

    private var offRouteStreak = 0

    /** (índice de tramo, umbral "far"/"near") ya avisados por voz — evita repetir el mismo aviso en cada fix de GPS. */
    private val announced = mutableSetOf<Pair<Int, String>>()

    init {
        viewModelScope.launch {
            var hasRequestedInitialRoute = false
            locationSource.observeLocation(intervalMs = NAV_POLL_INTERVAL_MS).collect { sample ->
                val point = GeoPoint(sample.latitude, sample.longitude)
                val navPosition = NavPosition(
                    point = point,
                    speedKmh = (sample.speedMetersPerSecond * 3.6f).roundToInt(),
                    bearingDegrees = sample.bearingDegrees,
                )
                _position.value = navPosition

                if (!hasRequestedInitialRoute) {
                    hasRequestedInitialRoute = true
                    requestRoute(origin = point)
                } else {
                    onPositionUpdate(navPosition)
                }
            }
        }
    }

    private suspend fun requestRoute(origin: GeoPoint) {
        _uiState.value = CarNavigationUiState.Loading
        getDrivingRoute(origin, destination)
            .onSuccess { route ->
                offRouteStreak = 0
                announced.clear()
                _uiState.value = buildActiveState(route, origin)
            }
            .onFailure {
                _uiState.value = CarNavigationUiState.Error("No pudimos calcular la ruta hasta ahí.")
            }
    }

    private fun onPositionUpdate(navPosition: NavPosition) {
        val current = _uiState.value as? CarNavigationUiState.Active ?: return
        val progress = projectOntoPolyline(navPosition.point, current.route.polyline) ?: return

        if (progress.distanceToRouteMeters > OFF_ROUTE_THRESHOLD_METERS) {
            offRouteStreak++
            if (offRouteStreak >= OFF_ROUTE_CONSECUTIVE_FIXES) {
                offRouteStreak = 0
                _uiState.value = current.copy(isRecalculating = true)
                viewModelScope.launch { requestRoute(origin = navPosition.point) }
                return
            }
        } else {
            offRouteStreak = 0
        }

        val newState = buildActiveState(current.route, navPosition.point)
        _uiState.value = newState
        maybeAnnounce(newState)
    }

    private fun buildActiveState(route: DrivingRoute, currentPoint: GeoPoint): CarNavigationUiState.Active {
        val progress = projectOntoPolyline(currentPoint, route.polyline)
        val distanceAlong = progress?.distanceAlongRouteMeters ?: 0.0
        val (stepIndex, distanceToManeuver) = currentStepInfo(route.steps, distanceAlong)
        val distanceRemaining = (route.distanceMeters - distanceAlong).coerceAtLeast(0.0)
        val minutesRemaining = if (route.distanceMeters > 0) {
            ((distanceRemaining / route.distanceMeters) * route.durationMinutes).roundToInt()
        } else {
            route.durationMinutes
        }.coerceAtLeast(0)

        return CarNavigationUiState.Active(
            route = route,
            currentStepIndex = stepIndex,
            distanceToManeuverMeters = distanceToManeuver,
            distanceRemainingMeters = distanceRemaining,
            minutesRemaining = minutesRemaining,
        )
    }

    /** Tramo en el que va el usuario + cuánto falta para SU maniobra, a partir de cuánto lleva recorrido de la ruta entera. */
    private fun currentStepInfo(steps: List<RouteStep>, distanceAlongRoute: Double): Pair<Int, Double> {
        if (steps.isEmpty()) return 0 to 0.0
        var cumulative = 0.0
        for ((index, step) in steps.withIndex()) {
            val stepEnd = cumulative + step.distanceMeters
            if (distanceAlongRoute < stepEnd || index == steps.lastIndex) {
                return index to (stepEnd - distanceAlongRoute).coerceAtLeast(0.0)
            }
            cumulative = stepEnd
        }
        return steps.lastIndex to 0.0
    }

    private fun maybeAnnounce(state: CarNavigationUiState.Active) {
        val step = state.route.steps.getOrNull(state.currentStepIndex) ?: return
        val thresholdLabel = when {
            state.distanceToManeuverMeters <= VOICE_NEAR_THRESHOLD_METERS -> "near"
            state.distanceToManeuverMeters <= VOICE_FAR_THRESHOLD_METERS -> "far"
            else -> return
        }
        if (announced.add(state.currentStepIndex to thresholdLabel)) {
            _speak.tryEmit(step.voiceAnnouncement ?: step.instruction)
        }
    }
}
