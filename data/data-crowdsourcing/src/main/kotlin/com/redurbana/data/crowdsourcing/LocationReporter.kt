package com.redurbana.data.crowdsourcing

import com.redurbana.core.location.DeviceLocationSource
import com.redurbana.core.location.RawLocationSample
import com.redurbana.domain.crowdsourcing.LocationSample
import com.redurbana.domain.crowdsourcing.OnBusHeuristic
import com.redurbana.domain.crowdsourcing.OnBusSignal
import com.redurbana.domain.crowdsourcing.model.CrowdPing
import com.redurbana.domain.crowdsourcing.model.TripSessionId
import com.redurbana.domain.crowdsourcing.usecase.ObserveCrowdSourcingOptInUseCase
import com.redurbana.domain.crowdsourcing.usecase.ReportCrowdPingUseCase
import com.redurbana.domain.transport.model.GeoPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orquesta el reporte anónimo: solo escucha el GPS y solo manda pings
 * cuando el usuario tiene el opt-in activo Y [OnBusHeuristic] considera que
 * hay evidencia suficiente de "vas en colectivo". El resto del tiempo no
 * hace nada — ni escucha GPS siquiera, gracias a [flatMapLatest] cortando
 * el stream de ubicación apenas se apaga el opt-in.
 *
 * Se arranca desde la Application (o un Worker, para que sobreviva en
 * background) — no vive atado al ciclo de vida de ninguna pantalla,
 * a propósito: el usuario puede tener la app minimizada mientras viaja.
 */
@Singleton
class LocationReporter @Inject constructor(
    private val locationSource: DeviceLocationSource,
    private val observeOptIn: ObserveCrowdSourcingOptInUseCase,
    private val reportPing: ReportCrowdPingUseCase,
) {
    private var currentSessionId: TripSessionId? = null
    private val sampleWindow = ArrayDeque<LocationSample>()
    private val windowMaxSize = 12 // ~12 muestras a intervalo de 5s ≈ 1 minuto de historia

    suspend fun start() {
        observeOptIn()
            .flatMapLatest { optedIn ->
                if (optedIn) locationSource.observeLocation(intervalMs = 5_000)
                else flowOf<RawLocationSample>() // sin opt-in, ni se pide ubicación
            }
            .collectLatest { raw -> onNewSample(raw) }
    }

    private suspend fun onNewSample(raw: RawLocationSample) {
        val sample = LocationSample(
            position = GeoPoint(raw.latitude, raw.longitude),
            speedKmh = raw.speedMetersPerSecond * 3.6f,
            bearingDegrees = raw.bearingDegrees,
            timestamp = raw.timestamp,
        )
        sampleWindow.addLast(sample)
        while (sampleWindow.size > windowMaxSize) sampleWindow.removeFirst()

        when (val signal = OnBusHeuristic.evaluate(sampleWindow.toList())) {
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
                        candidateRouteId = null, // TODO: inferir por proximidad al trazado de una línea conocida
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
