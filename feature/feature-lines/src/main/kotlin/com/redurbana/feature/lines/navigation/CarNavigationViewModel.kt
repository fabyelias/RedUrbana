package com.redurbana.feature.lines.navigation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.redurbana.core.location.DeviceLocationSource
import com.redurbana.core.location.RawLocationSample
import com.redurbana.domain.crowdsourcing.model.DriverSessionId
import com.redurbana.domain.crowdsourcing.model.LiveDriverPosition
import com.redurbana.domain.crowdsourcing.usecase.PublishLiveDriverPositionUseCase
import com.redurbana.domain.crowdsourcing.usecase.StopSharingLiveDriverUseCase
import com.redurbana.domain.transport.model.DrivingRoute
import com.redurbana.domain.transport.model.GeoPoint
import com.redurbana.domain.transport.model.RouteStep
import com.redurbana.domain.transport.model.VehicleCategory
import com.redurbana.domain.transport.model.VehicleDimensions
import com.redurbana.domain.transport.model.VehicleProfile
import com.redurbana.domain.transport.model.distanceMeters
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
import kotlinx.datetime.Clock
import java.util.UUID
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlin.time.DurationUnit

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
// 30m y 3 fixes seguidos (~3s): con PRIORITY_HIGH_ACCURACY (ver más abajo,
// observeLocation(highAccuracy = true)) el error típico de un fix de GPS
// real es de unos pocos metros, no los 50m+ que daba la prioridad
// balanceada por red que usa el resto de la app — el umbral más ancho que
// había antes (50m/5) era para compensar ESA imprecisión, y de paso hacía
// que la app tardara mucho en darse cuenta de un desvío real (reporte de
// campo: "recalcula muy tarde").
private const val OFF_ROUTE_THRESHOLD_METERS = 30.0
private const val OFF_ROUTE_CONSECUTIVE_FIXES = 3
private const val VOICE_FAR_THRESHOLD_METERS = 300.0
private const val VOICE_NEAR_THRESHOLD_METERS = 50.0

// Publicar cada 3 fixes (~3s con NAV_POLL_INTERVAL_MS=1s), no en cada uno:
// mismo orden de magnitud que el sondeo del lado de quien mira (ver
// SupabaseLiveDriversRepository.POLL_INTERVAL_MS) — publicar más seguido que
// eso no lo haría verse más fluido para nadie, solo gastaría más red/Supabase.
private const val LIVE_DRIVER_PUBLISH_EVERY_N_FIXES = 3

/**
 * Sondea el GPS cada 1s (no los 5s por defecto del resto de la app — acá
 * hace falta esa frecuencia para que la cámara/velocidad/detección de
 * desvío se sientan en vivo) con `highAccuracy = true` (PRIORITY_HIGH_ACCURACY
 * real, no la prioridad balanceada por red que usa el resto de la app —
 * reporte de campo: manejando por Panamericana el punto azul aparecía en la
 * mano contraria, la velocidad no se calculaba y los avisos de voz llegaban
 * minutos tarde, todo consistente con estar usando ubicación por red en vez
 * de GPS real) y deriva de eso: en qué tramo va, cuánto falta para el
 * próximo giro, si se salió de la ruta trazada, y cuándo avisar por voz.
 * `GetDrivingRouteUseCase` es el mismo que ya usa ExploreViewModel.confirmDriving
 * — acá se reinvoca cada vez que hace falta recalcular, con el origen
 * actualizado a la posición real.
 */
