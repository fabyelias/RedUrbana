package com.redurbana.data.transport.composite

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

/**
 * Combina varios [TransportDataProvider] en uno solo, delegando cada
 * operación al primer proveedor de la lista que declare soportarla en
 * [ProviderCapabilities]. Es totalmente funcional (no es un esqueleto como
 * GtfsRealtimeProvider/GcbaOfficialApiProvider) porque no conoce ninguna
 * fuente concreta: solo orquesta lo que le pasen por constructor.
 *
 * Caso de uso típico una vez haya fuentes reales: posiciones en vivo desde
 * GTFS-RT (más frecuente) + alertas desde la API oficial del GCBA (si esa
 * es la que las publica primero), sin que el resto de la app note que son
 * dos fuentes distintas — sigue viendo un solo TransportDataProvider.
 *
 * Se activa cambiando el @Binds de TransportModule para que devuelva una
 * instancia de esto en vez de un provider único.
 *
 * TODO: la estrategia actual ("el primero que soporte la capability, en
 * orden de lista") es simple pero ingenua para el caso de
 * CrowdsourcedTransportProvider combinado con una fuente oficial — ahí lo
 * correcto sería un merge por [com.redurbana.domain.transport.model.ReliabilityScore]
 * (gana el dato más confiable, no el primero de la lista). Queda pendiente
 * el día que haya dos fuentes reales conviviendo.
 */
class CompositeTransportProvider(
    private val providers: List<TransportDataProvider>,
) : TransportDataProvider {

    init {
        require(providers.isNotEmpty()) { "CompositeTransportProvider necesita al menos un provider" }
    }

    override val providerId: String = "composite(" + providers.joinToString(",") { it.providerId } + ")"

    override val capabilities = ProviderCapabilities(
        supportsRealtimePositions = providers.any { it.capabilities.supportsRealtimePositions },
        supportsArrivalEstimates = providers.any { it.capabilities.supportsArrivalEstimates },
        supportsServiceAlerts = providers.any { it.capabilities.supportsServiceAlerts },
        supportsReliabilityScore = providers.any { it.capabilities.supportsReliabilityScore },
    )

    private fun providerFor(predicate: (ProviderCapabilities) -> Boolean): TransportDataProvider =
        providers.firstOrNull { predicate(it.capabilities) }
            ?: throw UnsupportedOperationException("Ningún provider combinado soporta esta operación")

    override fun observeVehiclesOnRoute(routeId: RouteId): Flow<List<VehiclePosition>> =
        providerFor { it.supportsRealtimePositions }.observeVehiclesOnRoute(routeId)

    override fun observeVehiclesInBounds(bounds: GeoBounds): Flow<List<VehiclePosition>> =
        providerFor { it.supportsRealtimePositions }.observeVehiclesInBounds(bounds)

    override fun observeVehicle(routeId: RouteId, vehicleId: VehicleId): Flow<VehiclePosition?> =
        providerFor { it.supportsRealtimePositions }.observeVehicle(routeId, vehicleId)

    override suspend fun getRouteDetails(routeId: RouteId): Result<RouteDetails> =
        providerFor { it.supportsRealtimePositions }.getRouteDetails(routeId)

    override suspend fun getStopsNearby(location: GeoPoint, radiusMeters: Int): Result<List<Stop>> =
        providerFor { it.supportsRealtimePositions }.getStopsNearby(location, radiusMeters)

    override suspend fun getRouteRecommendations(origin: GeoPoint, destination: GeoPoint): Result<List<RouteRecommendation>> =
        providerFor { it.supportsRealtimePositions }.getRouteRecommendations(origin, destination)

    override suspend fun getArrivalEstimates(stopId: StopId): Result<List<ArrivalEstimate>> =
        providerFor { it.supportsArrivalEstimates }.getArrivalEstimates(stopId)

    override suspend fun getServiceAlerts(routeId: RouteId?): Result<List<ServiceAlert>> =
        providerFor { it.supportsServiceAlerts }.getServiceAlerts(routeId)

    override fun observeVehicleReliability(routeId: RouteId): Flow<ReliabilityScore> =
        providerFor { it.supportsReliabilityScore }.observeVehicleReliability(routeId)
}
