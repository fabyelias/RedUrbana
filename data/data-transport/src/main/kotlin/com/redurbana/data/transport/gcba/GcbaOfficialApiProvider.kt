package com.redurbana.data.transport.gcba

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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Alternativa a [com.redurbana.data.transport.gtfsrt.GtfsRealtimeProvider]
 * para el caso de que la Ciudad de Buenos Aires exponga una API propia
 * (REST/JSON) en vez de (o además de) GTFS-RT estándar — es habitual que
 * los organismos de transporte publiquen ambas cosas.
 *
 * Mismo criterio que el resto de los providers: implementación vacía hasta
 * tener la documentación/URL definitiva. Si en algún momento conviven ambas
 * fuentes (ej: posiciones de GTFS-RT + alertas oficiales de esta API), el
 * punto de combinarlas es [com.redurbana.data.transport.CompositeTransportProvider],
 * no esta clase ni GtfsRealtimeProvider directamente.
 */
@Singleton
class GcbaOfficialApiProvider @Inject constructor(
    // TODO: inyectar el servicio Retrofit apuntando a la API del GCBA (data.buenosaires.gob.ar u otra).
) : TransportDataProvider {

    override val providerId: String = "gcba-official-api"

    override val capabilities = ProviderCapabilities(
        supportsRealtimePositions = true,
        supportsArrivalEstimates = true,
        supportsServiceAlerts = true,
        supportsReliabilityScore = true, // si la API expone puntualidad histórica, se calcula acá
    )

    override fun observeVehiclesOnRoute(routeId: RouteId): Flow<List<VehiclePosition>> = flow {
        throw NotImplementedError("GcbaOfficialApiProvider: pendiente de documentación de la API oficial")
    }

    override fun observeVehiclesInBounds(bounds: GeoBounds): Flow<List<VehiclePosition>> = flow {
        throw NotImplementedError("GcbaOfficialApiProvider: pendiente de documentación de la API oficial")
    }

    override fun observeVehicle(routeId: RouteId, vehicleId: VehicleId): Flow<VehiclePosition?> = flow {
        throw NotImplementedError("GcbaOfficialApiProvider: pendiente de documentación de la API oficial")
    }

    override suspend fun getRouteDetails(routeId: RouteId): Result<RouteDetails> =
        Result.failure(NotImplementedError("GcbaOfficialApiProvider: pendiente"))

    override suspend fun getStopsNearby(location: GeoPoint, radiusMeters: Int): Result<List<Stop>> =
        Result.failure(NotImplementedError("GcbaOfficialApiProvider: pendiente"))

    override suspend fun getTripItineraries(origin: GeoPoint, destination: GeoPoint): Result<List<TripItinerary>> =
        Result.failure(NotImplementedError("GcbaOfficialApiProvider: pendiente"))

    override suspend fun getArrivalEstimates(stopId: StopId): Result<List<ArrivalEstimate>> =
        Result.failure(NotImplementedError("GcbaOfficialApiProvider: pendiente"))

    override suspend fun getServiceAlerts(routeId: RouteId?): Result<List<ServiceAlert>> =
        Result.failure(NotImplementedError("GcbaOfficialApiProvider: pendiente"))

    override fun observeVehicleReliability(routeId: RouteId): Flow<ReliabilityScore> = flow {
        throw NotImplementedError("GcbaOfficialApiProvider: pendiente")
    }
}