@HiltViewModel
class CarNavigationViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val locationSource: DeviceLocationSource,
    private val getDrivingRoute: GetDrivingRouteUseCase,
    private val publishLiveDriverPosition: PublishLiveDriverPositionUseCase,
    private val stopSharingLiveDriver: StopSharingLiveDriverUseCase,
) : ViewModel() {

    /**
     * Nueva por cada vez que se entra a esta pantalla, se descarta al salir
     * — nunca un id de cuenta (ver DriverSessionId). Mientras dure esta
     * navegación, cualquier otro usuario con el mapa abierto ve este
     * vehículo moverse (pedido explícito: "como Waze") — nunca en modo
     * Colectivo, que ni siquiera pasa por esta pantalla.
     */
    private val driverSessionId = DriverSessionId(UUID.randomUUID().toString())
    private var fixesSinceLastPublish = 0

    private val destination = GeoPoint(
        latitude = savedStateHandle.get<String>("destinationLat")!!.toDouble(),
        longitude = savedStateHandle.get<String>("destinationLng")!!.toDouble(),
    )
    val destinationName: String = savedStateHandle.get<String>("destinationName") ?: "destino"

    /**
     * Viaja por args de navegación (ver AppRoute.CarNavigation) — String por
     * la misma limitación de siempre (Navigation Compose no serializa enums
     * ni Double directo). Alturas/ancho/peso vacíos = sin dimensiones (autos,
     * motos, camionetas, patrulleros no las necesitan).
     */
    val vehicleProfile: VehicleProfile = run {
        val category = savedStateHandle.get<String>("vehicleCategory")
            ?.let { name -> runCatching { VehicleCategory.valueOf(name) }.getOrNull() }
            ?: VehicleCategory.CAR
        val height = savedStateHandle.get<String>("vehicleHeightMeters")?.toDoubleOrNull()
        val width = savedStateHandle.get<String>("vehicleWidthMeters")?.toDoubleOrNull()
        val weight = savedStateHandle.get<String>("vehicleWeightTons")?.toDoubleOrNull()
        val dimensions = if (height != null && width != null && weight != null) {
            VehicleDimensions(heightMeters = height, widthMeters = width, weightTons = weight)
        } else {
            null
        }
        VehicleProfile(category = category, dimensions = dimensions)
    }

    /**
     * Si el conductor eligió la ruta "directa" en el sheet de ExploreMapScreen
     * (ver DrivingRouteOptions), la navegación tiene que seguir pidiendo esa
     * MISMA variante en cada recálculo por desvío — no tiene sentido
     * volver a preguntar a mitad de un viaje ya empezado. Se logra pidiendo
     * sin medidas (mismo resultado que en la vista previa: sin dimensiones,
     * Mapbox no aplica max_height/width/weight ni exclude=tunnel) — la
     * categoría real (para el ícono del puck) sigue siendo [vehicleProfile].
     */
    private val routingVehicleProfile: VehicleProfile = run {
        val useDirectRoute = savedStateHandle.get<Boolean>("useDirectRoute") ?: false
        if (useDirectRoute) vehicleProfile.copy(dimensions = null) else vehicleProfile
    }

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

    private var previousFix: RawLocationSample? = null

    /**
     * GPS real trae velocidad medida por el receptor (efecto Doppler) — no
     * hace falta calcular nada, se usa tal cual. Sin chip GPS (ubicación
     * resuelta por red/WiFi, ver FusedDeviceLocationSource), speedMetersPerSecond
     * casi siempre viene en 0 aunque el dispositivo se esté moviendo de
     * verdad: cada fix por red es una foto de posición aislada, no trae
     * velocidad medida. Reporte de campo: "el velocímetro no funciona,
     * siempre 0" — acá se estima como respaldo "distancia recorrida entre
     * el fix anterior y este, sobre el tiempo transcurrido", mejor que
     * mostrar siempre 0. Sigue siendo una estimación, no un velocímetro real
     * — el techo de precisión de la ubicación por red aplica igual acá.
     */
    private fun deriveSpeedKmh(sample: RawLocationSample): Int {
        val previous = previousFix
        previousFix = sample
        if (sample.speedMetersPerSecond > 0.5f) return (sample.speedMetersPerSecond * 3.6f).roundToInt()
        if (previous == null) return 0
        val elapsedSeconds = (sample.timestamp - previous.timestamp).toDouble(DurationUnit.SECONDS)
        if (elapsedSeconds <= 0.0) return 0
        val distanceTraveled = GeoPoint(sample.latitude, sample.longitude)
            .distanceMeters(GeoPoint(previous.latitude, previous.longitude))
        return ((distanceTraveled / elapsedSeconds) * 3.6).roundToInt()
    }

    init {
        viewModelScope.launch {
            var hasRequestedInitialRoute = false
            locationSource.observeLocation(intervalMs = NAV_POLL_INTERVAL_MS, highAccuracy = true).collect { sample ->
                val point = GeoPoint(sample.latitude, sample.longitude)
                val navPosition = NavPosition(
                    point = point,
                    speedKmh = deriveSpeedKmh(sample),
                    bearingDegrees = sample.bearingDegrees,
                )
                _position.value = navPosition

                if (!hasRequestedInitialRoute) {
                    hasRequestedInitialRoute = true
                    requestRoute(origin = point)
                } else {
                    onPositionUpdate(navPosition)
                }

                publishLiveDriverPositionThrottled(navPosition)
            }
        }
    }

    private fun publishLiveDriverPositionThrottled(navPosition: NavPosition) {
        fixesSinceLastPublish++
        if (fixesSinceLastPublish < LIVE_DRIVER_PUBLISH_EVERY_N_FIXES) return
        fixesSinceLastPublish = 0
        viewModelScope.launch {
            publishLiveDriverPosition(
                LiveDriverPosition(
                    sessionId = driverSessionId,
                    position = navPosition.point,
                    bearingDegrees = navPosition.bearingDegrees,
                    vehicleCategory = vehicleProfile.category,
                    updatedAt = Clock.System.now(),
                ),
            )
        }
    }

    /**
     * Llamado desde el botón salir de CarNavigationScreen ANTES de navegar
     * afuera (no desde onCleared(): viewModelScope ya está cancelado para
     * cuando onCleared() corre, así que un suspend lanzado ahí no llega a
     * ejecutarse — ver el comentario en CarNavigationScreen). Best-effort:
     * si falla, la fila igual expira sola por el cron de limpieza.
     */
    suspend fun stopSharing() {
        stopSharingLiveDriver(driverSessionId)
    }

    private suspend fun requestRoute(origin: GeoPoint) {
        _uiState.value = CarNavigationUiState.Loading
        getDrivingRoute(origin, destination, routingVehicleProfile)
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
