package com.redurbana.data.crowdsourcing.mock

import com.redurbana.core.location.DeviceLocationSource
import com.redurbana.core.location.RawLocationSample
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Simula que el dispositivo "va arriba de un colectivo": velocidad
 * oscilando entre 0 (semáforo) y ~30 km/h, siguiendo un trayecto simple.
 * Permite probar [com.redurbana.domain.crowdsourcing.OnBusHeuristic] y
 * [com.redurbana.data.crowdsourcing.LocationReporter] de punta a punta sin
 * GPS real ni moverse de un lugar.
 *
 * Reemplaza a FusedDeviceLocationSource (core-location) mientras esa
 * implementación real no esté lista — se cambia en el @Binds de
 * CrowdSourcingModule, mismo patrón que el resto del proyecto.
 */
@Singleton
class MockDeviceLocationSource @Inject constructor() : DeviceLocationSource {

    override fun observeLocation(intervalMs: Long): Flow<RawLocationSample> = flow {
        var lat = -34.6095
        var lng = -58.3924
        var tick = 0

        while (true) {
            // Patrón: acelera, viaja unos segundos, frena en "semáforo", repite.
            val phase = tick % 6
            val speedKmh = when (phase) {
                0, 1 -> 0f       // parado
                2, 3 -> 18f      // arrancando
                else -> 28f      // crucero
            }
            val speedMs = speedKmh / 3.6f
            lat += 0.00006 * (speedKmh / 28f)
            lng += 0.00004 * (speedKmh / 28f)

            emit(
                RawLocationSample(
                    latitude = lat,
                    longitude = lng,
                    speedMetersPerSecond = speedMs,
                    bearingDegrees = 42f,
                    timestamp = Clock.System.now(),
                    accuracyMeters = 8f,
                ),
            )
            tick++
            delay(intervalMs)
        }
    }
}
