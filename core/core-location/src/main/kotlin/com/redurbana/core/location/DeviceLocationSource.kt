package com.redurbana.core.location

import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

/** Muestra cruda de ubicación del dispositivo — sin ninguna referencia a GeoPoint de dominio a propósito. */
data class RawLocationSample(
    val latitude: Double,
    val longitude: Double,
    val speedMetersPerSecond: Float,
    val bearingDegrees: Float,
    val timestamp: Instant,
    val accuracyMeters: Float,
)

/**
 * Fuente de ubicación del dispositivo, desacoplada de qué API de Android la
 * resuelve (FusedLocationProviderClient hoy; podría ser otra cosa mañana).
 * Mismo principio que TransportDataProvider: quien consume esto (por
 * ejemplo LocationReporter en data-crowdsourcing) no sabe ni le importa.
 */
interface DeviceLocationSource {
    /**
     * Stream de ubicaciones mientras haya un colector activo. Implica que
     * el permiso de ubicación ya fue otorgado — llamarlo sin permiso debe
     * fallar el Flow, no crashear.
     */
    fun observeLocation(intervalMs: Long = 5_000): Flow<RawLocationSample>
}
