package com.redurbana.data.crowdsourcing

import com.redurbana.core.location.DeviceLocationSource
import com.redurbana.core.location.RawLocationSample
import com.redurbana.domain.crowdsourcing.LocationSample
import com.redurbana.domain.crowdsourcing.OnBusHeuristic
import com.redurbana.domain.crowdsourcing.OnBusSignal
import com.redurbana.domain.crowdsourcing.TripSessionController
import com.redurbana.domain.crowdsourcing.model.CrowdPing
import com.redurbana.domain.crowdsourcing.model.TripSessionId
import com.redurbana.domain.crowdsourcing.usecase.ObserveCrowdSourcingOptInUseCase
import com.redurbana.domain.crowdsourcing.usecase.ReportCrowdPingUseCase
import com.redurbana.domain.transport.model.GeoPoint
import com.redurbana.domain.transport.model.RouteId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orquesta el reporte anónimo: solo escucha el GPS y solo manda pings
 * cuando el usuario tiene el opt-in activo Y eligió un viaje/línea (no un
 * toggle global suelto — reportar sin saber a qué línea corresponde no le
 * sirve a nadie) Y [OnBusHeuristic] considera que hay evidencia suficiente
 * de "vas en colectivo". El resto del tiempo no hace nada — ni escucha GPS
 * siquiera, gracias a [flatMapLatest] cortando el stream apenas falta
 * cualquiera de esas dos condiciones.
 *
 * Vive atado a la pantalla del mapa con línea elegida (ver LiveMapViewModel),
 * no a `Application.onCreate()`: sin Foreground Service todavía (TODO ya
 * documentado en RedUrbanaApplication.kt), reportar en background con la
 * app minimizada queda fuera de alcance por ahora.
 */
@Singleton
class LocationReporter @Inject constructor(
    private val locationSource: DeviceLocationSource,
    private val observeOptIn: ObserveCrowdSourcingOptInUseCase,
    private val reportPing: ReportCrowdPingUseCase,
) : TripSessionController {
    private val reporterScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var collectingJob: Job? = null

    private val activeTrip = MutableStateFlow<RouteId?>(null)

    private var currentSessionId: TripSessionId? = null
    private val sampleWindow = ArrayDeque<LocationSample>()
    private val windowMaxSize = 12 // ~12 muestras a intervalo de 5s ≈ 1 minuto de historia

    /** Se llama al entrar/salir de la pantalla del mapa con una línea elegida. */
    override fun setActiveTrip(routeId: RouteId?) {
        activeTrip.value = routeId
        if (routeId == null) currentSessionId = null // corta la sesión al salir del viaje
        startIfNeeded()
    }

    /** Idempotente: llamarla varias veces no duplica el collector. */
    private fun startIfNeeded() {
        if (collectingJob?.isActive == true) return
        collectingJob = reporterScope.launch {
            combine(observeOptIn(), activeTrip) { optedIn, routeId -> optedIn to routeId }
                .flatMapLatest { (optedIn, routeId) ->
                    if (optedIn && routeId != null) locationSource.observeLocation(intervalMs = 5_000)
                    else flowOf<RawLocationSample>() // sin opt-in o sin viaje elegido, ni se pide ubicación
                }
                .collectLatest { raw -> onNewSample(raw, activeTrip.value) }
        }
    }

    private suspend fun onNewSample(raw: RawLocationSample, routeId: RouteId?) {
        val sample = LocationSample(
            position = GeoPoint(raw.latitude, raw.longitude),
            speedKmh = raw.speedMetersPerSecond * 3.6f,
            bearingDegrees = raw.bearingDegrees,
            timestamp = raw.timestamp,
        )
        sampleWindow.addLast(sample)
        while (sampleWindow.size > windowMaxSize) sampleWindow.removeFirst()

        when (OnBusHeuristic.evaluate(sampleWindow.toList())) {
            is OnBusSignal.LikelyOnBus -> {
                val sessionId = currentSessionId ?: TripSessionId(UUID.randomUUID().toString()).also {
                    currentSessionId = it
                }
                reportPing(
                    CrowdPing(
                        sessionId = sessionId,
                        position = sample.position,
                        speedKmh = sample.speedKmh,
                        bearingDegrees = sample.bearingDegrees,
                        timestamp = Clock.System.now(),
                        candidateRouteId = routeId,
                    ),
                )
            }
            is OnBusSignal.NotOnBus -> {
                // Viaje terminado (o nunca empezó): se descarta la sesión
                // para que el próximo viaje sea una sesión nueva, no
                // correlacionable con este.
                currentSessionId = null
            }
            is OnBusSignal.Unknown -> Unit // seguir esperando más evidencia
        }
    }
}
