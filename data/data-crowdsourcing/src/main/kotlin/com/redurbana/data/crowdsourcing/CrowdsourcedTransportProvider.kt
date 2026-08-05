package com.redurbana.data.crowdsourcing

import com.redurbana.domain.crowdsourcing.CrowdSourcingRepository
import com.redurbana.domain.transport.GeoBounds
import com.redurbana.domain.transport.TransportDataProvider
import com.redurbana.domain.transport.model.ArrivalEstimate
import com.redurbana.domain.transport.model.GeoPoint
import com.redurbana.domain.transport.model.ProviderCapabilities
import com.redurbana.domain.transport.model.ReliabilityScore
import com.redurbana.domain.transport.model.RouteDetails
import com.redurbana.domain.transport.model.RouteId
import com.redurbana.domain.transport.model.ServiceAlert
import com.redurbana.domain.transport.model.Stop
import com.redurbana.domain.transport.model.StopId
import com.redurbana.domain.transport.model.TripItinerary
import com.redurbana.domain.transport.model.VehicleId
import com.redurbana.domain.transport.model.VehiclePosition
import com.redurbana.domain.transport.model.VehicleStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Traduce [com.redurbana.domain.crowdsourcing.model.VehicleGroupEstimate]
 * (la salida del backend de crowdsourcing) al mismo "idioma común"
 * [VehiclePosition] que usa toda la app — así se puede combinar con
 * cualquier otra fuente vía [com.redurbana.data.transport.composite.CompositeTransportProvider]
 * sin que ninguna pantalla note la diferencia.
 *
 * Caso de uso previsto: `CompositeTransportProvider(listOf(gtfsProvider, crowdsourcedProvider))`
 * — GTFS-RT como fuente principal, y esto rellenando/afinando posiciones en
 * corredores donde el GPS oficial del colectivo es poco confiable o
 * inexistente. La prioridad exacta de cuál "gana" cuando ambas fuentes
 * reportan al mismo vehículo es una decisión de producto pendiente — hoy
 * `CompositeTransportProvider` usa "el primero que soporte la capability",
 * pero un merge por confianza (mayor `ReliabilityScore` gana) es más
 * apropiado acá y queda como TODO en ese archivo.
 *
 * Como con GtfsRealtimeProvider, esto no está conectado a nada real
 * todavía — `CrowdSourcingRepository.observeGroupEstimates` hoy devuelve
 * siempre lista vacía porque no hay backend (ver LocalCrowdSourcingRepository).
 */
@Singleton
class CrowdsourcedTransportProvider @Inject constructor(
    private val crowdSourcingRepository: CrowdSourcingRepository,
) : TransportDataProvider {

    override val providerId: String = "crowdsourced"

    override val capabilities = ProviderCapabilities(
        supportsRealtimePositions = true,
        supportsArrivalEstimates = false, // no calcula ETAs por sí solo, solo posiciones
        supportsServiceAlerts = false,
        supportsReliabilityScore = true, // literalmente lo que mide: cuánta gente coincide
    )

    override fun observeVehiclesOnRoute(routeId: RouteId): Flow<List<VehiclePosition>> =
        crowdSourcingRepository.observeGroupEstimates(routeId).map { estimates ->
            estimates.map { it.toVehiclePosition() }
        }

    override fun observeVehiclesInBounds(bounds: GeoBounds): Flow<List<VehiclePosition>> {
        // TODO: CrowdSourcingRepository hoy solo permite consultar por routeId.
        // Para bounds arbitrarios el backend necesitaría un endpoint propio
        // (o el cliente pide por cada línea visible y combina) — pendiente
        // de diseño una vez que el backend exista de verdad.
        throw NotImplementedError("CrowdsourcedTransportProvider: observeVehiclesInBounds pendiente de endpoint por bounds")
    }

    override fun observeVehicle(routeId: RouteId, vehicleId: VehicleId): Flow<VehiclePosition?> =
        throw NotImplementedError("CrowdsourcedTransportProvider: no tiene noción de vehicleId individual, solo grupos agregados por línea")

    override suspend fun getRouteDetails(routeId: RouteId): Result<RouteDetails> =
        Result.failure(NotImplementedError("CrowdsourcedTransportProvider: no tiene datos estáticos de línea, solo posiciones"))

    override suspend fun getStopsNearby(location: GeoPoint, radiusMeters: Int): Result<List<Stop>> =
        Result.failure(NotImplementedError("CrowdsourcedTransportProvider: no aplica"))

    override suspend fun getTripItineraries(origin: GeoPoint, destination: GeoPoint): Result<List<TripItinerary>> =
        Result.failure(NotImplementedError("CrowdsourcedTransportProvider: no aplica"))

    override suspend fun getArrivalEstimates(stopId: StopId): Result<List<ArrivalEstimate>> =
        Result.failure(NotImplementedError("CrowdsourcedTransportProvider: no aplica"))

    override suspend fun getServiceAlerts(routeId: RouteId?): Result<List<ServiceAlert>> =
        Result.failure(NotImplementedError("CrowdsourcedTransportProvider: no aplica"))

    override fun observeVehicleReliability(routeId: RouteId): Flow<ReliabilityScore> =
        crowdSourcingRepository.observeGroupEstimates(routeId).map { estimates ->
            estimates.maxByOrNull { it.confidence.percent }?.confidence ?: ReliabilityScore(0)
        }

    private fun com.redurbana.domain.crowdsourcing.model.VehicleGroupEstimate.toVehiclePosition() = VehiclePosition(
        vehicleId = VehicleId("crowd-$routeId"), // no hay id de vehículo real, es un grupo agregado
        routeId = routeId,
        position = estimatedPosition,
        bearingDegrees = estimatedBearingDegrees,
        speedKmh = estimatedSpeedKmh,
        timestamp = lastUpdated,
        status = VehicleStatus.UNKNOWN, // el crowdsourcing no infiere puntualidad, solo posición
        branchId = null,
        internalNumber = null,
        positionConfidence = confidence,
        contributingReports = contributingPingCount,
    )
}
