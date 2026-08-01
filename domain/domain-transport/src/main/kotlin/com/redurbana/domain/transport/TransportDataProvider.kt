package com.redurbana.domain.transport

import com.redurbana.domain.transport.model.ArrivalEstimate
import com.redurbana.domain.transport.model.GeoPoint
import com.redurbana.domain.transport.model.ProviderCapabilities
import com.redurbana.domain.transport.model.ReliabilityScore
import com.redurbana.domain.transport.model.RouteDetails
import com.redurbana.domain.transport.model.RouteId
import com.redurbana.domain.transport.model.ServiceAlert
import com.redurbana.domain.transport.model.Stop
import com.redurbana.domain.transport.model.StopId
import com.redurbana.domain.transport.model.VehiclePosition
import kotlinx.coroutines.flow.Flow

/**
 * Rectángulo geográfico visible del mapa. Vive en domain para no atar
 * el contrato a LatLngBounds de Google Maps.
 */
data class GeoBounds(
    val northEast: GeoPoint,
    val southWest: GeoPoint,
)

/**
 * CONTRATO ÚNICO de acceso a datos de transporte.
 *
 * Ninguna feature, ViewModel o UseCase debe conocer si los datos vienen de
 * GTFS Realtime, de la API oficial del GCBA, de Google Maps o de un mock.
 * Toda nueva fuente de datos se integra escribiendo una implementación de
 * esta interfaz (+ sus mappers), sin tocar una sola línea del resto de la app.
 *
 * Ver [ProviderCapabilities] para exponer de forma explícita qué soporta
 * cada implementación, y así permitir degradación elegante en la UI.
 */
interface TransportDataProvider {

    /** Identificador estable de la fuente (útil para logs, debug, métricas). */
    val providerId: String

    /** Qué funcionalidades soporta esta fuente concreta. */
    val capabilities: ProviderCapabilities

    /** Stream de posiciones en vivo para una línea específica. */
    fun observeVehiclesOnRoute(routeId: RouteId): Flow<List<VehiclePosition>>

    /** Stream de posiciones en vivo dentro del área visible del mapa. */
    fun observeVehiclesInBounds(bounds: GeoBounds): Flow<List<VehiclePosition>>

    /** Stream de posición de un único vehículo (usado por "Seguir colectivo"). */
    fun observeVehicle(routeId: RouteId, vehicleId: com.redurbana.domain.transport.model.VehicleId): Flow<VehiclePosition?>

    suspend fun getRouteDetails(routeId: RouteId): Result<RouteDetails>

    suspend fun getStopsNearby(location: GeoPoint, radiusMeters: Int): Result<List<Stop>>

    suspend fun getArrivalEstimates(stopId: StopId): Result<List<ArrivalEstimate>>

    suspend fun getServiceAlerts(routeId: RouteId? = null): Result<List<ServiceAlert>>

    fun observeVehicleReliability(routeId: RouteId): Flow<ReliabilityScore>
}
