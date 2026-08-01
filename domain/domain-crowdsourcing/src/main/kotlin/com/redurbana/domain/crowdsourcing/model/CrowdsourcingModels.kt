package com.redurbana.domain.crowdsourcing.model

import com.redurbana.domain.transport.model.GeoPoint
import com.redurbana.domain.transport.model.ReliabilityScore
import com.redurbana.domain.transport.model.RouteId
import kotlinx.datetime.Instant

/**
 * Identificador de SESIÓN DE VIAJE, no de usuario. Se genera nuevo cada vez
 * que el heurístico detecta un viaje nuevo (ver [OnBusHeuristic]) y se
 * descarta al terminar el viaje. Deliberadamente NO es el id de cuenta ni
 * ningún identificador persistente — es la pieza central de que esto sea
 * anónimo: el backend puede agrupar pings de la MISMA sesión, pero no puede
 * correlacionar dos viajes distintos de la misma persona entre sí.
 */
@JvmInline
value class TripSessionId(val value: String)

/**
 * Un ping de posición reportado por el cliente cuando el heurístico
 * considera "probablemente vas en un colectivo". Es lo único que via al
 * backend — no lleva nombre, mail, ni id de cuenta.
 */
data class CrowdPing(
    val sessionId: TripSessionId,
    val position: GeoPoint,
    val speedKmh: Float,
    val bearingDegrees: Float,
    val timestamp: Instant,
    /** Si el cliente pudo inferir a qué línea corresponde por proximidad al trazado, la manda como candidata. */
    val candidateRouteId: RouteId?,
)

/**
 * Resultado YA CALCULADO por el backend: la posición agregada de un grupo
 * de pings que, por posición+velocidad+rumbo coincidentes, se interpreta
 * como "el mismo colectivo". Esto es lo que consumiría
 * CrowdsourcedTransportProvider — el cliente Android nunca hace la
 * triangulación en sí, solo la muestra.
 */
data class VehicleGroupEstimate(
    val routeId: RouteId,
    val estimatedPosition: GeoPoint,
    val estimatedBearingDegrees: Float,
    val estimatedSpeedKmh: Float,
    val contributingPingCount: Int,
    val confidence: ReliabilityScore,
    val lastUpdated: Instant,
)
