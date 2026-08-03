package com.redurbana.data.transport.gtfsrt

import com.redurbana.domain.transport.GeoBounds
import com.redurbana.domain.transport.TransportDataProvider
import com.redurbana.domain.transport.model.ArrivalEstimate
import com.redurbana.domain.transport.model.GeoPoint
import com.redurbana.domain.transport.model.ProviderCapabilities
import com.redurbana.domain.transport.model.ReliabilityScore
import com.redurbana.domain.transport.model.RouteDetails
import com.redurbana.domain.transport.model.RouteId
import com.redurbana.domain.transport.model.RouteRecommendation
import com.redurbana.domain.transport.model.ServiceAlert
import com.redurbana.domain.transport.model.Stop
import com.redurbana.domain.transport.model.StopId
import com.redurbana.domain.transport.model.VehicleId
import com.redurbana.domain.transport.model.VehiclePosition
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementación pensada para consumir feeds GTFS Realtime estándar:
 * `VehiclePositions.pb`, `TripUpdates.pb` y `ServiceAlerts.pb` (protobuf,
 * spec de Google: https://gtfs.org/realtime/reference/).
 *
 * NO ESTÁ CONECTADA A NINGÚN ENDPOINT TODAVÍA — es la estructura que queda
 * lista para cuando exista la URL definitiva del feed (GCBA u otro operador
 * que publique GTFS-RT). Reemplaza a [com.redurbana.data.transport.mock.MockTransportProvider]
 * en [com.redurbana.data.transport.di.TransportModule] cuando esté lista.
 *
 * Pasos para completarla (ninguno de ellos toca domain ni ninguna feature):
 *  1. Agregar la dependencia `com.google.transit:gtfs-realtime-bindings` al
 *     build.gradle.kts de este módulo (parsea el protobuf oficial).
 *  2. Implementar [fetchVehiclePositionsFeed] con Retrofit/OkHttp apuntando
 *     a la URL real de `VehiclePositions.pb`, devolviendo `FeedMessage`.
 *  3. Completar los mappers de [GtfsRealtimeMappers] (FeedEntity → VehiclePosition).
 *  4. Definir la cadencia de polling (GTFS-RT no es push: hay que hacer
 *     polling periódico — 10-30s es razonable, configurable como
 *     TICK_INTERVAL_MS igual que en el mock).
 *  5. Implementar `TripUpdates.pb` para [getArrivalEstimates] (ETAs reales)
 *     y `ServiceAlerts.pb` para [getServiceAlerts].
 */
@Singleton
class GtfsRealtimeProvider @Inject constructor(
    // TODO: inyectar el cliente HTTP (Retrofit/OkHttp) apuntando a las 3 URLs del feed.
) : TransportDataProvider {

    override val providerId: String = "gtfs-realtime"

    override val capabilities = ProviderCapabilities(
        supportsRealtimePositions = true,
        supportsArrivalEstimates = true,  // vía TripUpdates.pb
        supportsServiceAlerts = true,     // vía ServiceAlerts.pb
        supportsReliabilityScore = false, // GTFS-RT no expone esto nativamente; se calcularía en la capa de repositorio
    )

    override fun observeVehiclesOnRoute(routeId: RouteId): Flow<List<VehiclePosition>> = flow {
        // TODO: pollear fetchVehiclePositionsFeed() cada TICK_INTERVAL_MS,
        // mapear con GtfsRealtimeMappers.toVehiclePositions(), filtrar por routeId, emit().
        throw NotImplementedError("GtfsRealtimeProvider: pendiente de URL de feed definitiva")
    }

    override fun observeVehiclesInBounds(bounds: GeoBounds): Flow<List<VehiclePosition>> = flow {
        // TODO: mismo polling que observeVehiclesOnRoute; el filtrado por
        // bounds puede reusar SpatialGrid (:data:data-transport/spatial),
        // exactamente igual que en MockTransportProvider.
        throw NotImplementedError("GtfsRealtimeProvider: pendiente de URL de feed definitiva")
    }

    override fun observeVehicle(routeId: RouteId, vehicleId: VehicleId): Flow<VehiclePosition?> = flow {
        throw NotImplementedError("GtfsRealtimeProvider: pendiente de URL de feed definitiva")
    }

    override suspend fun getRouteDetails(routeId: RouteId): Result<RouteDetails> =
        Result.failure(NotImplementedError("GtfsRealtimeProvider: requiere el feed estático GTFS (routes.txt/shapes.txt), no solo el realtime"))

    override suspend fun getStopsNearby(location: GeoPoint, radiusMeters: Int): Result<List<Stop>> =
        Result.failure(NotImplementedError("GtfsRealtimeProvider: requiere el feed estático GTFS (stops.txt)"))

    override suspend fun getRouteRecommendations(origin: GeoPoint, destination: GeoPoint): Result<List<RouteRecommendation>> =
        Result.failure(NotImplementedError("GtfsRealtimeProvider: requiere el feed estático GTFS (shapes.txt/stops.txt)"))

    override suspend fun getArrivalEstimates(stopId: StopId): Result<List<ArrivalEstimate>> =
        Result.failure(NotImplementedError("GtfsRealtimeProvider: pendiente de TripUpdates.pb"))

    override suspend fun getServiceAlerts(routeId: RouteId?): Result<List<ServiceAlert>> =
        Result.failure(NotImplementedError("GtfsRealtimeProvider: pendiente de ServiceAlerts.pb"))

    override fun observeVehicleReliability(routeId: RouteId): Flow<ReliabilityScore> = flow {
        // TODO: calcular en base a la comparación entre TripUpdates (horario
        // planificado) y las posiciones reales — GTFS-RT no lo da directo.
        throw NotImplementedError("GtfsRealtimeProvider: reliability se deriva, no viene del feed")
    }
}
